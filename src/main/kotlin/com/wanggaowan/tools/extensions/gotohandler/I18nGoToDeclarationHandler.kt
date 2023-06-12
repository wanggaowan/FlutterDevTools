package com.wanggaowan.tools.extensions.gotohandler

import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.lang.dart.psi.DartId
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import com.wanggaowan.tools.extensions.lang.I18nFoldingBuilder
import com.wanggaowan.tools.utils.ex.basePath
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

/**
 * 文本资源定位
 *
 * @author Created by wanggaowan on 2023/3/4 23:56
 */
object I18nGoToDeclarationHandler {
    fun getGotoDeclarationTargets(module: Module, sourceElement: PsiElement): Array<PsiElement>? {
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

        val psiFile = sourceElement.containingFile
        val isExample = if (psiFile == null) {
            false
        } else {
            val path = psiFile.virtualFile?.path
            path != null && path.startsWith("${module.basePath}/example/")
        }

        val file = I18nFoldingBuilder.getTranslateFile(module, isExample) ?: return null
        val jsonObject = file.getChildOfType<JsonObject>() ?: return arrayOf(file)

        val children = parent.children
        if (children.size != 2) {
            return arrayOf(file)
        }

        val key = children[1].text
        for (child in jsonObject.children) {
            if (child is JsonProperty) {
                if (key == child.name) {
                    return arrayOf(child)
                }
            }
        }

        return arrayOf(file)
    }
}