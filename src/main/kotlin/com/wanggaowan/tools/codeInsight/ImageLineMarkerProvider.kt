package com.wanggaowan.tools.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ui.JBUI
import com.wanggaowan.tools.gotohandler.ImagesGoToDeclarationHandler
import java.awt.Image
import java.awt.event.MouseEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.ImageIcon

/**
 * 在代码行数栏展示当前行包含的图片文件
 *
 * @author Created by wanggaowan on 2023/3/6 22:19
 */
class ImageLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val files = ImagesGoToDeclarationHandler.getGotoDeclarationTargets(element.project, element, 0, null)
        if (files.isNullOrEmpty()) {
            return null
        }

        val file = files[0]
        if (file !is PsiFile) {
            return null
        }

        val read = try {
            ImageIO.read(File(file.virtualFile.path).inputStream())
        } catch (e: Exception) {
            return null
        }

        val size = JBUI.scale(15)
        val icon = ImageIcon(read.getScaledInstance(size, size, Image.SCALE_FAST))
        return LineMarkerInfo(
            element, element.textRange, icon, null,
            { e, _ -> clickIcon(e, file) },
            GutterIconRenderer.Alignment.LEFT
        ) { "" }
    }

    private fun clickIcon(e: MouseEvent, file: PsiFile) {
        FileEditorManagerImpl.getInstance(file.project).openFile(file.virtualFile, true)
    }
}
