package com.wanggaowan.tools.gotohandler

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.lang.dart.psi.*
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

/**
 * 图片资源定位
 *
 * @author Created by wanggaowan on 2023/3/4 23:55
 */
object ImagesGoToDeclarationHandler {
    fun getGotoDeclarationTargets(
        project: Project,
        sourceElement: PsiElement, offset: Int, editor: Editor?
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
        if (!text.startsWith("Images.")) {
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
        val pathElement = bodyElement.getChildOfType<DartStringLiteralExpression>() ?: return null
        val elements = findFile(project, pathElement.text.replace("'", ""))
        if (elements.isEmpty()) {
            return null
        }
        return elements.toTypedArray()
    }

    fun findFile(project: Project, path: String): List<PsiElement> {
        val index = path.lastIndexOf("/")
        val imageDir = if (index == -1) "" else path.substring(0, index)
        val imageName = if (index == -1) path else path.substring(index + 1)
        val data = mutableListOf<PsiElement>()

        val basePath = project.basePath
        var element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/4.0x/$imageName")
            ?.toPsiFile(project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/3.0x/$imageName")
            ?.toPsiFile(project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/2.0x/$imageName")
            ?.toPsiFile(project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$imageDir/1.5x/$imageName")
            ?.toPsiFile(project)
        if (element != null) {
            data.add(element)
            return data
        }

        element = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$path")?.toPsiFile(project)
        if (element != null) {
            data.add(element)
            return data
        }

        return data
    }
}
