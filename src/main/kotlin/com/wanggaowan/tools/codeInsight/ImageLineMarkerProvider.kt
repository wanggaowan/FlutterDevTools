package com.wanggaowan.tools.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ui.JBUI
import com.jetbrains.lang.dart.psi.DartFunctionBody
import com.jetbrains.lang.dart.psi.DartGetterDeclaration
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression
import com.wanggaowan.tools.gotohandler.ImagesGoToDeclarationHandler
import com.wanggaowan.tools.utils.ex.findModule
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
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? { // 查找Images.homeIcSaoma类型节点引用的图片文件
        val module = element.findModule() ?: return null
        val files = ImagesGoToDeclarationHandler.getGotoDeclarationTargets(module, element)
        if (!files.isNullOrEmpty()) {
            return createLineMarkerInfo(element, files[0])
        }

        // 查找Images.dart中定义的图片，比如：static String get fileOutlined => 'assets/images/file_outlined.png';
        // element为'assets/images/file_outlined.png'节点
        if (element is DartStringLiteralExpression) {
            if (element.parent is DartFunctionBody && element.parent.parent is DartGetterDeclaration) {
                val fileList = ImagesGoToDeclarationHandler.findFile(
                    module, element.text.replace("'", "").replace("\"", "")
                )
                if (fileList.isNotEmpty()) {
                    return createLineMarkerInfo(element, fileList[0])
                }
            }
        }

        return null
    }

    private fun createLineMarkerInfo(element: PsiElement, file: PsiElement): LineMarkerInfo<*>? {
        if (file !is PsiFile) {
            return null
        }

        val read = try {
            ImageIO.read(File(file.virtualFile.path).inputStream())
        } catch (e: Exception) {
            return null
        } ?: return null

        val size = JBUI.scale(15)
        val icon = ImageIcon(read.getScaledInstance(size, size, Image.SCALE_FAST))
        return LineMarkerInfo(
            element, element.textRange, icon, null, { e, _ -> clickIcon(e, file) }, GutterIconRenderer.Alignment.LEFT
        ) { "" }
    }

    private fun clickIcon(e: MouseEvent, file: PsiFile) {
        FileEditorManagerImpl.getInstance(file.project).openFile(file.virtualFile, true)
    }
}
