package com.wanggaowan.tools.description

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.lang.dart.ide.documentation.DartDocumentationProvider
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import com.wanggaowan.tools.gotohandler.ImagesGoToDeclarationHandler
import com.wanggaowan.tools.utils.dart.DartPsiUtils
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
        return null
    }

    override fun getCustomDocumentationElement(
        editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int
    ): PsiElement? {
        val project = editor.project
        if (project != null && !project.isFlutterProject) {
            return null
        }

        // 需要特殊显示的节点，需要生成自定义节点返回，然后才会进入generateHoverDoc/generateDoc方法
        val imageElement = getCustomImageDocumentationElement(contextElement)
        if (imageElement != null) {
            return imageElement
        }

        return super.getCustomDocumentationElement(editor, file, contextElement, targetOffset)
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? {
        if (!element.project.isFlutterProject) {
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
     * 创建图片自定义节点
     */
    private fun getCustomImageDocumentationElement(contextElement: PsiElement?): PsiElement? {
        if (contextElement == null) {
            return null
        }

        val parent = contextElement.parent?.parent?.parent
        if (parent !is DartReferenceExpression) {
            return null
        }

        val text = contextElement.text
        if (parent.textMatches("Images.${text}")) {
            // 如果是简单的Document文档，可以直接使用Dart文件节点, 复杂的则返回自定义节点，然后会执行generateHoverDoc/generateDoc，这两个方法支持完整的html语法
            return DartPsiUtils.createCommonElement(contextElement.project, IMAGE_ELEMENT_PREFIX + text)
        }
        return null
    }

    /**
     * 生成DOC
     */
    private fun getDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null || originalElement == null || originalElement !is LeafPsiElement) {
            return null
        }

        val text = element.text
        if (text != null && !text.startsWith(IMAGE_ELEMENT_PREFIX)) {
            return null
        }

        val imageFiles =
            ImagesGoToDeclarationHandler.getGotoDeclarationTargets(element.project, originalElement, 0, null)
        if (imageFiles.isNullOrEmpty()) {
            return null
        }

        val image = imageFiles[0]
        if (image !is PsiFile) {
            return null
        }

        val imgPath: String = image.virtualFile.path
        val projectPath = element.project.basePath ?: ""
        val read = ImageIO.read(File(imgPath).inputStream())
        val width = read?.width ?: 200
        val height = read?.height ?: 200
        val scale = (width / 200).coerceAtLeast(height / 200)
        return if (scale <= 1) {
            "<img src=\"${imgPath}\" width=\"${width}\" height=\"${height}\"><br>${
                imgPath.replace(projectPath, "")
            }"
        } else {
            "<img src=\"${imgPath}\" width=\"${width / scale}\" height=\"${height / scale}\"><br>${
                imgPath.replace(projectPath, "")
            }"
        }
    }

    companion object {
        // 自定义图片标识
        const val IMAGE_ELEMENT_PREFIX = "ImageElement_"
    }
}
