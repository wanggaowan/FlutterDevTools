package com.wanggaowan.tools.extensions.description

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.wanggaowan.tools.extensions.gotohandler.ImagesGoToDeclarationHandler
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.findModule
import com.wanggaowan.tools.utils.ex.isFlutterProject
import java.io.File
import javax.imageio.ImageIO

/**
 * 提供图片文件描述
 *
 * @author Created by wanggaowan on 2023/3/4 23:55
 */
class ImageDocumentDescriptionProvider : DocumentationProvider {
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element != null && !element.project.isFlutterProject) {
            return null
        }

        return getDoc(element, originalElement)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element != null && !element.project.isFlutterProject) {
            return null
        }

        return getDoc(element, originalElement)
    }

    /**
     * 生成DOC
     */
    private fun getDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null || originalElement == null || originalElement !is LeafPsiElement) {
            return null
        }

        val module = element.findModule() ?: return null
        val imageFiles =
            ImagesGoToDeclarationHandler.getGotoDeclarationTargets(module, originalElement, true)
        if (imageFiles.isNullOrEmpty()) {
            return null
        }

        // element是originalElement引用对象的原始文件,此处element就是Images.dart中定义的内容
        val image = imageFiles[0]
        if (image !is PsiFile) {
            return null
        }

        val virtualFile = image.virtualFile
        val imgPath: String = virtualFile.path
        val parent = virtualFile.parent
        val parentName = parent?.name
        val showImgPath =
            if (parentName == "1.5x" || parentName == "2.0x" || parentName == "3.0x" || parentName == "4.0x") {
                parent.parent?.findChild(virtualFile.name)?.path ?: imgPath
            } else {
                imgPath
            }

        val modulePath = module.basePath ?: ""
        val read = try {
            ImageIO.read(File(imgPath).inputStream())
        } catch (_: Exception) {
            null
        }
        val width = read?.width ?: 200
        val height = read?.height ?: 200
        if (width <= 0 || height <= 0) {
            return "<img src=\"\"><br>${
                showImgPath.replace(modulePath, "")
            }"
        }

        if (width < 100 && height < 100) {
            val scale = (100 / width).coerceAtLeast(100 / height)
            return "<img src=\"file:///${imgPath}\" width=\"${width * scale}\" height=\"${height * scale}\"><br>${
                showImgPath.replace(modulePath, "")
            }"
        }

        val scale = (width / 200).coerceAtLeast(height / 200)
        return if (scale <= 1) {
            "<img src=\"file:///${imgPath}\" width=\"${width}\" height=\"${height}\"><br>${
                showImgPath.replace(modulePath, "")
            }"
        } else {
            "<img src=\"file:///${imgPath}\" width=\"${width / scale}\" height=\"${height / scale}\"><br>${
                showImgPath.replace(modulePath, "")
            }"
        }
    }
}
