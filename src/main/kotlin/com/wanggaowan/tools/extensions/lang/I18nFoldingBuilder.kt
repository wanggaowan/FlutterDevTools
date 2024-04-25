package com.wanggaowan.tools.extensions.lang

import com.intellij.json.psi.JsonObject
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.psi.DartArgumentList
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.findChild
import com.wanggaowan.tools.utils.ex.findModule
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * 多语言文本折叠，展示最终翻译后的文本
 *
 * @author Created by wanggaowan on 2023/2/19 20:19
 */
class I18nFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean
    ): Array<FoldingDescriptor> { // 查找需要折叠的元素
        val group = FoldingGroup.newGroup("flutter dev tools")
        val descriptors = mutableListOf<FoldingDescriptor>()

        if (root.module.isFlutterProject) {
            PsiTreeUtil.collectElementsOfType(root, DartReferenceExpression::class.java).forEach {
                if (it is DartReferenceExpression) {
                    val text = it.text.replace("\n", "").replace(" ", "")
                    if (text.startsWith("S.of(") || text.startsWith("S.current.")) {
                        val parent = it.parent
                        if (parent is DartCallExpression) {
                            descriptors.add(FoldingDescriptor(parent.node, parent.textRange, group))
                        } else {
                            descriptors.add(FoldingDescriptor(it.node, it.textRange, group))
                        }
                    }
                }
            }
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String? { // 返回折叠的元素需要展示的文本内容
        val element = node.psi ?: return null
        val module = element.findModule() ?: return null

        val psiFile = element.containingFile
        val isExample = if (psiFile == null) {
            false
        } else {
            val path = psiFile.virtualFile?.path
            path != null && path.startsWith("${module.basePath}/example/")
        }

        val translateFile = getTranslateFile(module, isExample) ?: return null
        try {
            val args: Array<PsiElement>?
            val key: String = if (element is DartCallExpression) {
                val children = element.children
                args = children[1].getChildOfType<DartArgumentList>()?.children
                children[0].children[1].text
            } else {
                args = null
                element.children[1].text
            }

            val jsonProperty = translateFile.getChildOfType<JsonObject>()?.findProperty(key)
            if (jsonProperty != null) {
                var text = jsonProperty.value?.text
                if (!text.isNullOrEmpty() && !args.isNullOrEmpty()) {
                    text = appendArgs(text, args)
                }
                return text
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun appendArgs(text: String, args: Array<PsiElement>): String {
        val regex = Regex("\\{[^{}]*}")
        val results = regex.findAll(text).iterator()
        var index = 0
        val argSize = args.size
        var newStr = text
        results.forEach {
            if (index > argSize) {
                return@forEach
            }
            val arg = args[index]
            newStr = if (arg is DartReferenceExpression) {
                if (arg.getChildrenOfType<DartReferenceExpression>().isNotEmpty()) {
                    newStr.replace(it.value, "\${${args[index].text}}")
                } else {
                    newStr.replace(it.value, "\$${args[index].text}")
                }
            } else if (arg is DartStringLiteralExpression) {
                newStr.replace(it.value, arg.firstChild?.nextSibling?.text ?: arg.text)
            } else {
                newStr.replace(it.value, arg.text ?: "")
            }
            index++
        }
        return newStr
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return true
    }

    companion object {
        /**
         * 查找多语言引用的字段翻译文件对象
         */
        internal fun getTranslateFile(module: Module, isExample: Boolean = false): PsiFile? {
            val basePath = module.basePath ?: return null
            val l10nFile = if (isExample) {
                module.findChild("example")?.findChild("l10n.yaml")?.toPsiFile(module.project)
            } else {
                module.findChild("l10n.yaml")?.toPsiFile(module.project)
            }
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
                transientFilePath = "lib/l10n/app_en.arb"
            }

            if (isExample) {
                return VirtualFileManager.getInstance()
                    .findFileByUrl("file://$basePath/example/$transientFilePath")?.toPsiFile(module.project)
            }

            return VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$transientFilePath")
                ?.toPsiFile(module.project)
        }
    }
}
