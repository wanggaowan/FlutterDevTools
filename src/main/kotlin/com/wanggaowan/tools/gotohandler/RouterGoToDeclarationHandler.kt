package com.wanggaowan.tools.gotohandler

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
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

        val varList = classMembers.getChildrenOfType<DartVarDeclarationList>()
        var pagesElement: PsiElement? = null
        for (child in varList) {
            val element = child.getChildOfType<DartVarAccessDeclaration>()
            val name = element?.getChildOfType<DartComponentName>()?.name
            if (name == "getPages") {
                val type = element.getChildOfType<DartType>()?.text
                if (!type.isNullOrEmpty() && !type.startsWith("List")) {
                    return null
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
                        return null
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
                var element: PsiElement =
                    children[1].getChildOfType<DartFunctionExpression>()?.getChildOfType<DartFunctionExpressionBody>()
                        ?: return null
                val newElement = element.getChildOfType<DartNewExpression>()
                if (newElement != null) {
                    element = newElement.getChildOfType<DartType>()?.children?.get(0)?.children?.get(0) ?: return null
                    return if (element is DartReferenceExpression) {
                        val find = element.resolve()
                        if (find == null) null else arrayOf(find)
                    } else {
                        null
                    }
                } else {
                    val find = element.getChildOfType<DartCallExpression>()?.getChildOfType<DartReferenceExpression>()?.resolve()
                    return if (find == null) null else arrayOf(find)
                }
            }
        }

        return null
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
}
