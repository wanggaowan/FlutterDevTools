package com.wanggaowan.tools.extensions.refactoring

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.ide.refactoring.DartServerRenameHandler

/**
 * 对[DartServerRenameHandler]的扩展，兼容dart文件重命名及更新引用位置内容
 *
 * @author Created by wanggaowan on 2024/3/5 15:06
 */
class ExtensionDartServerRenameHandler : DartServerRenameHandler() {

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        if (editor == null) {
            val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
            return psiFile.language == DartLanguage.INSTANCE
        }

        return super.isAvailableOnDataContext(dataContext)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, context: DataContext?) {
        super.invoke(project, elements, context)
        if (elements.isEmpty()) {
            return
        }

        val element = elements[0]
        if (element !is PsiFile) {
            return
        }

        DartRenameDialog(project, element).show()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, context: DataContext?) {
        super.invoke(project, editor, file, context)
    }
}
