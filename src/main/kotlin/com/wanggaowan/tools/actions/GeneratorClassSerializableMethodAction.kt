package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.psi.*
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.dart.DartPsiUtils.findElementAtOffset
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

/**
 * 根据选择的dart class，生成class对应的序列号方法，序列号配置项
 *
 * @author Created by wanggaowan on 2023/2/6 15:44
 */
class GeneratorClassSerializableMethodAction : AnAction() {

    private var selectedClassPsiElement: DartClass? = null

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        if (!project.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor == null) {
            e.presentation.isVisible = false
            return
        }

        val virtualFile = e.getData(CommonDataKeys.PSI_FILE)
        if (virtualFile == null || virtualFile.isDirectory) {
            e.presentation.isVisible = false
            return
        }

        if (!virtualFile.name.endsWith(".dart")) {
            e.presentation.isVisible = false
            return
        }

        var psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (psiElement == null) {
            editor.let {
                psiElement = findElementAtOffset(virtualFile, it.selectionModel.selectionStart)
            }
        }

        if (psiElement != null && psiElement !is DartClass) {
            psiElement = PsiTreeUtil.getParentOfType(psiElement, DartClass::class.java)
        }

        if (psiElement !is DartClass) {
            e.presentation.isVisible = false
            return
        }

        selectedClassPsiElement = psiElement as DartClass
        e.presentation.isVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = getEventProject(event) ?: return
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return
        if (selectedClassPsiElement == null) {
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            addJsonSerializableImport(project, psiFile)
            addPartImport(project, psiFile)
            selectedClassInit(project, selectedClassPsiElement!!)
        }
    }

    /**
     * 添加序列化需要的Import导入
     */
    private fun addJsonSerializableImport(project: Project, psiFile: PsiFile) {
        var lastImportElement: PsiElement? = null
        var existAnyImport = false
        for (child in psiFile.children) {
            if (child is DartImportStatement) {
                existAnyImport = true
                if (child.textMatches("import 'package:json_annotation/json_annotation.dart';")) {
                    return
                }
            } else if (child !is PsiWhiteSpace && existAnyImport) {
                lastImportElement = child
                break
            }
        }

        DartPsiUtils.createCommonElement(project, "import 'package:json_annotation/json_annotation.dart';")?.also {
            if (lastImportElement != null) {
                psiFile.addBefore(it, lastImportElement)
            } else {
                psiFile.addBefore(it, psiFile.firstChild)
            }
        }
    }

    /**
     * 添加序列化需要的part导入
     */
    private fun addPartImport(project: Project, psiFile: PsiFile) {
        val fileName = psiFile.name.replace(".dart", "")
        val part = "part '$fileName.g.dart';"

        var lastImportElement: PsiElement? = null
        var lastPartElement: PsiElement? = null
        var existAnyPart = false
        for (child in psiFile.children) {
            if (child is DartPartStatement) {
                existAnyPart = true
                if (child.textMatches(part)) {
                    return
                }
            } else if (child is DartImportStatement) {
                lastImportElement = child
            } else if (child !is PsiWhiteSpace && existAnyPart) {
                lastPartElement = child
                break
            }
        }

        DartPsiUtils.createCommonElement(project, part)?.also {
            if (lastPartElement != null) {
                psiFile.addAfter(it, lastPartElement)
            } else if (lastImportElement != null) {
                psiFile.addAfter(it, lastImportElement)
            } else {
                psiFile.addBefore(it, psiFile.firstChild)
            }
        }
    }

    /**
     * 初始化用户选中的类
     */
    private fun selectedClassInit(project: Project, selectedClazzElement: DartClass) {
        var className = "Dummy"
        var classMembers: PsiElement? = null
        selectedClazzElement.also {
            val bodyElement = PsiTreeUtil.getChildOfType(it, DartClassBody::class.java)
            bodyElement?.also { body ->
                classMembers = body.classMembers
                if (classMembers != null) {
                    val element = PsiTreeUtil.getChildOfType(it, DartComponentName::class.java)
                    className = element?.text ?: "Dummy"
                }
            }
        }

        if (classMembers == null) {
            return
        }

        var exist = false
        for (child in selectedClazzElement.children) {
            if (child is DartMetadata && child.text.contains("@JsonSerializable(")) {
                exist = true
                break
            }
        }

        if (!exist) {
            DartPsiUtils.createCommonElement(project, "@JsonSerializable()")?.also {
                selectedClazzElement.addBefore(it, selectedClazzElement.firstChild)
            }
        }

        // 如果选中的类存在构造函数则不创建
        var existConstructor = false
        // 如果选中的类存在工厂构造函数则不创建
        var existFactory = false
        // 如果选中的类存在序列化方法则则创建
        var existToJson = false
        for (child in classMembers!!.children) {
            if (child is DartFactoryConstructorDeclaration
                && child.textMatches("${className}.fromJson(Map<String, dynamic> json)")
            ) {
                existFactory = true
            }

            if (child is DartMethodDeclaration) {
                val element = child.getChildOfType<DartComponentName>()
                if (element != null) {
                    if (element.textMatches(className)) {
                        existConstructor = true
                    } else if (element.textMatches("toJson")) {
                        existToJson = true
                    }
                }
            }

            if (existConstructor && existFactory && existToJson) {
                break
            }
        }

        createClassConstructorAndSerializableMethod(
            project,
            classMembers!!,
            className,
            !existConstructor,
            !existFactory,
            !existToJson
        )
    }

    /**
     * 创建类的构造函数及系列化方法
     */
    private fun createClassConstructorAndSerializableMethod(
        project: Project,
        classMembers: PsiElement,
        className: String,
        createConstructor: Boolean,
        createFactory: Boolean,
        createToJson: Boolean,
    ) {

        if (createConstructor) {
            val constructor = StringBuilder("$className({")
            var index = 0
            val privateFiled = mutableListOf<String>()
            classMembers.children.forEach {
                if (it is DartVarDeclarationList) {
                    var type:String? = null
                    var fieldName:String? = null
                    it.children.forEach {child->
                        if (child is DartVarAccessDeclaration) {
                            child.children.forEach {child2->
                                if (child2 is DartType) {
                                    type = child2.text
                                } else if (child2 is DartComponentName) {
                                    fieldName = child2.text
                                }
                            }
                        }
                    }

                    if (type != null && fieldName != null) {
                        val isPrivate = fieldName!!.startsWith("_")
                        if (isPrivate) {
                            privateFiled.add(fieldName!!)
                        }

                        if (index > 0) {
                            constructor.append(", ")
                        }

                        if (!type!!.endsWith("?")) {
                            constructor.append("required ")
                        }

                        if (isPrivate) {
                            constructor.append("$type ${fieldName!!.substring(1)}")
                        } else {
                            constructor.append("this.$fieldName")
                        }
                        index++
                    }
                }
            }

            if (privateFiled.size == 0) {
                constructor.append("});")
            } else {
                constructor.append("}) {\n")
                privateFiled.forEach {
                    constructor.append(it)
                        .append(" = ")
                        .append(it.substring(1))
                        .append(";")
                }
                constructor.append("\n}")
            }

            DartPsiUtils.createCommonElement(project, constructor.toString())?.also {
                classMembers.add(it)
            }
        }

        if (createFactory) {
            val fromJson =
                "factory ${className}.fromJson(Map<String, dynamic> json) => _\$${className}FromJson(json);"
            DartPsiUtils.createClassMember(project, fromJson)?.also {
                classMembers.add(it)
            }
        }

        if (createToJson) {
            val toJson = "Map<String, dynamic> toJson() => _\$${className}ToJson(this);"
            DartPsiUtils.createCommonElement(project, toJson)?.also {
                classMembers.add(it)
            }
        }
    }
}
