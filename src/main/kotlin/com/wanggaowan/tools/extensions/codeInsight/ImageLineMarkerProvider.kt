package com.wanggaowan.tools.extensions.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ui.JBUI
import com.jetbrains.lang.dart.psi.DartFunctionBody
import com.jetbrains.lang.dart.psi.DartGetterDeclaration
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression
import com.wanggaowan.tools.extensions.gotohandler.ImagesGoToDeclarationHandler
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.findModule
import icons.FlutterDevToolsIcons

/**
 * 在代码行数栏展示当前行包含的图片文件
 *
 * @author Created by wanggaowan on 2023/3/6 22:19
 */
class ImageLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? { // 查找Images.homeIcSaoma类型节点引用的图片文件
        val module = element.findModule() ?: return null
        val files = ImagesGoToDeclarationHandler.getGotoDeclarationTargets(module, element, true)
        if (!files.isNullOrEmpty()) {
            return createLineMarkerInfo(element, files[0])
        }

        // 查找Images.dart中定义的图片，比如：static String get fileOutlined => 'assets/images/file_outlined.png';
        // element为'assets/images/file_outlined.png'节点
        if (element !is DartStringLiteralExpression) {
            return null
        }

        val leafElement = element.firstChild?.nextSibling ?: return null
        val path = leafElement.text
        if (!path.isNullOrEmpty() && element.parent is DartFunctionBody && element.parent.parent is DartGetterDeclaration) {
            val isExample = element.containingFile?.virtualFile?.path?.startsWith("${module.basePath}/example/")
            val fileList = ImagesGoToDeclarationHandler.findFile(module, path, isExample == true, true)
            if (fileList.isNotEmpty()) {
                return createLineMarkerInfo(leafElement, fileList[0])
            }
        }

        return null
    }

    private fun createLineMarkerInfo(element: PsiElement, file: PsiElement): LineMarkerInfo<*>? {
        if (file !is PsiFile) {
            return null
        }

        val size = JBUI.scale(15)
        val icon = FlutterDevToolsIcons.getIcon(file.virtualFile.path, size, size) ?: return null
        return LineMarkerInfo(
            element, element.textRange, icon, null,
            { _, _ -> clickIcon(file) }, GutterIconRenderer.Alignment.LEFT
        ) { "" }
    }

    private fun clickIcon(file: PsiFile) {
        val virtualFile = file.virtualFile
        val parent = virtualFile.parent
        val name = parent?.name
        if (parent != null && (name == "1.5x" || name == "2.0x" || name == "3.0x" || name == "4.0x")) {
            val child = parent.parent?.findChild(file.name)
            if (child != null) {
                FileEditorManager.getInstance(file.project).openFile(child, true)
                return
            }
        }

        FileEditorManager.getInstance(file.project).openFile(virtualFile, true)
    }
}
