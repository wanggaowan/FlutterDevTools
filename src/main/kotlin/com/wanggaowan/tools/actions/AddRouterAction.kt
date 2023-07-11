package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.psi.*
import com.wanggaowan.tools.extensions.gotohandler.RouterGoToDeclarationHandler
import com.wanggaowan.tools.ui.AddRouterDialog
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.ex.findModule
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

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
                    // 光标位置的PsiElement
                    var cursorElement = event.getData(CommonDataKeys.PSI_ELEMENT)
                    if (cursorElement == null) {
                        val editor = event.getData(CommonDataKeys.EDITOR)
                        editor?.let {
                            cursorElement =
                                DartPsiUtils.findElementAtOffset(psiFile, it.selectionModel.selectionStart)
                        }
                    }

                    // 查找光标位置所在类
                    var routeMapClass: PsiElement? = cursorElement?.getParentOfType<DartClass>(strict = true)
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

                    var insertAnchorList: PsiElement? = null
                    var insertAnchorMethod: PsiElement? = null

                    val dartListLiteralExpression: PsiElement
                    if (pagesElement == null) {
                        pagesElement =
                            DartPsiUtils.createClassMember(project, "static List<GetPage> getPages = []")
                                ?: return@runWriteCommandAction
                        pagesElement = classMembers.add(pagesElement) ?: return@runWriteCommandAction

                        DartPsiUtils.createSemicolonElement(project)?.also {
                            classMembers.add(it)
                        }

                        dartListLiteralExpression =
                            findPageListDefinitionElement(project, pagesElement) ?: return@runWriteCommandAction
                    } else {
                        dartListLiteralExpression =
                            findPageListDefinitionElement(project, pagesElement) ?: return@runWriteCommandAction

                        // 存在getPages节点的情况下，查找触发当前Action时，最接近Editor中光标处PsiElement
                        // 然后找打对应的列表节点和方法节点，之后输入插入到这些数据之后
                        val (listNode, methodNode) = findInsertNode(cursorElement, dartListLiteralExpression)
                        insertAnchorList = listNode
                        insertAnchorMethod = methodNode
                    }

                    // 插入数据到getPages列表节点
                    val pagePath = dialog.getPagePath()
                    val pageName = dialog.getPageName()
                    var pageElement: PsiElement?
                    if (insertAnchorList == null) {
                        val listChildren = dartListLiteralExpression.children
                        if (listChildren.isNotEmpty() && endOfComma(listChildren[listChildren.size - 1]) == null) {
                            DartPsiUtils.createCommaElement(project)?.also {
                                dartListLiteralExpression.addBefore(it, dartListLiteralExpression.lastChild)
                            }
                        }

                        pageElement = DartPsiUtils.createListItem(
                            project,
                            "GetPage(name: '$pagePath', page: () => const $pageName())"
                        )

                        if (pageElement != null) {
                            pageElement =
                                dartListLiteralExpression.addBefore(pageElement, dartListLiteralExpression.lastChild)
                        }
                    } else {
                        pageElement = DartPsiUtils.createListItem(
                            project,
                            "GetPage(name: '$pagePath', page: () => const $pageName())"
                        )

                        if (pageElement != null) {
                            endOfComma(insertAnchorList)?.also {
                                insertAnchorList = it
                            }

                            pageElement =
                                dartListLiteralExpression.addAfter(pageElement, insertAnchorList)
                            DartPsiUtils.createCommaElement(project)?.also {
                                dartListLiteralExpression.addAfter(it, pageElement)
                            }
                        }
                    }

                    // 插入数据到方法列表节点
                    val doc = dialog.getDoc()
                    if (doc.isNotEmpty()) {
                        DartPsiUtils.createDocElement(project, "/// $doc")?.also { docElement ->
                            if (insertAnchorMethod == null) {
                                classMembers.add(docElement)
                            } else {
                                insertAnchorMethod = classMembers.addAfter(docElement, insertAnchorMethod)
                            }
                        }
                    }

                    val params = dialog.getParams()
                    val returnType = dialog.getReturnType()
                    val returnGenerics = dialog.getReturnGenerics()

                    val returnStr = if (returnType == "无返回参数") {
                        ""
                    } else if (returnType == "自定义") {
                        if (returnGenerics.isEmpty()) {
                            ""
                        } else {
                            "Future<$returnGenerics?>? "
                        }
                    } else if ((returnType == "List" || returnType == "Set") && returnGenerics.isNotEmpty()) {
                        "Future<$returnType<$returnGenerics>?>? "
                    } else if (returnType == "Map") {
                        "Future<Map<String,dynamic>?>? "
                    } else {
                        "Future<$returnType?>? "
                    }

                    var method = if (returnStr.isEmpty()) {
                        "static ${returnStr}go$pageName(&params&) {&arguments1& Get.toNamed('$pagePath'&arguments2&); }"
                    } else {
                        "static ${returnStr}go$pageName(&params&) async {&arguments1& var result = await Get.toNamed('$pagePath'&arguments2&); return result; }"
                    }

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
                            } else if (it.type == "自定义") {
                                "$required${it.generics}$couldNull ${it.name}"
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

                    DartPsiUtils.createClassMember(project, method)?.also {
                        if (insertAnchorMethod == null) {
                            classMembers.add(it)
                        } else {
                            insertAnchorMethod = classMembers.addAfter(it, insertAnchorMethod)
                        }
                    }

                    addImport(project, psiFile)

                    DartPsiUtils.reformatFile(project, psiFile)

                    // pageElement?.also {
                    //     EditorHelper.openInEditor(it)
                    // }
                }

                progressIndicator.isIndeterminate = false
                progressIndicator.fraction = 1.0
            }
        })
    }

    /**
     * 查找指定节点之前，类型为DartElement的节点
     */
    private fun findPreListNode(element: PsiElement): PsiElement? {
        val pre = element.prevSibling ?: return null

        if (pre is DartElement) {
            return pre
        }
        return findPreListNode(pre)
    }

    /**
     * 查找指定节点之前，类型为DartMethodDeclaration的节点
     */
    private fun findPreMethodNode(element: PsiElement): PsiElement? {
        val pre = element.prevSibling ?: return null

        if (pre is DartMethodDeclaration) {
            return pre
        }
        return findPreMethodNode(pre)
    }

    /**
     * 查找插入节点,根据执行当前动作时鼠标位置PsiElement插入
     */
    private fun findInsertNode(
        cursorElement: PsiElement?,
        dartListLiteralExpression: PsiElement
    ): Pair<PsiElement?, PsiElement?> {
        var insertAnchorList: PsiElement? = null
        var insertAnchorMethod: PsiElement? = null
        // 元素必须在RouteMap类中DartClassMembers里
        var cursorElement2 = cursorElement
        val dartClassMembers = cursorElement2?.getParentOfType<DartClassMembers>(strict = true)
        if (dartClassMembers == null) {
            cursorElement2 = null
        } else {
            val name =
                dartClassMembers.getParentOfType<DartClass>(strict = true)?.getChildOfType<DartComponentName>()?.name
            if (name != "RouteMap") {
                cursorElement2 = null
            }
        }

        if (cursorElement2 != null) {
            var parent: PsiElement? =
                cursorElement2.getParentOfType<DartListLiteralExpression>(strict = true)
            if (parent != null) {
                // 说明光标在getPages列表里
                insertAnchorList =
                    cursorElement2.getParentOfType<DartElement>(strict = true) ?: findPreListNode(cursorElement2)
                        ?: parent.firstChild
            } else {
                parent = cursorElement2.getParentOfType<DartMethodDeclaration>(strict = true)
                insertAnchorMethod = parent ?: findPreMethodNode(cursorElement2)
            }
        }

        if (insertAnchorList != null || insertAnchorMethod != null) {
            if (insertAnchorMethod != null) {
                val argListElements =
                    PsiTreeUtil.findChildrenOfAnyType(insertAnchorMethod, DartArgumentList::class.java)
                if (argListElements.isNotEmpty()) {
                    for (element in argListElements) {
                        val leafPsiElement =
                            element.getChildOfType<DartStringLiteralExpression>()?.firstChild?.nextSibling
                        if (leafPsiElement != null) {
                            element.findModule()?.also { module ->
                                val elements = RouterGoToDeclarationHandler.getGotoDeclarationTargets(
                                    module,
                                    leafPsiElement
                                )
                                if (!elements.isNullOrEmpty()) {
                                    for (element2 in elements) {
                                        if (element2 is DartElement) {
                                            insertAnchorList = element2
                                            break
                                        }
                                    }
                                }
                            }
                            break
                        }
                    }
                }
            } else {
                val argListElements =
                    PsiTreeUtil.findChildrenOfAnyType(insertAnchorList, DartArgumentList::class.java)
                if (argListElements.isNotEmpty()) {
                    for (argListElement in argListElements) {
                        for (namedArgument in argListElement.children) {
                            if (namedArgument.text.startsWith("name")) {
                                val leafPsiElement =
                                    namedArgument.getChildOfType<DartStringLiteralExpression>()?.firstChild?.nextSibling
                                if (leafPsiElement != null) {
                                    namedArgument.findModule()?.also { module ->
                                        val elements =
                                            RouterGoToDeclarationHandler.getGotoDeclarationTargets(
                                                module,
                                                leafPsiElement
                                            )
                                        if (!elements.isNullOrEmpty()) {
                                            for (element2 in elements) {
                                                if (element2 is DartMethodDeclaration) {
                                                    insertAnchorMethod = element2
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                                break
                            }
                        }

                        if (insertAnchorMethod != null) {
                            break
                        }
                    }
                }

                if (insertAnchorList == dartListLiteralExpression.firstChild) {
                    val firstMethod = dartClassMembers?.getChildOfType<DartMethodDeclaration>()
                    if (firstMethod != null) {
                        insertAnchorMethod = preNotOfComment(firstMethod)
                    }
                }
            }
        } else if (cursorElement2 != null) {
            // 此时表明应插入到最顶部
            insertAnchorMethod = cursorElement2.let {
                if (cursorElement2.nextSibling?.textMatches(";") == true) {
                    cursorElement2.nextSibling
                } else {
                    cursorElement2
                }
            }

            insertAnchorList = dartListLiteralExpression.firstChild
        }

        return Pair(insertAnchorList, insertAnchorMethod)
    }

    /**
     * 查找指定元素之前第一个不是注释的元素
     */
    private tailrec fun preNotOfComment(psiElement: PsiElement): PsiElement {
        val element = psiElement.prevSibling ?: return psiElement
        if (element !is PsiWhiteSpace && element !is PsiComment) {
            return element
        }
        return preNotOfComment(element)
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
    private tailrec fun endOfComma(psiElement: PsiElement): PsiElement? {
        val element = psiElement.nextSibling ?: return null
        if (element !is PsiWhiteSpace && element !is LeafPsiElement) {
            return null
        }

        if (element.textMatches(",")) {
            return element
        }
        return endOfComma(element)
    }

    /**
     * 判断页面列表是否已分号结尾
     */
    private tailrec fun endOfSemicolon(psiElement: PsiElement): PsiElement? {
        val element = psiElement.nextSibling ?: return null
        if (element !is PsiWhiteSpace && element !is LeafPsiElement) {
            return null
        }

        if (element.textMatches(";")) {
            return element
        }
        return endOfSemicolon(element)
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
