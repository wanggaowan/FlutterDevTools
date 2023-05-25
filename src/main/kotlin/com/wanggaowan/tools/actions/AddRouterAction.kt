package com.wanggaowan.tools.actions

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.lang.dart.psi.*
import com.wanggaowan.tools.ui.AddRouterDialog
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType

/**
 * 增加路由
 *
 * @author Created by wanggaowan on 2023/4/26 14:53
 */
class AddRouterAction : DumbAwareAction() {

    override fun update(e: AnActionEvent) {
        if (!e.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor == null) {
            e.presentation.isVisible = false
            return
        }

        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null && !virtualFile.isDirectory) {
            e.presentation.isVisible = virtualFile.name.endsWith(".dart")
            return
        }

        e.presentation.isVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return

        val dialog = AddRouterDialog(project)
        dialog.show()
        if (dialog.exitCode != DialogWrapper.OK_EXIT_CODE) {
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "add router") {
            override fun run(progressIndicator: ProgressIndicator) {
                progressIndicator.isIndeterminate = true
                WriteCommandAction.runWriteCommandAction(project) {
                    val children = psiFile.getChildrenOfType<DartClass>()
                    var routeMapClass: PsiElement? = null
                    for (child in children) {
                        val name = child.getChildOfType<DartComponentName>()?.name
                        if (name == "RouteMap") {
                            routeMapClass = child
                            break
                        }
                    }

                    if (routeMapClass == null) {
                        routeMapClass =
                            DartPsiUtils.createClassElement(project, "RouteMap") ?: return@runWriteCommandAction
                        routeMapClass = psiFile.add(routeMapClass)
                    }

                    val classMembers =
                        routeMapClass!!.getChildOfType<DartClassBody>()?.getChildOfType<DartClassMembers>()
                            ?: return@runWriteCommandAction
                    val varList = classMembers.getChildrenOfType<DartVarDeclarationList>()
                    var pagesElement: PsiElement? = null
                    for (child in varList) {
                        val element = child.getChildOfType<DartVarAccessDeclaration>()
                        val name = element?.getChildOfType<DartComponentName>()?.name
                        if (name == "getPages") {
                            val type = element.getChildOfType<DartType>()?.text
                            if (!type.isNullOrEmpty() && !type.startsWith("List")) {
                                return@runWriteCommandAction
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
                                    return@runWriteCommandAction
                                }

                                pagesElement = child
                                break
                            }
                        }
                    }


                    if (pagesElement == null) {
                        pagesElement =
                            DartPsiUtils.createClassMember(project, "static List<GetPage> getPages = []")
                                ?: return@runWriteCommandAction
                        pagesElement = classMembers.add(pagesElement)

                        DartPsiUtils.createSemicolonElement(project)?.also {
                            classMembers.add(it)
                        }
                    }

                    val dartListLiteralExpression =
                        findPageListDefinitionElement(project, pagesElement!!) ?: return@runWriteCommandAction
                    val listChildren = dartListLiteralExpression.children
                    if (listChildren.isNotEmpty() && !endOfComma(listChildren[listChildren.size - 1])) {
                        DartPsiUtils.createCommaElement(project)?.also {
                            dartListLiteralExpression.addBefore(it, dartListLiteralExpression.lastChild)
                        }
                    }

                    val pagePath = dialog.getPagePath()
                    val pageName = dialog.getPageName()
                    DartPsiUtils.createListItem(
                        project,
                        "GetPage(name: '$pagePath', page: () => const $pageName())"
                    )?.also {
                        dartListLiteralExpression.addBefore(it, dartListLiteralExpression.lastChild)
                    }

                    val doc = dialog.getDoc()
                    if (doc.isNotEmpty()) {
                        DartPsiUtils.createDocElement(project, "/// $doc")?.also { docElement ->
                            classMembers.add(docElement)
                        }
                    }


                    val params = dialog.getParams()

                    var method = "static go$pageName(&params&) {&arguments1& Get.toNamed('$pagePath'&arguments2&); }"
                    if (params.isEmpty()) {
                        method = method.replace("&params&", "")
                            .replace("&arguments1&", "")
                            .replace("&arguments2&", "")
                    } else {
                        var paramsStr = "{"
                        var arguments = " Map<String, dynamic> params = {"

                        for (i in params.indices) {
                            val it = params[i]
                            val couldNull = if (it.type != "dynamic" && it.couldNull) "?" else ""
                            val required = if (it.couldNull) "" else "required "

                            paramsStr += if ((it.type == "List" || it.type == "Set") && it.generics.isNotEmpty()) {
                                "$required${it.type}<${it.generics}>$couldNull ${it.name}"
                            } else if (it.type == "Map") {
                                "$required${it.type}<String,dynamic>$couldNull ${it.name}"
                            } else {
                                "$required${it.type}$couldNull ${it.name}"
                            }

                            arguments += "'${it.name}': ${it.name}"


                            if (i != params.size - 1) {
                                paramsStr += ","
                                arguments += ","
                            }
                        }

                        paramsStr += "}"
                        arguments += "};"
                        method = method.replace("&params&", paramsStr)
                            .replace("&arguments1&", arguments)
                            .replace("&arguments2&", ", arguments: params")
                    }

                    DartPsiUtils.createCommonElement(project, method)
                        ?.also {
                            val element = classMembers.add(it)
                            EditorHelper.openInEditor(element)
                        }

                    addImport(project, psiFile)
                    DartPsiUtils.reformatFile(project, psiFile)
                }

                progressIndicator.isIndeterminate = false
                progressIndicator.fraction = 1.0
            }
        })
    }

    /**
     * 查找定义页面的列表节点
     */
    private fun findPageListDefinitionElement(project: Project, parent: PsiElement): PsiElement? {
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
            var varInit: PsiElement? = parent.getChildOfType<DartVarInit>()
            if (varInit == null) {
                val element =
                    DartPsiUtils.createClassMember(project, "static List<GetPage> getPages = []")

                if (element == null) {
                    null
                } else {
                    varInit = element.getChildOfType<DartVarInit>()
                    if (varInit == null) {
                        null
                    } else {
                        varInit = parent.add(varInit)
                        varInit.getChildOfType<DartListLiteralExpression>()
                    }
                }
            } else {
                varInit.getChildOfType<DartListLiteralExpression>()
            }
        }
    }

    /**
     * 判断页面列表是否已逗号结尾
     */
    private fun endOfComma(psiElement: PsiElement): Boolean {
        val element = psiElement.nextSibling ?: return false
        if (element.textMatches(",")) {
            return true
        }
        return endOfComma(element)
    }

    /**
     * 添加必要的导入
     */
    private fun addImport(project: Project, psiFile: PsiFile) {
        var lastImportElement: PsiElement? = null
        var existAnyImport = false
        for (child in psiFile.children) {
            if (child is DartImportStatement) {
                existAnyImport = true
                if (child.textMatches("import 'package:get/get.dart';")) {
                    return
                }
            } else if (child !is PsiWhiteSpace && existAnyImport) {
                lastImportElement = child
                break
            }
        }

        DartPsiUtils.createCommonElement(project, "import 'package:get/get.dart';")?.also {
            if (lastImportElement != null) {
                psiFile.addBefore(it, lastImportElement)
            } else {
                psiFile.addBefore(it, psiFile.firstChild)
            }
        }
    }
}
