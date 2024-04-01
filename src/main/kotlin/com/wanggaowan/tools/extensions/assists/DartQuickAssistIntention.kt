package com.wanggaowan.tools.extensions.assists

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.Html
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.jetbrains.lang.dart.psi.DartImportStatement
import com.wanggaowan.tools.extensions.complete.CodeAnalysisService
import com.wanggaowan.tools.extensions.complete.Suggestion
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.idea.base.util.module

// 智能提示，比如可以快速补全代码等
open class DartQuickAssistIntention : IntentionAction {
    private var suggestion: Suggestion? = null

    override fun getFamilyName(): String {
        return ""
    }

    override fun getText(): String {
        return suggestion?.libraryUriToImport ?: ""
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) {
            return
        }

        val suggestion = this.suggestion ?: return
        insertImport(project, file, editor, suggestion)
    }

    private fun insertImport(project: Project, file: PsiFile, editor: Editor, suggestion: Suggestion) {
        if (suggestion.libraryUriToImport.isNullOrEmpty()) {
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val importStr = "import '${suggestion.libraryUriToImport}';"
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
            val exist = PsiTreeUtil.getChildrenOfType(file, DartImportStatement::class.java)
                ?.find { it.text.trim() == importStr } != null
            if (exist) {
                return@runWriteCommandAction
            }
            DartPsiUtils.addImport(project, file, importStr)
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        suggestion = null
        if (editor == null || file == null) {
            return false
        }

        val service = CodeAnalysisService.getInstance(project)
        if (!service.isAvailable) {
            return false
        }

        val module = file.module ?: return false
        if (!module.isFlutterProject) {
            return false
        }

        val currentCaret = editor.caretModel.primaryCaret
        var text = editor.document.getText(TextRange(currentCaret.selectionStart, currentCaret.selectionEnd)).trim()
        if (text.isEmpty()) {
            return false
        }

        val index = text.lastIndexOf("(")
        if (index != -1) {
            text = text.substring(0, index).trim()
        }

        val suggestion = service.getSuggestions(module)?.find { it.name == text }
        if (suggestion == null || suggestion.libraryUriToImport.isNullOrEmpty()) {
            return false
        }

        val imports = PsiTreeUtil.getChildrenOfType(file, DartImportStatement::class.java)
        val libraryUri = suggestion.libraryUriToImport
        if (imports?.find {
                val text2 = it.text
                text2 == "import '${libraryUri}';" || text2 == "import \"${libraryUri}\";"
            } != null) {
            /// 如果import已导入，则跳过，此时官方Dart插件自动补全会出现提示
            return false
        }

        this.suggestion = suggestion
        return true
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        if (suggestion == null) {
            return IntentionPreviewInfo.EMPTY
        }

        return Html(suggestion!!.libraryUriToImport!!)
    }

    override fun startInWriteAction(): Boolean {
        return true
    }

    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
        return null
    }
}
