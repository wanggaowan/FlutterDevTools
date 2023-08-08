package com.wanggaowan.tools.extensions.description

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
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

        var text = originalElement.text
        if (text.endsWith("px")) {
            text = text.replace("px", "")
            return try {
                val size = text.toInt()
                val format = DecimalFormat("#.##")
                "414设计图尺寸：" + format.format(size * 0.38333333333) + "dp"
            } catch (e: NumberFormatException) {
                null
            }
        }


        if (text.endsWith("dp")) {
            text = text.replace("dp", "")
            return try {
                val size = text.toInt()
                val format = DecimalFormat("#.##")
                "1080设计图尺寸：" + format.format(size / 0.38333333333) + "px"
            } catch (e: NumberFormatException) {
                null
            }
        }

        return null
    }
}
