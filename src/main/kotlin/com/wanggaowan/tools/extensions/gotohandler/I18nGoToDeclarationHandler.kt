package com.wanggaowan.tools.extensions.gotohandler

import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.lang.dart.psi.DartId
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import com.wanggaowan.tools.extensions.lang.I18nFoldingBuilder
import com.wanggaowan.tools.utils.dart.NameWrapperPsiElement
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
        var splits: List<String> = text.split(".")
        if (splits.size < 3) {
            return null
        }

        splits = splits.map { it.replace("\n", "").replace(" ", "") }
        val sourceText = sourceElement.text
        // 一般多语言调用格式为S.of(context).txt或S.current.txt
        if (splits[2] != sourceText || splits[0] != "S" || (splits[1] != "current" && !splits[1].startsWith("of("))) {
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
        val allFiles = mutableListOf(file)
        file.parent?.children?.forEach {
            if (it !is PsiFile) {
                return@forEach
            }

            val name = it.name
            if (name == file.name) {
                return@forEach
            }

            if (name.lowercase().endsWith(".arb")) {
                allFiles.add(it)
            }
        }

        val findElements = mutableListOf<PsiElement>()
        allFiles.forEach {
            it.getChildOfType<JsonObject>()?.findProperty(sourceText)?.also { jsonProperty ->
                findElements.add(NameWrapperPsiElement(jsonProperty) { node ->
                    var text2 = (node as JsonProperty).value?.text ?: ""
                    if (text2.length > 50) {
                        text2 = "${text2.substring(0, 50)}\" Click show details"
                    }
                    return@NameWrapperPsiElement "${it.name}: $text2"
                })
            }
        }

        if (findElements.isEmpty()) {
            return arrayOf(file)
        }

        return findElements.toTypedArray()
    }
}
