package com.wanggaowan.tools.extensions.refactoring

import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringSettings
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import com.intellij.usageView.UsageInfo
import com.wanggaowan.tools.actions.image.PsiBinaryFileDelegate
import com.wanggaowan.tools.extensions.findusage.ImageUsagesHandler
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.XUtils

/**
 * 安全删除图片文件处理器
 *
 * @author Created by wanggaowan on 2024/3/1 11:32
 */
class SafeDeleteImageFileProcessorDelegate : SafeDeleteProcessorDelegateBase() {
    override fun handlesElement(element: PsiElement?): Boolean {
        if (element !is PsiFile) {
            return false
        }

        val file = element.virtualFile ?: return false
        if (!XUtils.isImage(element.name)) {
            return false
        }

        val imagesDir = PluginSettings.getImagesFileDir(element.project)
        val exampleImagesDir = PluginSettings.getExampleImagesFileDir(element.project)
        return !(!file.path.contains(imagesDir) && !file.path.contains(exampleImagesDir))
    }

    override fun findUsages(
        element: PsiElement,
        allElementsToDelete: Array<out PsiElement>,
        usages: MutableList<in UsageInfo>
    ): NonCodeUsageSearchInfo {
        if (element !is PsiBinaryFileDelegate || element.needFind) {
            ImageUsagesHandler(element).processElementUsages(element, {
                val usage = SafeDeleteReferenceSimpleDeleteUsageInfo(
                    it.element,
                    element,
                    -1,
                    -1,
                    it.isNonCodeUsage,
                    false
                )
                usages.add(usage)
                true
            }, FindUsagesOptions(element.project))
        }

        return NonCodeUsageSearchInfo({ element is PsiFile && allElementsToDelete.contains(element) }, element)
    }

    override fun getElementsToSearch(
        element: PsiElement,
        module: Module?,
        allElementsToDelete: MutableCollection<out PsiElement>
    ): MutableCollection<out PsiElement> {
        return mutableListOf(element)
    }

    override fun getAdditionalElementsToDelete(
        element: PsiElement,
        allElementsToDelete: MutableCollection<out PsiElement>,
        askUser: Boolean
    ): MutableCollection<PsiElement>? {
        return null
    }

    override fun findConflicts(
        element: PsiElement,
        allElementsToDelete: Array<out PsiElement>
    ): MutableCollection<String>? {
        return null
    }

    override fun preprocessUsages(project: Project, usages: Array<out UsageInfo>): Array<out UsageInfo> {
        return usages
    }

    override fun prepareForDeletion(element: PsiElement) {

    }

    override fun isToSearchInComments(element: PsiElement?): Boolean {
        return RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS
    }

    override fun setToSearchInComments(element: PsiElement?, enabled: Boolean) {
        RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS = enabled
    }

    override fun isToSearchForTextOccurrences(element: PsiElement?): Boolean {
        return RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_NON_JAVA
    }

    override fun setToSearchForTextOccurrences(element: PsiElement?, enabled: Boolean) {
        RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_NON_JAVA = enabled
    }
}
