// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.wanggaowan.tools.extensions

import com.intellij.CommonBundle
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.icons.AllIcons
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.IconManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.PlatformIcons
import com.intellij.util.ProcessingContext
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService.*
import com.jetbrains.lang.dart.assists.AssistUtils
import com.jetbrains.lang.dart.assists.DartSourceEditException
import com.jetbrains.lang.dart.ide.codeInsight.DartCodeInsightSettings
import com.jetbrains.lang.dart.ide.completion.DartLookupObject
import com.jetbrains.lang.dart.psi.*
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.util.DartResolveUtil
import org.apache.commons.lang3.StringUtils
import org.dartlang.analysis.server.protocol.*
import javax.swing.Icon
import kotlin.math.max

/// Dart自动提示逻辑，完全复制的Dart插件逻辑，学习如何给出自动提示
class DartServerCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            StandardPatterns.or(
                PlatformPatterns.psiElement().withLanguage(DartLanguage.INSTANCE),
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(HTMLLanguage.INSTANCE)),
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName("analysis_options.yaml")),
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName("pubspec.yaml")),
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName("fix_data.yaml"))
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

                    // If completion is requested in "Evaluate Expression" dialog or when editing a breakpoint condition, use runtime completion.
                    if (originalFile is DartExpressionCodeFragment) {
                        appendRuntimeCompletion(parameters, resultSet)
                        return
                    }

                    var file = DartResolveUtil.getRealVirtualFile(originalFile)
                    if (file is VirtualFileWindow) {
                        file = (file as VirtualFileWindow).delegate
                    }

                    if (file == null) return

                    val sdk = DartSdk.getDartSdk(project)
                    if (sdk == null || !isDartSdkVersionSufficient(sdk)) return

                    val das = getInstance(project)
                    das.updateFilesContent()

                    val startOffsetInHostFile =
                        InjectedLanguageManager.getInstance(project).injectedToHost(originalFile, parameters.offset)

                    if (das.serverVersion.isNotEmpty() && das.shouldUseCompletion2()) {
                        handleCompletion2(project, resultSet, file, startOffsetInHostFile, parameters.invocationCount)
                        return
                    }

                    val completionId = das.completion_getSuggestions(file, startOffsetInHostFile) ?: return

                    val targetFile: VirtualFile = file
                    das.addCompletions(
                        file,
                        completionId,
                        CompletionSuggestionConsumer { replacementOffset: Int, _: Int, suggestion: CompletionSuggestion ->
                            val updatedResultSet: CompletionResultSet
                            if (uriPrefix != null) {
                                updatedResultSet = resultSet
                            } else {
                                val specialPrefix = getPrefixForSpecialCases(parameters, replacementOffset)
                                updatedResultSet = if (specialPrefix != null) {
                                    resultSet.withPrefixMatcher(specialPrefix)
                                } else {
                                    resultSet
                                }
                            }
                            updatedResultSet.addElement(
                                createLookupElementAskingExtensions(
                                    project,
                                    suggestion,
                                    null,
                                    null
                                )
                            )
                        },
                        CompletionLibraryRefConsumer { includedSet: IncludedSuggestionSet, includedKinds: Set<String?>, includedRelevanceTags: Map<String, IncludedSuggestionRelevanceTag>, libraryFilePathSD: String? ->
                            if (includedKinds.isEmpty()) {
                                return@CompletionLibraryRefConsumer
                            }
                            val suggestionSet = das.getAvailableSuggestionSet(includedSet.id) ?: return@CompletionLibraryRefConsumer

                            val existingImports = das.getExistingImports(libraryFilePathSD)
                            for (suggestion in suggestionSet.items) {
                                val kind = suggestion.element.kind
                                if (!includedKinds.contains(kind)) {
                                    continue
                                }

                                val importedLibraries: MutableSet<String> = HashSet()
                                if (existingImports != null) {
                                    for ((importedLibraryUri, importedLibrary) in existingImports) {
                                        val names = importedLibrary[suggestion.declaringLibraryUri]
                                        if (names != null && names.contains(suggestion.label)) {
                                            importedLibraries.add(importedLibraryUri)
                                        }
                                    }
                                }

                                if (importedLibraries.isNotEmpty() && !importedLibraries.contains(suggestionSet.uri)) {
                                    // If some library exports this label but the current suggestion set does not, we should filter.
                                    continue
                                }

                                val completionSuggestion =
                                    createCompletionSuggestionFromAvailableSuggestion(
                                        suggestion,
                                        includedSet.relevance,
                                        includedRelevanceTags
                                    )
                                val displayUri =
                                    if (includedSet.displayUri != null) includedSet.displayUri else suggestionSet.uri
                                val insertHandler: SuggestionDetailsInsertHandlerBase =
                                    SuggestionDetailsInsertHandler(
                                        project, targetFile, completionSuggestion, startOffsetInHostFile,
                                        suggestionSet.id
                                    )

                                resultSet.addElement(
                                    createLookupElementAskingExtensions(
                                        project,
                                        completionSuggestion,
                                        displayUri,
                                        insertHandler
                                    )
                                )
                            }
                        })
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
                context.replacementOffset = parentParent.getTextRange().startOffset + uriAndRange.second.endOffset
            } else {
                // If replacement context is not set explicitly then com.intellij.codeInsight.completion.CompletionProgressIndicator#duringCompletion
                // implementation looks for the reference at caret and on Tab replaces the whole reference.
                // angular_analyzer_plugin provides angular-specific completion inside Dart string literals. Without the following hack Tab replaces
                // too much useful text. This hack is not ideal though as it may leave a piece of tail not replaced.
                // TODO: use replacementLength received from the server
                context.replacementOffset = context.replacementOffset
            }
        } else {
            var reference = context.file.findReferenceAt(context.startOffset)
            if (reference is PsiMultiReference && reference.references.isNotEmpty()) {
                reference.getRangeInElement() // to ensure that references are sorted by range
                reference =
                    reference.references[0]
            }
            if (reference is DartNewExpression ||
                reference is DartParenthesizedExpression ||
                reference is DartListLiteralExpression ||
                reference is DartSetOrMapLiteralExpression
            ) {
                // historically DartNewExpression is a reference; it can appear here only in situation like new Foo(o.<caret>);
                // without the following hack closing paren is replaced on Tab. We won't get here if at least one symbol after dot typed.
                context.replacementOffset = context.startOffset
            }
            if (reference is DartReferenceExpression) {
                val firstChild = reference.firstChild
                val lastChild = reference.lastChild
                if (firstChild !== lastChild &&
                    lastChild is PsiErrorElement && context.startOffset <= firstChild.textRange.endOffset
                ) {
                    context.replacementOffset = firstChild.textRange.endOffset
                }
            }
        }
    }

    private abstract class SuggestionDetailsInsertHandlerBase protected constructor(
        protected val myProject: Project,
        protected val myFile: VirtualFile,
        protected val myStartOffsetInHostFile: Int,
        protected val mySuggestion: CompletionSuggestion
    ) : InsertHandler<LookupElement> {
        protected abstract fun getSuggestionDetails(das: DartAnalysisServerService): Pair<String, SourceChange>?

        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val result = getSuggestionDetails(getInstance(myProject)) ?: return

            context.document.replaceString(context.startOffset, context.tailOffset, result.getFirst())

            val change = result.getSecond() ?: return

            try {
                AssistUtils.applySourceChange(myProject, change, true)
            } catch (e: DartSourceEditException) {
                CommonRefactoringUtil.showErrorHint(
                    myProject,
                    context.editor,
                    e.message!!,
                    CommonBundle.getErrorTitle(),
                    null
                )
                return
            }

            val element = mySuggestion.element
            if (element != null &&
                (ElementKind.FUNCTION == element.kind || ElementKind.CONSTRUCTOR == element.kind) && mySuggestion.parameterNames != null
            ) {
                handleFunctionInvocationInsertion(context, item, mySuggestion)
            }
        }
    }

    private class SuggestionDetailsInsertHandler(
        project: Project,
        file: VirtualFile,
        suggestion: CompletionSuggestion,
        startOffsetInHostFile: Int,
        private val mySuggestionSetId: Int
    ) : SuggestionDetailsInsertHandlerBase(project, file, startOffsetInHostFile, suggestion) {
        override fun getSuggestionDetails(das: DartAnalysisServerService): Pair<String, SourceChange>? {
            return das.completion_getSuggestionDetails(
                myFile,
                mySuggestionSetId,
                mySuggestion.completion,
                myStartOffsetInHostFile
            )
        }
    }

    private class SuggestionDetailsInsertHandler2(
        project: Project,
        file: VirtualFile,
        startOffsetInHostFile: Int,
        suggestion: CompletionSuggestion,
        private val myLibraryUriToImport: String
    ) : SuggestionDetailsInsertHandlerBase(project, file, startOffsetInHostFile, suggestion) {
        override fun getSuggestionDetails(das: DartAnalysisServerService): Pair<String, SourceChange>? {
            return das.completion_getSuggestionDetails2(
                myFile,
                myStartOffsetInHostFile,
                mySuggestion.completion,
                myLibraryUriToImport
            )
        }
    }

    companion object {
        private fun handleCompletion2(
            project: Project,
            resultSet: CompletionResultSet,
            file: VirtualFile,
            startOffsetInHostFile: Int,
            invocationCount: Int
        ) {
            // Invocation count is the number of times that the user has pressed Ctrl + Space querying for completions at this location.
            // If 0 or 1, the initial query, then get only the first 100 results, if more than 1, then return (invocationCount - 1) * 1000
            // completions.
            val maxResults = if (invocationCount <= 1) {
                100
            } else {
                (invocationCount - 1) * 1000
            }
            val das = getInstance(project)
            var completionInfo2: CompletionInfo2? =
                das.completion_getSuggestions2(
                    file,
                    startOffsetInHostFile,
                    maxResults,
                    CompletionMode.BASIC,
                    invocationCount
                )?: return

            val addedCompletions: MutableSet<CompletionSuggestion> = HashSet()
            addToCompletionList(project, resultSet, file, startOffsetInHostFile, completionInfo2!!, addedCompletions)

            var retryCount = 0
            while (completionInfo2!!.myIsIncomplete && completionInfo2.mySuggestions.size < maxResults && retryCount++ < 3) {
                completionInfo2 = das.completion_getSuggestions2(
                    file,
                    startOffsetInHostFile,
                    maxResults,
                    CompletionMode.BASIC,
                    invocationCount
                )
                if (completionInfo2 == null) return

                addToCompletionList(project, resultSet, file, startOffsetInHostFile, completionInfo2, addedCompletions)
            }
        }

        private fun addToCompletionList(
            project: Project,
            resultSet: CompletionResultSet,
            file: VirtualFile,
            startOffsetInHostFile: Int,
            completionInfo2: CompletionInfo2,
            addedCompletions: MutableSet<CompletionSuggestion>
        ) {
            val suggestions = completionInfo2.mySuggestions

            // Add all the completion results that came back from the completion_getSuggestions2 call to this result set reference.
            for (suggestion in suggestions) {
                if (!addedCompletions.add(suggestion)) continue

                val libraryUri = suggestion.libraryUri
                val libraryUriToImport = if (suggestion.isNotImported == java.lang.Boolean.TRUE) libraryUri else null
                val libraryUriToDisplay =
                    if (libraryUri != null && libraryUri.startsWith("file:")) StringUtil.substringAfterLast(
                        libraryUri,
                        "/"
                    ) else libraryUri

                var insertHandler: SuggestionDetailsInsertHandlerBase? = null
                if (libraryUriToImport != null) {
                    insertHandler = SuggestionDetailsInsertHandler2(
                        project,
                        file,
                        startOffsetInHostFile,
                        suggestion,
                        libraryUriToImport
                    )
                }

                resultSet.addElement(
                    createLookupElementAskingExtensions(
                        project,
                        suggestion,
                        libraryUriToDisplay,
                        insertHandler
                    )
                )
            }

            // As the user types additional characters, restart the completion only if we don't already have the complete set of completions.
            if (completionInfo2.myIsIncomplete) {
                resultSet.restartCompletionOnAnyPrefixChange()

                val shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CODE_COMPLETION)
                resultSet.addLookupAdvertisement(
                    DartBundle.message(
                        "press.completion.shortcut.again.for.more.results",
                        shortcut
                    )
                )
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

        private fun appendRuntimeCompletion(
            parameters: CompletionParameters,
            resultSet: CompletionResultSet
        ) {
            val originalFile = parameters.originalFile
            val project = originalFile.project
            val contextElement = originalFile.context ?: return

            val contextFile = contextElement.containingFile.virtualFile
            val contextOffset = contextElement.textOffset

            val dummyDocument = FileDocumentManager.getInstance().getDocument(originalFile.virtualFile)
                ?: return

            val code = dummyDocument.text
            val codeOffset = parameters.offset

            val das = getInstance(project)
            val completionResult =
                das.execution_getSuggestions(
                    code, codeOffset,
                    contextFile, contextOffset,
                    emptyList(), emptyList()
                )
            if (completionResult?.getFirst() != null) {
                for (suggestion in completionResult.getFirst()!!) {
                    resultSet.addElement(createLookupElementAskingExtensions(project, suggestion, null, null))
                }
            }
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

        /**
         * Handles completion provided by angular_analyzer_plugin in HTML files and inside string literals;
         * our PSI doesn't allow to calculate prefix in such cases
         */
        private fun getPrefixForSpecialCases(parameters: CompletionParameters, replacementOffset: Int): String? {
            val psiElement = parameters.originalPosition ?: return null

            val parent = psiElement.parent
            val language = psiElement.containingFile.language
            if (parent is DartStringLiteralExpression || language.isKindOf(XMLLanguage.INSTANCE)) {
                return getPrefixUsingServerData(parameters, replacementOffset)
            }

            return null
        }

        private fun getPrefixUsingServerData(parameters: CompletionParameters, replacementOffset: Int): String? {
            var element:PsiElement? = parameters.originalPosition ?: return null

            val manager = InjectedLanguageManager.getInstance(element!!.project)
            val injectedContext = parameters.originalFile

            val completionOffset = manager.injectedToHost(injectedContext, parameters.offset)
            val range = manager.injectedToHost(injectedContext, element.textRange)

            if (completionOffset < range.startOffset || completionOffset > range.endOffset) return null // shouldn't happen

            if (replacementOffset > completionOffset) return null // shouldn't happen


            while (element != null) {
                val elementStartOffset = manager.injectedToHost(injectedContext, element.textRange.startOffset)
                if (elementStartOffset <= replacementOffset) {
                    break // that's good, we can use this element to calculate prefix
                }
                element = element.parent
            }

            if (element != null) {
                val startOffset = manager.injectedToHost(injectedContext, element.textRange.startOffset)
                return element.text.substring(replacementOffset - startOffset, completionOffset - startOffset)
            }

            return null
        }

        private fun applyOverlay(base: Icon, condition: Boolean, overlay: Icon): Icon {
            if (condition) {
                return LayeredIcon(base, overlay)
            }
            return base
        }

        private fun createLookupElementAskingExtensions(
            project: Project,
            suggestion: CompletionSuggestion,
            displayUri: String?,
            insertHandler: SuggestionDetailsInsertHandlerBase?
        ): LookupElement {
            // for (DartCompletionExtension extension : DartCompletionExtension.getExtensions()) {
            //   LookupElement lookupElement = extension.createLookupElement(project, suggestion);
            //   if (lookupElement != null) {
            //     return lookupElement;
            //   }
            // }
            return createLookupElement(project, suggestion, displayUri, insertHandler)
        }

        // used by Flutter plugin
        fun createLookupElement(project: Project, suggestion: CompletionSuggestion): LookupElementBuilder {
            return createLookupElement(project, suggestion, null, null)
        }

        private fun createLookupElement(
            project: Project,
            suggestion: CompletionSuggestion,
            displayUri: String?,
            insertHandler: SuggestionDetailsInsertHandlerBase?
        ): LookupElementBuilder {
            val element = suggestion.element
            val location = element?.location
            val lookupObject = DartLookupObject(project, location, suggestion.relevance)

            val lookupString = suggestion.completion
            var lookup = LookupElementBuilder.create(lookupObject, lookupString)

            if (suggestion.displayText != null) {
                lookup = lookup.withPresentableText(suggestion.displayText)
            }

            // keywords are bold
            if (suggestion.kind == CompletionSuggestionKind.KEYWORD) {
                lookup = lookup.bold()
            }

            val dotIndex = lookupString.indexOf('.')
            if (dotIndex > 0 && dotIndex < lookupString.length - 1 &&
                StringUtil.isJavaIdentifier(lookupString.substring(0, dotIndex)) &&
                StringUtil.isJavaIdentifier(lookupString.substring(dotIndex + 1))
            ) {
                // 'path.Context' should match 'Conte' prefix
                lookup = lookup.withLookupString(lookupString.substring(dotIndex + 1))
            }

            var shouldSetSelection = true
            if (element != null) {
                // @deprecated
                if (element.isDeprecated) {
                    lookup = lookup.strikeout()
                }

                if (StringUtil.isEmpty(suggestion.displayText)) {
                    // append type parameters
                    val typeParameters = element.typeParameters
                    if (typeParameters != null) {
                        lookup = lookup.appendTailText(typeParameters, false)
                    }
                    // append parameters
                    val parameters = element.parameters
                    if (parameters != null) {
                        lookup = lookup.appendTailText(parameters, false)
                    }
                }

                // append return type
                val returnType = element.returnType
                if (!StringUtils.isEmpty(returnType)) {
                    lookup = lookup.withTypeText(returnType, true)
                }

                // If this is a class or similar global symbol, try to show which package it's coming from.
                if (!StringUtils.isEmpty(displayUri)) {
                    val packageInfo = "($displayUri)"
                    lookup = lookup.withTypeText(
                        if (StringUtils.isEmpty(returnType)) packageInfo else "$returnType $packageInfo",
                        true
                    )
                }

                // icon
                var icon = getBaseImage(element)
                if (icon != null) {
                    val iconManager = IconManager.getInstance()
                    if (suggestion.kind == CompletionSuggestionKind.OVERRIDE) {
                        icon = iconManager.createRowIcon(icon, AllIcons.Gutter.OverridingMethod)
                    } else {
                        icon = iconManager.createRowIcon(
                            icon, if (element.isPrivate) iconManager.getPlatformIcon(
                                PlatformIcons.Private
                            )
                            else com.intellij.util.PlatformIcons.PUBLIC_ICON
                        )
                        icon = applyOverlay(icon, element.isFinal, iconManager.getPlatformIcon(PlatformIcons.FinalMark))
                        icon = applyOverlay(icon, element.isConst, iconManager.getPlatformIcon(PlatformIcons.FinalMark))
                    }

                    lookup = lookup.withIcon(icon)
                }

                // Prepare for typing arguments, if any.
                if (insertHandler == null && CompletionSuggestionKind.INVOCATION == suggestion.kind && suggestion.parameterNames != null) {
                    shouldSetSelection = false
                    lookup = lookup.withInsertHandler { context: InsertionContext, item: LookupElement ->
                        handleFunctionInvocationInsertion(
                            context,
                            item,
                            suggestion
                        )
                    }
                }
            }

            if (insertHandler != null) {
                lookup = lookup.withInsertHandler(insertHandler)
            } else if (shouldSetSelection) {
                // Use selection offset / length.
                lookup = lookup.withInsertHandler { context: InsertionContext, _ ->
                    val editor = context.editor
                    val startOffset = context.startOffset + suggestion.selectionOffset
                    val endOffset = startOffset + suggestion.selectionLength
                    editor.caretModel.moveToOffset(startOffset)
                    if (endOffset > startOffset) {
                        editor.selectionModel.setSelection(startOffset, endOffset)
                    }
                }
            }

            return lookup
        }

        private fun handleFunctionInvocationInsertion(
            context: InsertionContext,
            item: LookupElement,
            suggestion: CompletionSuggestion
        ) {
            val parameterNames = suggestion.parameterNames ?: return

            // like in JavaCompletionUtil.insertParentheses()
            val needRightParenth = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET ||
                parameterNames.isEmpty() && context.completionChar != '('

            val hasParameters = parameterNames.isNotEmpty()
            val handler =
                ParenthesesInsertHandler.getInstance(hasParameters, false, false, needRightParenth, false)
            handler.handleInsert(context, item)

            val editor = context.editor
            if (hasParameters && DartCodeInsightSettings.getInstance().INSERT_DEFAULT_ARG_VALUES) {
                val argumentListString = suggestion.defaultArgumentListString
                if (argumentListString != null) {
                    val document = editor.document
                    val offset = editor.caretModel.offset

                    // At this point caret is expected to be right after the opening paren.
                    // But if user was completing using Tab over the existing method call with arguments then old arguments are still there,
                    // if so, skip inserting argumentListString
                    val text = document.charsSequence
                    if (text[offset - 1] == '(' && text[offset] == ')') {
                        document.insertString(offset, argumentListString)

                        PsiDocumentManager.getInstance(context.project).commitDocument(document)

                        val builder =
                            TemplateBuilderFactory.getInstance()
                                .createTemplateBuilder(context.file) as TemplateBuilderImpl

                        val ranges = suggestion.defaultArgumentListTextRanges
                        // Only proceed if ranges are provided and well-formed.
                        if (ranges != null && (ranges.size and 1) == 0) {
                            var index = 0
                            while (index < ranges.size) {
                                val start = ranges[index]
                                val length = ranges[index + 1]
                                val arg = argumentListString.substring(start, start + length)
                                val expression = TextExpression(arg)
                                val range = TextRange(offset + start, offset + start + length)

                                index += 2
                                builder.replaceRange(range, "group_" + (index - 1), expression, true)
                            }

                            builder.run(editor, true)
                        }
                    }
                }
            }

            val itemObj = item.getObject()
            if (itemObj is DartLookupObject) {
                AutoPopupController.getInstance(context.project)
                    .autoPopupParameterInfo(editor, itemObj.findPsiElement())
            }
        }


        private fun createCompletionSuggestionFromAvailableSuggestion(
            suggestion: AvailableSuggestion,
            suggestionSetRelevance: Int,
            includedSuggestionRelevanceTags: Map<String, IncludedSuggestionRelevanceTag>
        ): CompletionSuggestion {
            var relevanceBoost = 0
            val relevanceTags = suggestion.relevanceTags
            if (relevanceTags != null) {
                for (tag in relevanceTags) {
                    val relevanceTag = includedSuggestionRelevanceTags[tag]
                    if (relevanceTag != null) {
                        relevanceBoost = max(relevanceBoost.toDouble(), relevanceTag.relevanceBoost.toDouble())
                            .toInt()
                    }
                }
            }

            val element = suggestion.element
            return CompletionSuggestion(
                "UNKNOWN",  // we don't have info about CompletionSuggestionKind
                suggestionSetRelevance + relevanceBoost,
                suggestion.label,
                null,
                0,
                0,
                0,
                0,
                element.isDeprecated,
                false,
                null,
                null,
                null,
                suggestion.defaultArgumentListString,
                suggestion.defaultArgumentListTextRanges,
                element,
                element.returnType,
                suggestion.parameterNames,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        }

        private fun getBaseImage(element: Element): Icon? {
            val elementKind = element.kind
            return when (elementKind) {
                ElementKind.CLASS, ElementKind.CLASS_TYPE_ALIAS -> if (element.isAbstract) AllIcons.Nodes.AbstractClass else AllIcons.Nodes.Class
                ElementKind.ENUM -> AllIcons.Nodes.Enum
                ElementKind.MIXIN -> AllIcons.Nodes.AbstractClass
                ElementKind.ENUM_CONSTANT, ElementKind.FIELD -> AllIcons.Nodes.Field
                ElementKind.COMPILATION_UNIT -> com.intellij.util.PlatformIcons.FILE_ICON
                ElementKind.CONSTRUCTOR -> AllIcons.Nodes.ClassInitializer
                ElementKind.GETTER -> if (element.isTopLevelOrStatic) AllIcons.Nodes.PropertyReadStatic else AllIcons.Nodes.PropertyRead
                ElementKind.SETTER -> if (element.isTopLevelOrStatic) AllIcons.Nodes.PropertyWriteStatic else AllIcons.Nodes.PropertyWrite
                ElementKind.METHOD -> if (element.isAbstract) AllIcons.Nodes.AbstractMethod else AllIcons.Nodes.Method
                ElementKind.FUNCTION -> AllIcons.Nodes.Lambda
                ElementKind.FUNCTION_TYPE_ALIAS -> AllIcons.Nodes.Annotationtype
                ElementKind.TOP_LEVEL_VARIABLE -> AllIcons.Nodes.Variable
                ElementKind.EXTENSION -> AllIcons.Nodes.Include
                else -> null
            }
        }
    }
}
