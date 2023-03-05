package com.wanggaowan.tools.gotohandler

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.lang.dart.psi.DartId
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import com.wanggaowan.tools.lang.I18nFoldingBuilder

/**
 * 文本资源定位
 *
 * @author Created by wanggaowan on 2023/3/4 23:56
 */
object I18nGoToDeclarationHandler {
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
        if (!text.startsWith("S.current")
            && !text.startsWith("S.of(")
        ) {
            return null
        }

        val file = I18nFoldingBuilder.getTranslateFile(project) ?: return null
        return arrayOf(file)
    }
}
