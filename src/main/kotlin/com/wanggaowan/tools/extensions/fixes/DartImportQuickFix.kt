package com.wanggaowan.tools.extensions.fixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.jetbrains.lang.dart.analyzer.DartServerData.DartError
import com.jetbrains.lang.dart.psi.DartImportStatement
import com.wanggaowan.tools.extensions.complete.Suggestion
import com.wanggaowan.tools.utils.dart.DartPsiUtils

/// import快速修复
class DartImportQuickFix(private val suggestion: Suggestion, private val dartError: DartError) :
    IntentionAction {
    override fun getFamilyName(): String {
        return ""
    }

    override fun getText(): String {
        val importStr = suggestion.libraryUriToImport ?: ""
        return "import library '$importStr'"
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) {
            return
        }

        if (!file.isPhysical && !ApplicationManager.getApplication().isWriteAccessAllowed) {
            doInvokeForPreview(file)
            return
        }

        insertImport(project, file, editor)
    }

    private fun doInvokeForPreview(psiFile: PsiFile) {
        if (suggestion.libraryUriToImport.isNullOrEmpty()) {
            return
        }

        // 通过在虚拟文件的插入快速修复的代码，就能实现预览，预览内容从插入位置到下一个空白符位置截止
        // 不是很明白这种方式的预览机制，也可以通过复写generatePreview实现预览，但是样式实现较复制，得写很多html内容
        val document = psiFile.viewProvider.document
        val importStr = "import '${suggestion.libraryUriToImport}';\n\n"
        document.replaceString(0, 0, importStr)
    }

    private fun insertImport(project: Project, file: PsiFile, editor: Editor) {
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
        return suggestion.libraryUriToImport != null
    }

    override fun startInWriteAction(): Boolean {
        return true
    }

    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
        return if (suggestion.libraryUriToImport.isNullOrEmpty()) null else this
    }
}
