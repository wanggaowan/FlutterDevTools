package com.wanggaowan.tools.lang

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.flutter.FlutterUtil
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.findChild
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * 多语言文本折叠，展示最终翻译后的文本
 *
 * @author Created by wanggaowan on 2023/2/19 20:19
 */
class I18nFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        // 查找需要折叠的元素
        val group = FoldingGroup.newGroup("flutter dev tools")
        val descriptors = mutableListOf<FoldingDescriptor>()
        if (root.project.isFlutterProject) {
            PsiTreeUtil.collectElementsOfType(root, DartReferenceExpression::class.java).forEach {
                if (it is DartReferenceExpression) {
                    val text = it.text
                    if (text.startsWith("S.of(") || text.startsWith("S.current.")) {
                        descriptors.add(FoldingDescriptor(it.node, it.textRange, group))
                    }
                }
            }
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String? {
        // 返回折叠的元素需要展示的文本内容
        val element = node.psi.reference?.resolve() ?: return null
        val translateFile = getTranslateFile(element.project) ?: return null
        try {
            val jsonObject = Gson().fromJson(translateFile.text, JsonObject::class.java)
            val jsonElement = jsonObject.get(element.text)
            if (jsonElement.isJsonPrimitive) {
                return jsonElement.toString()
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return true
    }

    /**
     * 查找多语言引用的字段翻译文件对象
     */
    private fun getTranslateFile(project: Project): PsiElement? {
        val l10nFile = project.findChild("l10n.yaml")?.toPsiFile(project)
        var transientFilePath: String
        if (l10nFile != null) {
            val elements = PsiTreeUtil.collectElementsOfType(l10nFile, YAMLKeyValue::class.java)
            var dir: String? = null
            var templateFile: String? = null
            for (element2 in elements) {
                if (element2.keyText == "arb-dir") {
                    dir = element2.valueText
                } else if (element2.keyText == "template-arb-file") {
                    templateFile = element2.valueText
                }

                if (dir != null && templateFile != null) {
                    break
                }
            }

            if (dir == null || templateFile == null) {
                return null
            }
            transientFilePath = dir
            transientFilePath += if (transientFilePath.endsWith("/")) {
                templateFile
            } else {
                "/${templateFile}"
            }
        } else {
            transientFilePath = "l10n/app_en.arb"
        }

        return VirtualFileManager.getInstance().findFileByUrl("file://${project.basePath}/$transientFilePath")
            ?.toPsiFile(project)
    }
}
