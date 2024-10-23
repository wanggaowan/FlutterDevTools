package com.wanggaowan.tools.extensions.lang

import com.intellij.json.psi.JsonObject
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.psi.*
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
    ): Array<FoldingDescriptor> {
        // 查找需要折叠的元素
        val descriptors = mutableListOf<FoldingDescriptor>()
        if (root.module.isFlutterProject) {
            PsiTreeUtil.collectElementsOfType(root, DartReferenceExpression::class.java).forEach {
                if (it !is DartReferenceExpression) {
                    return@forEach
                }

                val element = getCouldFoldPsiElement(it) ?: return@forEach
                val descriptor = createFoldingDescriptor(element) ?: return@forEach
                descriptors.add(descriptor)
            }
        }

        return descriptors.toTypedArray()
    }

    private fun getCouldFoldPsiElement(element: DartReferenceExpression): PsiElement? {
        val text = element.text.replace("\n", "").replace(" ", "")
        val splits = text.split('.')
        if (splits.size != 3) {
            return null
        }

        if (splits[0] != "S") {
            return null
        }

        if (splits[1] != "current" && !splits[1].startsWith("of(")) {
            return null
        }

        return element
    }

    private fun createFoldingDescriptor(element: PsiElement): FoldingDescriptor? {
        // 如果需要多个折叠同时展开，则指定同一个group即可
        // val group = FoldingGroup.newGroup("flutter dev tools")
        val parent = element.parent
        val key = Key<String>("I18nFoldStr")
        if (parent is DartLongTemplateEntry) {
            val str = getPlaceholderText(parent)
            if (str == null) {
                parent.node.putUserData(key, null)
                return null
            }

            parent.node.putUserData(key, str)
            return FoldingDescriptor(parent.node, parent.textRange, null)
        }

        if (parent is DartCallExpression) {
            val parent2 = parent.parent
            if (parent2 is DartLongTemplateEntry) {
                val str = getPlaceholderText(parent2)
                if (str == null) {
                    parent2.node.putUserData(key, null)
                    return null
                }

                parent2.node.putUserData(key, str)
                return FoldingDescriptor(parent2.node, parent2.textRange, null)
            } else {
                val str = getPlaceholderText(parent)
                if (str == null) {
                    parent.node.putUserData(key, null)
                    return null
                }

                parent.node.putUserData(key, str)
                return FoldingDescriptor(parent.node, parent.textRange, null)
            }
        }

        val str = getPlaceholderText(element)
        if (str == null) {
            element.node.putUserData(key, null)
            return null
        }

        element.node.putUserData(key, str)
        return FoldingDescriptor(element.node, element.textRange, null)
    }

    override fun getPlaceholderText(node: ASTNode): String? {
        // 返回折叠的元素需要展示的文本内容
        val key = Key<String>("I18nFoldStr")
        var str = node.getUserData(key)
        if (str != null) {
            return str
        }

        str = getPlaceholderText(node.psi)
        if (str != null) {
            node.putUserData(key, str)
        }

        return str
    }

    private fun getPlaceholderText(psiElement: PsiElement?): String? {
        var element = psiElement ?: return null
        if (element is DartLongTemplateEntry) {
            element = element.children[0]
        }

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
                var text = jsonProperty.value?.text?.replace("\"", "")
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
            newStr = when (arg) {
                is DartReferenceExpression -> {
                    val element = getCouldFoldPsiElement(arg)
                    val str =
                        if (element != null) {
                            getPlaceholderText(element) ?: ""
                        } else if (arg.getChildrenOfType<DartReferenceExpression>().isNotEmpty()) {
                            "\${${arg.text}}"
                        } else {
                            "\$${arg.text}"
                        }

                    newStr.replace(it.value, str)
                }

                is DartStringLiteralExpression -> {
                    newStr.replace(it.value, arg.firstChild?.nextSibling?.text ?: arg.text)
                }

                else -> {
                    newStr.replace(it.value, arg.text ?: "")
                }
            }
            index++
        }
        return newStr
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return true
    }
}

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
