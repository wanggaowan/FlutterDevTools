package com.wanggaowan.tools.extensions.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Consumer
import com.intellij.util.ObjectUtils
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.analyzer.DartServerData.DartError
import com.jetbrains.lang.dart.analyzer.DartServerData.DartRegion
import com.jetbrains.lang.dart.highlight.DartSyntaxHighlighterColors
import com.jetbrains.lang.dart.psi.DartImportStatement
import com.jetbrains.lang.dart.sdk.DartSdk
import com.wanggaowan.tools.extensions.complete.CodeAnalysisService
import com.wanggaowan.tools.extensions.fixes.DartImportQuickFix
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.dartlang.analysis.server.protocol.AnalysisErrorSeverity
import org.jetbrains.kotlin.idea.base.util.module
import kotlin.math.max


// 修复import导入注解
class FixImportAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (holder.isBatchMode) {
            return
        }

        val service = CodeAnalysisService.getInstance(element.project)
        if (!service.isAvailable) {
            return
        }

        var text = element.text
        if (text.isEmpty()) {
            return
        }

        val file = element.containingFile ?: return

        val module = element.module ?: return
        if (!module.isFlutterProject) {
            return
        }

        val session = holder.currentAnnotationSession
        var notYetAppliedErrors: MutableList<DartError>? = session.getUserData(DART_ERRORS)?.toMutableList()
        if (notYetAppliedErrors == null) {
            notYetAppliedErrors = mutableListOf()
            val vFile = file.virtualFile
            if (canBeAnalyzedByServer(element.project, vFile)) {
                val dartService = DartAnalysisServerService.getInstance(element.project)
                if (dartService.serverReadyForRequest()) {
                    dartService.updateFilesContent()
                    if (ApplicationManager.getApplication().isUnitTestMode) {
                        dartService.waitForAnalysisToComplete_TESTS_ONLY(vFile)
                    }

                    notYetAppliedErrors.addAll(dartService.getErrors(vFile))
                    notYetAppliedErrors.sortWith(
                        Comparator.comparingInt<DartRegion> { it.offset }
                    )
                    ensureNoErrorsAfterEOF(
                        notYetAppliedErrors,
                        element.containingFile.textLength
                    )
                }
            }
        }

        processDartRegionsInRange(notYetAppliedErrors, element.textRange,
            Consumer { err: DartError ->
                if (AnalysisErrorSeverity.ERROR != err.severity) {
                    return@Consumer
                }

                val index = text.lastIndexOf("(")
                if (index != -1) {
                    text = text.substring(0, index).trim()
                }

                val suggestion = service.getSuggestions(module)?.find { it.name == text }
                if (suggestion == null || suggestion.libraryUriToImport.isNullOrEmpty()) {
                    return@Consumer
                }

                val imports = PsiTreeUtil.getChildrenOfType(file, DartImportStatement::class.java)
                val libraryUri = suggestion.libraryUriToImport
                if (imports?.find {
                        val text2 = it.text
                        text2 == "import '${libraryUri}';" || text2 == "import \"${libraryUri}\";"
                    } != null) {
                    /// 如果import已导入，则跳过，此时官方Dart插件自动补全会出现提示
                    return@Consumer
                }

                holder.newAnnotation(HighlightSeverity.ERROR, err.message)
                    .range(element.textRange)
                    .tooltip("")
                    .textAttributes(DartSyntaxHighlighterColors.ERROR)
                    .withFix(DartImportQuickFix(suggestion, err))
                    .create()
            })
    }

    private fun canBeAnalyzedByServer(project: Project, file: VirtualFile?): Boolean {
        if (!DartAnalysisServerService.isLocalAnalyzableFile(file)) {
            return false
        } else {
            val sdk = DartSdk.getDartSdk(project)
            return if (sdk != null && DartAnalysisServerService.isDartSdkVersionSufficient(sdk))
                DartAnalysisServerService.getInstance(project).isInIncludedRoots(file)
            else false
        }
    }

    private fun ensureNoErrorsAfterEOF(errors: MutableList<DartError>, fileLength: Int) {
        for (i in errors.indices.reversed()) {
            val error = errors[i]
            if (error.offset < fileLength) {
                return
            }

            errors[i] = error.asEofError(fileLength)
        }
    }

    private fun processDartRegionsInRange(
        regions: MutableList<DartError>,
        psiElementRange: TextRange,
        processor: Consumer<DartError>
    ) {
        if (regions.isEmpty()) return

        var i: Int = ObjectUtils.binarySearch(
            0, regions.size
        ) { mid -> if (regions[mid].offset < psiElementRange.startOffset) -1 else 1 }
        i = max(0.0, (-i - 1).toDouble()).toInt()

        var region = if (i < regions.size) regions[i] else null

        while (region != null && region.offset < psiElementRange.endOffset) {
            if (psiElementRange.containsRange(region.offset, region.offset + region.length)) {
                regions.removeAt(i)
                processor.consume(region)
            } else {
                i++ // regions.remove(i) not called => need to increment i
            }
            region = if (i < regions.size) regions[i] else null
        }
    }

    companion object {
        private val DART_ERRORS: Key<List<DartError>> = Key.create("DART_ERRORS")
    }
}
