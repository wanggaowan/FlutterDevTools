package com.wanggaowan.tools.extensions.gotohandler

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * 路由资源定位
 *
 * @author Created by wanggaowan on 2023/5/26 10:00
 */
object RouterGoToDeclarationHandler {
    fun getGotoDeclarationTargets(module: Module, sourceElement: PsiElement): Array<PsiElement>? {
        if (sourceElement !is LeafPsiElement) {
            return null
        }

        val text = sourceElement.text.trim()
        if (!text.startsWith("/")) {
            return null
        }

        val clazz = sourceElement.getParentOfType<DartClass>(strict = true) ?: return null
        val classMembers =
            clazz.getChildOfType<DartClassBody>()?.getChildOfType<DartClassMembers>()
                ?: return null

        val findElement = mutableListOf<PsiElement>()
        val pages = getListDefine(classMembers, text)
        val method = sourceElement.getParentOfType<DartMethodDeclaration>(strict = true)
        val isInMethod =
            if (method != null) {
                method.getChildOfType<DartComponentName>()?.text != "getPages"
            } else {
                false
            }

        if (!isInMethod) {
            val elements = getMethodDefine(classMembers, text)
            if (elements.isNotEmpty()) {
                findElement.addAll(elements)
            }
        } else {
            pages?.also {
                findElement.addAll(it)
            }

            pages?.forEach {
                getResolvePage(it)?.also { page ->
                    val parentClazz = page.getParentOfType<DartClass>(strict = true)
                    if (parentClazz == null) {
                        findElement.add(page)
                    } else {
                        findElement.add(parentClazz)
                    }
                }
            }
        }

        return if (findElement.isEmpty()) null else findElement.toTypedArray()
    }

    // 获取与text内容匹配，定义在列表中的内容
    private fun getListDefine(classMembers: DartClassMembers, text: String): List<PsiElement>? {
        val varList = classMembers.getChildrenOfType<DartVarDeclarationList>()
        var pagesElement: PsiElement? = null
        for (child in varList) {
            val element = child.getChildOfType<DartVarAccessDeclaration>()
            val name = element?.getChildOfType<DartComponentName>()?.name
            if (name == "getPages") {
                val type = element.getChildOfType<DartType>()?.text
                if (!type.isNullOrEmpty() && !type.startsWith("List")) {
                    break
                }

                pagesElement = child
                break
            }
        }

        if (pagesElement == null) {
            val methodList = classMembers.getChildrenOfType<DartMethodDeclaration>()
            for (child in methodList) {
                val name = child.getChildOfType<DartComponentName>()?.name
                if (name == "getPages") {
                    val type = child.getChildOfType<DartReturnType>()?.text
                    if (!type.isNullOrEmpty() && !type.startsWith("List")) {
                        break
                    }

                    pagesElement = child
                    break
                }
            }
        }


        if (pagesElement == null) {
            return null
        }

        val dartListLiteralExpression =
            findPageListDefinitionElement(pagesElement) ?: return null

        val elements = mutableListOf<PsiElement>()
        for (child in dartListLiteralExpression.children) {
            val dartArgumentList = child.getChildOfType<DartCallExpression>()?.getChildOfType<DartArguments>()
                ?.getChildOfType<DartArgumentList>() ?: continue

            val children = dartArgumentList.children
            if (children.size < 2) {
                continue
            }

            val content = children[0]
                ?.getChildOfType<DartStringLiteralExpression>()?.text?.replace("\"", "")
                ?.replace("\'", "")?.trim()
            if (content == text) {
                elements.add(child)
                break
            }
        }
        return elements
    }

    // 获取指定节点下定义的Page界面
    private fun getResolvePage(parent: PsiElement): PsiElement? {
        val dartArgumentList = parent.getChildOfType<DartCallExpression>()?.getChildOfType<DartArguments>()
            ?.getChildOfType<DartArgumentList>() ?: return null

        val children = dartArgumentList.children
        if (children.size < 2) {
            return null
        }

        var element: PsiElement =
            children[1].getChildOfType<DartFunctionExpression>()?.getChildOfType<DartFunctionExpressionBody>()
                ?: return null
        val newElement = element.getChildOfType<DartNewExpression>()
        if (newElement != null) {
            element = newElement.getChildOfType<DartType>()?.children?.get(0)?.children?.get(0) ?: return null
            return if (element is DartReferenceExpression) {
                element.resolve()
            } else {
                null
            }
        } else {
            return element.getChildOfType<DartCallExpression>()?.getChildOfType<DartReferenceExpression>()
                ?.resolve()
        }
    }

    /**
     * 查找定义页面的列表节点
     */
    private fun findPageListDefinitionElement(parent: PsiElement): PsiElement? {
        return if (parent is DartMethodDeclaration) {
            val body = parent.getChildOfType<DartFunctionBody>()
            if (body == null) {
                null
            } else {
                var element: PsiElement? = body.getChildOfType<DartListLiteralExpression>()
                if (element != null) {
                    element
                } else {
                    element = body.getChildOfType<DartLazyParseableBlock>()?.getChildOfType<DartStatements>()
                        ?.getChildOfType<DartReturnStatement>()
                    if (element == null) {
                        null
                    } else {
                        val element2 = element.getChildOfType<DartListLiteralExpression>()
                        if (element2 != null) {
                            element2
                        } else {
                            element = element.getChildOfType<DartReferenceExpression>()
                            if (element == null) {
                                null
                            } else {
                                element = element.resolve()?.parent?.parent
                                if (element is DartVarDeclarationList) {
                                    element.getChildOfType<DartVarInit>()?.getChildOfType<DartListLiteralExpression>()
                                } else {
                                    null
                                }
                            }
                        }
                    }
                }
            }
        } else {
            parent.getChildOfType<DartVarInit>()?.getChildOfType<DartListLiteralExpression>()
        }
    }

    // 获取与text内容匹配，定义在方法中的内容
    private fun getMethodDefine(classMembers: DartClassMembers, text: String): List<PsiElement> {
        val children = classMembers.getChildrenOfType<DartMethodDeclaration>()
        val elements = mutableListOf<PsiElement>()
        for (child in children) {
            val callExpressions = PsiTreeUtil.findChildrenOfAnyType(child, DartCallExpression::class.java)
            for (callExpression in callExpressions) {
                val router = callExpression.getChildOfType<DartArguments>()
                    ?.getChildOfType<DartArgumentList>()?.firstChild?.text?.replace("\"", "")
                    ?.replace("\'", "")?.trim()
                if (router == text) {
                    // 先只查找一个，一般情况下都是一对一
                    elements.add(child)
                    break
                }
            }
        }
        return elements
    }
}
