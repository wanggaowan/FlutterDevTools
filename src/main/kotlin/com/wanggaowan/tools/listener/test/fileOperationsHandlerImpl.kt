package com.wanggaowan.tools.listener.test

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.usageView.UsageInfo

/**
 *
 *
 * @author Created by wanggaowan on 2023/2/26 14:49
 */
class LocalFileOperationsHandler: MoveHandlerDelegate() {
    override fun canMove(dataContext: DataContext?): Boolean {
        return false
    }

    override fun canMove(
        elements: Array<out PsiElement>?,
        targetContainer: PsiElement?,
        reference: PsiReference?
    ): Boolean {
        return false
    }

    override fun canMove(elements: Array<out PsiElement>?, targetContainer: PsiElement?): Boolean {
        return false
    }
}
