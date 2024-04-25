package com.wanggaowan.tools.extensions.gotohandler

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.lang.dart.psi.*
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.ex.basePath
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

/**
 * 图片资源定位
 *
 * @author Created by wanggaowan on 2023/3/4 23:55
 */
object ImagesGoToDeclarationHandler {
    fun getGotoDeclarationTargets(
        module: Module,
        sourceElement: PsiElement,
        highResolutionFile: Boolean = false
    ): Array<PsiElement>? {
        if (sourceElement !is LeafPsiElement) {
            return null
        }

        var parent = sourceElement.parent
        if (parent !is DartId) {
            return null
        }

        parent = parent.parent
        if (parent !is DartReferenceExpression) {
            return null
        }

        parent = parent.parent
        if (parent !is DartReferenceExpression) {
            return null
        }

        val text = parent.text ?: return null
        var splits: List<String> = text.split(".")
        if (splits.size < 2) {
            return null
        }

        splits = splits.map { it.replace("\n", "").replace(" ", "") }
        if (splits[0] != PluginSettings.getImagesRefClassName(module.project) || splits[1] != sourceElement.text) {
            return null
        }

        val reference = (sourceElement.parent.parent as DartReferenceExpression).resolve() ?: return null
        if (reference !is DartComponentName) {
            return null
        }

        parent = reference.parent ?: return null
        if (parent !is DartGetterDeclaration) {
            return null
        }

        val bodyElement = parent.getChildOfType<DartFunctionBody>() ?: return null
        val pathElement =
            bodyElement.getChildOfType<DartStringLiteralExpression>()?.firstChild?.nextSibling ?: return null
        val isExample = bodyElement.containingFile?.virtualFile?.path?.startsWith("${module.basePath}/example/")
        val elements = findFile(module, pathElement.text, isExample == true, highResolutionFile)
        if (elements.isEmpty()) {
            return null
        }
        return elements.toTypedArray()
    }

    /**
     * 查找文件，如果需要优先返回高分辨率图片，则[highResolutionFile]传true即可
     */
    fun findFile(
        module: Module,
        imageRelPath: String,
        isExample: Boolean,
        highResolutionFile: Boolean = false
    ): List<PsiElement> {
        return if (highResolutionFile) findFileByHighResolution(module, imageRelPath, isExample) else
            findFileByLowerResolution(module, imageRelPath, isExample)
    }

    private fun findFileByHighResolution(module: Module, path: String, isExample: Boolean): List<PsiElement> {
        val index = path.lastIndexOf("/")
        val imageDir = if (index == -1) "" else path.substring(0, index)
        val imageName = if (index == -1) path else path.substring(index + 1)
        val data = mutableListOf<PsiElement>()

        val basePath = module.basePath + (if (isExample) "/example" else "")
        var element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/4.0x/$imageName")
            ?.toPsiFile(module.project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/3.0x/$imageName")
            ?.toPsiFile(module.project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/2.0x/$imageName")
            ?.toPsiFile(module.project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/1.5x/$imageName")
            ?.toPsiFile(module.project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$path")?.toPsiFile(module.project)
        if (element != null) {
            data.add(element)
            return data
        }

        return data
    }

    private fun findFileByLowerResolution(module: Module, path: String, isExample: Boolean): List<PsiElement> {
        val index = path.lastIndexOf("/")
        val imageDir = if (index == -1) "" else path.substring(0, index)
        val imageName = if (index == -1) path else path.substring(index + 1)
        val data = mutableListOf<PsiElement>()

        val basePath = module.basePath + (if (isExample) "/example" else "")
        var element =
            VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$path")?.toPsiFile(module.project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/1.5x/$imageName")
            ?.toPsiFile(module.project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/2.0x/$imageName")
            ?.toPsiFile(module.project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/3.0x/$imageName")
            ?.toPsiFile(module.project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/4.0x/$imageName")
            ?.toPsiFile(module.project)
        if (element != null) {
            data.add(element)
            return data
        }
        return data
    }
}
