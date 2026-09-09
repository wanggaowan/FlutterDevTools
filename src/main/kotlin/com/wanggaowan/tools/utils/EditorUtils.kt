package com.wanggaowan.tools.utils

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.core.util.toPsiFile

/**
 * 编辑器工具
 *
 * @author Created by wanggaowan on 2026/9/9 09:14
 */
object EditorUtils {
    /**
     * 获取编辑器中选中区域的多个PsiElement
     *
     * 需在EDT线程执行
     *
     * [convert]对PsiElement进行进一步查找或转化
     */
    fun <T : PsiElement> getSelectionAreaPsiElement(editor: Editor, convert: (element: PsiElement) -> T?): List<T>? {
        val project = editor.project ?: return null
        val psiFile = editor.virtualFile?.toPsiFile(project) ?: return null
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) {
            return null
        }

        val start = selectionModel.selectionStart
        val end = selectionModel.selectionEnd
        val properties = mutableListOf<T>()
        var offset = start
        while (offset < end) {
            val element = psiFile.findElementAt(offset)
            var property: T? = null
            if (element != null) {
                property = convert.invoke(element)
            }

            if (property == null) {
                offset = (element?.textRange?.endOffset ?: offset) + 1
                continue
            }

            val range = property.textRange
            if (range.startOffset < end && range.endOffset > start) {
                properties.add(property)
            }
            offset = range.endOffset
        }

        return properties
    }

    /**
     * 获取光标所在的PsiElement，需在EDT线程执行
     */
    fun getSelectionPsiElement(editor: Editor): PsiElement? {
        val project = editor.project ?: return null
        val psiFile = editor.virtualFile?.toPsiFile(project) ?: return null
        return psiFile.findElementAt(editor.caretModel.offset)
    }
}
