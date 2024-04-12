package com.wanggaowan.tools.extensions.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.ui.ColorPicker
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import com.jetbrains.lang.dart.DartTokenTypes
import com.jetbrains.lang.dart.psi.*
import io.flutter.editor.FlutterColorProvider
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import java.awt.Color
import java.awt.event.MouseEvent

/**
 * 在代码行数栏展示当前行包含的图片文件
 *
 * @author Created by wanggaowan on 2023/3/6 22:19
 */
class ColorLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.node.elementType != DartTokenTypes.IDENTIFIER) {
            return null
        }

        var parent = element.parent?.parent ?: return null
        if (parent !is DartReferenceExpression) {
            return null
        }

        if (parent.parent.lastChild != parent) {
            return null
        }

        val callName = parent.parent.firstChild.text
        if (callName == "Colors" || callName == "CupertinoColors") {
            // Flutter插件已支持此格式
            return null
        }

        val reference = parent.resolve() ?: return null
        if (reference !is DartComponentName) {
            return null
        }

        parent = reference.parent ?: return null
        if (parent !is DartVarAccessDeclaration) {
            return null
        }

        parent = parent.parent ?: return null
        if (parent !is DartVarDeclarationList) {
            return null
        }

        val callExpression = parent.getChildOfType<DartVarInit>()?.getChildOfType<DartCallExpression>() ?: return null
        val colorElement =
            callExpression.getChildOfType<DartReferenceExpression>()?.firstChild?.firstChild ?: return null
        val color = try {
            FlutterColorProvider().getColorFrom(colorElement)
        } catch (e: Exception) {
            null
        } ?: return null
        return createLineMarkerInfo(element, color)
    }

    private fun createLineMarkerInfo(element: PsiElement, color: Color): LineMarkerInfo<*> {
        val size = JBUI.scale(12)
        val icon = ColorIcon(size, color)
        return LineMarkerInfo(
            element, element.textRange, icon, null,
            { e, _ -> navigate(e, element, color) }, GutterIconRenderer.Alignment.LEFT
        ) { "" }
    }

    private fun navigate(e: MouseEvent, element: PsiElement, color: Color) {
        try {
            val relativePoint = RelativePoint(e.component, e.point)
            ColorPicker.showColorPickerPopup(element.project, color, { _, _ ->

            }, relativePoint, true)
        } catch (e: Exception) {
            //
        }
    }
}
