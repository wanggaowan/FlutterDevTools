package com.wanggaowan.tools.extensions.description

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import java.text.DecimalFormat

/**
 * 屏幕尺寸描述
 *
 * 提供不同设计图尺寸之间的转换
 *
 * @author Created by wanggaowan on 2023/8/8 08:50
 */
class ScreenSizeDocumentDescriptionProvider : DocumentationProvider {
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        return getDoc(element, originalElement)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        return getDoc(element, originalElement)
    }

    /**
     * 生成DOC
     */
    private fun getDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null || originalElement == null || originalElement !is LeafPsiElement) {
            return null
        }

        var text: String? = element.text?.replace("\"", "")?.replace("'", "")
        if (text == null || (!text.endsWith("px") && !text.endsWith("dp"))) {
            text = originalElement.text.replace("\"", "").replace("'", "")
        }

        if (text.endsWith("px")) {
            text = text.replace("px", "")
            return try {
                val size = text.toFloat()
                val format = DecimalFormat("#.##")
                "414设计图尺寸：" + format.format(size * 0.38333333333) + "dp"
            } catch (_: NumberFormatException) {
                null
            }
        }


        if (text.endsWith("dp")) {
            text = text.replace("dp", "")
            return try {
                val size = text.toFloat()
                val format = DecimalFormat("#.##")
                "1080设计图尺寸：" + format.format(size / 0.38333333333) + "px"
            } catch (_: NumberFormatException) {
                null
            }
        }

        return null
    }

    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int): PsiElement? {
        if (contextElement != null) {
            try {
                val parent = contextElement.parent.parent.parent
                var text = parent.text
                var valid = false
                if (text.startsWith("SimpleUtil.getScaledValue")) {
                    valid = true

                } else {
                    text = parent.parent.parent.text
                    if (text.startsWith("SimpleUtil.getScaledValue")) {
                        valid = true
                    }
                }

                if (valid) {
                    text = contextElement.text
                    val value = text.toFloat()
                    var element: PsiElement? = PsiElementFactory.getInstance(editor.project).createFieldFromText("String a = \"${value}px\";", null)
                    element = element?.getChildOfType<PsiLiteralExpression>()
                    if (element != null) {
                        return element
                    }
                }
            } catch (_: Exception) {
                // 不满足要求
            }
        }

        return super.getCustomDocumentationElement(editor, file, contextElement, targetOffset)
    }
}
