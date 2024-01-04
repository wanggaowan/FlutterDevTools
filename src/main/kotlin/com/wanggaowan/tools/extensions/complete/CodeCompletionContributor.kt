package com.wanggaowan.tools.extensions.complete

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.icons.AllIcons
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.ide.completion.DartLookupObject
import com.jetbrains.lang.dart.psi.*
import com.jetbrains.lang.dart.util.DartResolveUtil
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import org.dartlang.analysis.server.protocol.ElementKind
import org.jetbrains.kotlin.idea.base.util.module
import javax.swing.Icon

/// Dart自动提示逻辑，完全复制的Dart插件逻辑，学习如何给出自动提示
class CodeCompletionContributor : CompletionContributor() {
    private val myInsertHandler = MyInsertHandler()

    init {
        extend(
            CompletionType.BASIC,
            StandardPatterns.or(
                PlatformPatterns.psiElement().withLanguage(DartLanguage.INSTANCE),
            ),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    originalResultSet: CompletionResultSet
                ) {
                    val originalFile = parameters.originalFile
                    val project = originalFile.project
                    if (originalResultSet.prefixMatcher.prefix.isEmpty() &&
                        isRightAfterBadIdentifier(parameters.editor.document.immutableCharSequence, parameters.offset)
                    ) {
                        return
                    }

                    val sorter = createSorter(parameters, originalResultSet.prefixMatcher)
                    val uriPrefix = getPrefixIfCompletingUri(parameters)
                    val resultSet = if (uriPrefix != null
                    ) originalResultSet.withRelevanceSorter(sorter).withPrefixMatcher(uriPrefix)
                    else originalResultSet.withRelevanceSorter(sorter)

                    var file = DartResolveUtil.getRealVirtualFile(originalFile)
                    if (file is VirtualFileWindow) {
                        file = (file as VirtualFileWindow).delegate
                    }

                    if (file == null) return

                    val module = originalFile.module ?: return
                    val elements = CodeAnalysisService.getInstance(project).getSuggestions(module)
                    val imports = PsiTreeUtil.getChildrenOfType(originalFile, DartImportStatement::class.java)
                    elements?.forEach { element ->
                        val libraryUri = element.libraryUriToImport
                        if (libraryUri != null && imports?.find {
                                val text = it.text
                                text == "import '${libraryUri}';" || text == "import \"${libraryUri}\";"
                            } != null) {
                            /// 如果import已导入，则跳过，此时官方Dart插件自动补全会出现提示
                            return@forEach
                        }

                        var libraryUriToDisplay = libraryUri
                        if (!libraryUriToDisplay.isNullOrEmpty()) {
                            libraryUriToDisplay = "($libraryUriToDisplay)"
                        }

                        val lookup = LookupElementBuilder.create(element, element.name)
                            .withTypeText(libraryUriToDisplay)
                            .withIcon(getBaseImage(element))
                            .withInsertHandler(myInsertHandler)
                        resultSet.addElement(lookup)
                    }
                }
            })
    }

    override fun beforeCompletion(context: CompletionInitializationContext) {
        val psiElement = context.file.findElementAt(context.startOffset)
        val parent = psiElement?.parent
        if (parent is DartStringLiteralExpression) {
            val parentParent = parent.getParent()
            if (parentParent is DartUriElement) {
                val uriAndRange = parentParent.uriStringAndItsRange
                context.replacementOffset =
                    parentParent.getTextRange().startOffset + (uriAndRange.second as TextRange).endOffset
            } else {
                context.replacementOffset = context.replacementOffset
            }
        } else {
            var reference = context.file.findReferenceAt(context.startOffset)
            if (reference is PsiMultiReference && reference.references.isNotEmpty()) {
                reference.getRangeInElement()
                reference = reference.references[0]
            }

            if (reference is DartNewExpression
                || reference is DartParenthesizedExpression
                || reference is DartListLiteralExpression
                || reference is DartSetOrMapLiteralExpression
            ) {
                context.replacementOffset = context.startOffset
            }

            if (reference is DartReferenceExpression) {
                val firstChild = reference.firstChild
                val lastChild = reference.lastChild
                if (firstChild !== lastChild && lastChild is PsiErrorElement && context.startOffset <= firstChild.textRange.endOffset) {
                    context.replacementOffset = firstChild.textRange.endOffset
                }
            }
        }
    }

    private fun isRightAfterBadIdentifier(text: CharSequence, offset: Int): Boolean {
        if (offset == 0 || offset >= text.length) return false

        var currentOffset = offset - 1
        if (!Character.isJavaIdentifierPart(text[currentOffset])) return false

        while (currentOffset > 0 && Character.isJavaIdentifierPart(text[currentOffset - 1])) {
            currentOffset--
        }

        return !Character.isJavaIdentifierStart(text[currentOffset])
    }

    private fun createSorter(parameters: CompletionParameters, prefixMatcher: PrefixMatcher): CompletionSorter {
        val dartWeigher: LookupElementWeigher = object : LookupElementWeigher("dartRelevance", true, false) {
            override fun weigh(element: LookupElement): Int {
                val lookupObject = element.getObject()
                return if (lookupObject is DartLookupObject) lookupObject.relevance else 0
            }
        }

        val defaultSorter = CompletionSorter.defaultSorter(parameters, prefixMatcher)
        return defaultSorter.weighBefore("liftShorter", dartWeigher)
    }

    private fun getPrefixIfCompletingUri(parameters: CompletionParameters): String? {
        val psiElement = parameters.originalPosition
        val parent = psiElement?.parent
        val parentParent = if (parent is DartStringLiteralExpression) parent.getParent() else null
        if (parentParent is DartUriElement) {
            val uriStringOffset = parentParent.uriStringAndItsRange.second.startOffset
            if (parameters.offset >= parentParent.getTextRange().startOffset + uriStringOffset) {
                return parentParent.getText()
                    .substring(uriStringOffset, parameters.offset - parentParent.getTextRange().startOffset)
            }
        }
        return null
    }

    companion object {
        private fun getBaseImage(suggestion: Suggestion): Icon? {
            return when (suggestion.kind) {
                ElementKind.CLASS, ElementKind.CLASS_TYPE_ALIAS -> if (suggestion.isAbstract) AllIcons.Nodes.AbstractClass else AllIcons.Nodes.Class
                ElementKind.ENUM -> AllIcons.Nodes.Enum
                ElementKind.MIXIN -> AllIcons.Nodes.AbstractClass
                ElementKind.ENUM_CONSTANT, ElementKind.FIELD -> AllIcons.Nodes.Field
                ElementKind.COMPILATION_UNIT -> com.intellij.util.PlatformIcons.FILE_ICON
                ElementKind.CONSTRUCTOR -> AllIcons.Nodes.ClassInitializer
                ElementKind.GETTER -> AllIcons.Nodes.PropertyReadStatic
                ElementKind.SETTER -> AllIcons.Nodes.PropertyWriteStatic
                ElementKind.FUNCTION -> AllIcons.Nodes.Lambda
                ElementKind.FUNCTION_TYPE_ALIAS -> AllIcons.Nodes.Annotationtype
                ElementKind.TOP_LEVEL_VARIABLE -> AllIcons.Nodes.Variable
                ElementKind.EXTENSION -> AllIcons.Nodes.Include
                else -> null
            }
        }
    }
}

class MyInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, lookup: LookupElement) {
        val obj = lookup.`object`
        if (obj !is Suggestion) {
            return
        }

        context.document.replaceString(context.startOffset, context.tailOffset, obj.name)
        insertImport(context, obj)
    }

    private fun insertImport(context: InsertionContext, suggestion: Suggestion) {
        if (suggestion.libraryUriToImport.isNullOrEmpty()) {
            return
        }

        WriteCommandAction.runWriteCommandAction(context.project) {
            val importStr = "import '${suggestion.libraryUriToImport}';"
            val file = context.file
            PsiDocumentManager.getInstance(context.project).commitDocument(context.editor.document)
            val exist = PsiTreeUtil.getChildrenOfType(file, DartImportStatement::class.java)
                ?.find { it.text.trim() == importStr } != null
            if (exist) {
                return@runWriteCommandAction
            }
            DartPsiUtils.addImport(context.project, context.file, importStr)
        }
    }
}
