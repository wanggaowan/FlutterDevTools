package com.wanggaowan.tools.actions

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.psi.*
import com.wanggaowan.tools.ui.JsonToDartDialog
import com.wanggaowan.tools.utils.StringUtils
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.flutter.FlutterCommandLine
import com.wanggaowan.tools.utils.flutter.FlutterCommandUtils
import com.wanggaowan.tools.utils.flutter.YamlUtils
import io.flutter.actions.FlutterSdkAction.showMissingSdkDialog
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType


/**
 * JSON文件转Dart
 *
 * @author Created by wanggaowan on 2023/2/3 16:26
 */
class JsonToDartAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = getEventProject(event) ?: return
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return
        var psiElement = event.getData(CommonDataKeys.PSI_ELEMENT)
        if (psiElement == null) {
            val editor = event.getData(CommonDataKeys.EDITOR)
            editor?.let {
                psiElement = DartPsiUtils.findElementAtOffset(psiFile, it.selectionModel.selectionStart)
            }
        }

        if (psiElement != null && psiElement !is DartClass) {
            psiElement = PsiTreeUtil.getParentOfType(psiElement, DartClass::class.java)
        }

        var className: String? = null
        var rootElement: PsiElement? = null
        val clazzElement = psiElement
        psiElement?.also {
            val bodyElement = PsiTreeUtil.getChildOfType(it, DartClassBody::class.java)
            bodyElement?.also { body ->
                rootElement = body.classMembers
                if (rootElement != null) {
                    val element = PsiTreeUtil.getChildOfType(it, DartComponentName::class.java)
                    className = element?.text
                }
            }
        }

        val dialog = JsonToDartDialog(project, className)
        dialog.show()
        if (dialog.exitCode != DialogWrapper.OK_EXIT_CODE) {
            return
        }

        if (dialog.isGeneratorGFile()) {
            val sdk = FlutterSdk.getFlutterSdk(project)
            if (sdk == null) {
                showMissingSdkDialog(project)
            } else {
                createDart(project, dialog, sdk, psiFile, clazzElement, className ?: "", rootElement)
            }
        } else {
            createDart(project, dialog, null, psiFile, clazzElement, className ?: "", rootElement)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        if (!XUtils.isFlutterProject(project)) {
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

    /**
     * 根据JSON场景Dart实体
     */
    private fun createDart(
        project: Project,
        dialog: JsonToDartDialog,
        flutterSdk: FlutterSdk?,
        psiFile: PsiFile,
        selectedClazzElement: PsiElement?,
        selectedClazzName: String,
        rootElement: PsiElement?
    ) {
        val className = dialog.getClassName()
        val jsonObject = dialog.getJsonValue()
        val suffix = dialog.getSuffix()
        val generatorDoc = dialog.isGeneratorDoc()
        val generatorJsonSerializable = dialog.isGeneratorJsonSerializable()
        val nullSafe = dialog.isNullSafe()
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "GsonFormat") {
            override fun run(progressIndicator: ProgressIndicator) {
                progressIndicator.isIndeterminate = true
                WriteCommandAction.runWriteCommandAction(project) {
                    if (rootElement == null) {
                        addClass(
                            project, psiFile, jsonObject, className, suffix, null,
                            generatorDoc, generatorJsonSerializable, nullSafe
                        )
                    } else {
                        // 用户选中了对应的Dart class
                        createFieldOnJsonObject(
                            project, psiFile, jsonObject, rootElement, dialog.getSuffix(),
                            generatorDoc, generatorJsonSerializable, nullSafe
                        )

                        selectedClassInit(
                            project,
                            selectedClazzElement,
                            rootElement,
                            selectedClazzName,
                            jsonObject,
                            generatorJsonSerializable,
                            nullSafe
                        )
                    }

                    if (generatorJsonSerializable) {
                        addJsonSerializableImport(project, psiFile)
                        addPartImport(project, psiFile)
                    }

                    reformatFile(project, psiFile)

                    flutterSdk?.also {
                        FileDocumentManager.getInstance().saveAllDocuments()
                        executeCommand(project, psiFile, it)
                    }
                }

                progressIndicator.isIndeterminate = false
                progressIndicator.fraction = 1.0
            }
        })
    }

    /**
     * 如果生产序列化相关数据，需要执行添加依赖及生成序列化命令
     */
    private fun executeCommand(project: Project, psiFile: PsiFile, sdk: FlutterSdk) {
        val pubRoot = PubRoot.forPsiFile(psiFile) ?: return
        val pubspec = pubRoot.pubspec.toPsiFile(project) ?: return
        ApplicationManager.getApplication().runReadAction {
            val haveJsonAnnotation = YamlUtils.haveDependencies(pubspec, YamlUtils.DEPENDENCY_TYPE_ALL, "json_annotation")
            val haveJsonSerializable = YamlUtils.haveDependencies(pubspec, YamlUtils.DEPENDENCY_TYPE_ALL, "json_serializable")
            val haveBuildRunner = YamlUtils.haveDependencies(pubspec, YamlUtils.DEPENDENCY_TYPE_ALL, "build_runner")
            addJsonAnnotation(project, pubRoot, sdk, haveJsonAnnotation) {
                addJsonSerializable(project, pubRoot, sdk, haveJsonSerializable) {
                    addBuildRunner(project, pubRoot, sdk, haveBuildRunner) {
                        FlutterCommandUtils.startGeneratorJsonSerializable(project, pubRoot, sdk)
                    }
                }
            }
        }
    }

    /**
     * 执行添加json_annotation依赖命令
     */
    private fun addJsonAnnotation(
        project: Project,
        pubRoot: PubRoot,
        flutterSdk: FlutterSdk,
        haveJsonAnnotation: Boolean,
        onDone: Runnable? = null
    ) {
        if (!haveJsonAnnotation) {
            FlutterCommandUtils.startAddDependencies(
                project, pubRoot, flutterSdk,
                FlutterCommandLine.Type.ADD_JSON_ANNOTATION, {
                    if (it == 0) {
                        onDone?.run()
                    }
                }
            )
        } else {
            onDone?.run()
        }
    }

    /**
     * 执行添加json_serializable依赖命令
     */
    private fun addJsonSerializable(
        project: Project,
        pubRoot: PubRoot,
        flutterSdk: FlutterSdk,
        haveJsonSerializable: Boolean,
        onDone: Runnable? = null
    ) {
        if (!haveJsonSerializable) {
            FlutterCommandUtils.startAddDependencies(
                project, pubRoot, flutterSdk,
                FlutterCommandLine.Type.ADD_JSON_SERIALIZABLE_DEV, {
                    if (it == 0) {
                        onDone?.run()
                    }
                }
            )
        } else {
            onDone?.run()
        }
    }

    /**
     * 执行添加build_runner依赖依赖命令，生成序列化文件
     */
    private fun addBuildRunner(
        project: Project,
        pubRoot: PubRoot,
        flutterSdk: FlutterSdk,
        haveBuildRunner: Boolean,
        onDone: Runnable? = null
    ) {
        if (!haveBuildRunner) {
            FlutterCommandUtils.startAddDependencies(
                project, pubRoot, flutterSdk,
                FlutterCommandLine.Type.ADD_BUILD_RUNNER_DEV, {
                    if (it == 0) {
                        onDone?.run()
                    }
                }
            )
        } else {
            onDone?.run()
        }
    }

    /**
     * 初始化用户选中的类
     */
    private fun selectedClassInit(
        project: Project,
        selectedClazzElement: PsiElement?,
        classMembers: PsiElement,
        selectedClazzName: String,
        jsonObject: JsonObject,
        isGeneratorJsonSerializable: Boolean,
        nullSafe: Boolean,
    ) {
        if (isGeneratorJsonSerializable && selectedClazzElement != null) {
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
        }

        // 如果选中的类存在构造函数则不创建
        var existConstructor = false
        // 如果选中的类存在工厂构造函数则不创建
        var existFactory = false
        // 如果选中的类存在序列化方法则则创建
        var existToJson = false
        for (child in classMembers.children) {
            if (child is DartFactoryConstructorDeclaration
                && child.textMatches("${selectedClazzName}.fromJson(Map<String, dynamic> json)")
            ) {
                existFactory = true
            }

            if (child is DartMethodDeclaration) {
                val element = child.getChildOfType<DartComponentName>()
                if (element != null) {
                    if (element.textMatches(selectedClazzName)) {
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
            project, jsonObject, classMembers, selectedClazzName, nullSafe,
            !existConstructor, !existFactory, !existToJson
        )
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
     * 执行格式化
     *
     * @param project     项目对象
     * @param psiFile 需要格式化文件
     */
    private fun reformatFile(project: Project, psiFile: PsiFile) {
        CodeStyleManagerImpl(project).reformatText(psiFile, mutableListOf(TextRange(0, psiFile.textLength)))
    }

    /**
     * 根据json创建类中的字段
     */
    private fun createFieldOnJsonObject(
        project: Project,
        psiFile: PsiFile,
        jsonObject: JsonObject,
        parentElement: PsiElement,
        suffix: String,
        createDoc: Boolean,
        generatorJsonSerializable: Boolean,
        nullSafe: Boolean,
    ) {
        jsonObject.keySet().forEach {
            val obj = jsonObject.get(it)
            if (obj == null || obj.isJsonNull) {
                addField(project, it, null, parentElement, createDoc, nullSafe)
            } else if (obj.isJsonPrimitive) {
                addField(project, it, obj as JsonPrimitive, parentElement, createDoc, nullSafe)
            } else if (obj.isJsonObject) {
                val (key, doc) = getFieldName(it)
                val className = StringUtils.toHumpFormat(key)
                addFieldForObjType(project, key, className + suffix, false, parentElement, doc, createDoc, nullSafe)
                addClass(
                    project,
                    psiFile,
                    obj as JsonObject,
                    className,
                    suffix,
                    doc,
                    createDoc,
                    generatorJsonSerializable,
                    nullSafe
                )
            } else if (obj.isJsonArray) {
                val (key, doc) = getFieldName(it)
                val className = StringUtils.toHumpFormat(key)
                obj.asJsonArray.let { jsonArray ->
                    val (type, json) = getJsonArrayType(jsonArray, className + suffix)
                    addFieldForObjType(project, key, type, true, parentElement, doc, createDoc, nullSafe)
                    if (json != null) {
                        addClass(
                            project,
                            psiFile,
                            json,
                            className,
                            suffix,
                            doc,
                            createDoc,
                            generatorJsonSerializable,
                            nullSafe
                        )
                    }
                }
            }
        }
    }

    private fun getJsonArrayType(jsonArray: JsonArray, className: String): Pair<String?, JsonObject?> {
        if (jsonArray.size() == 0) {
            return Pair(null, null)
        }

        val child = jsonArray.get(0)
        if (child.isJsonObject) {
            return Pair(className, child as JsonObject)
        } else if (child.isJsonPrimitive) {
            val typeName = child.asJsonPrimitive.let { primitive ->
                if (primitive.isNumber) {
                    if (primitive.asString.contains(".")) "double" else "int"
                } else if (primitive.isBoolean) {
                    "bool"
                } else if (primitive.isString) {
                    "String"
                } else {
                    "Object"
                }
            }
            return Pair(typeName, null)
        } else if (child.isJsonArray) {
            val (type, json) = getJsonArrayType(child as JsonArray, className)
            return Pair("List<$type>", json)
        } else {
            return Pair(null, null)
        }
    }

    private fun getFieldName(jsonKey: String): Pair<String, String> {
        return if (jsonKey.contains("(") && jsonKey.contains(")")) {
            // 兼容周卓接口文档JSON, "dataList (工序列表)":[]
            val index = jsonKey.indexOf("(")
            Pair(
                jsonKey.substring(index + 1, jsonKey.length - 1),
                jsonKey.substring(0, index).replace(" ", "")
            )
        } else {
            Pair(jsonKey, "")
        }
    }

    /**
     * 创建Dart类
     */
    private fun addClass(
        project: Project,
        psiFile: PsiFile,
        jsonObject: JsonObject,
        className: String,
        suffix: String,
        doc: String?,
        createDoc: Boolean,
        generatorJsonSerializable: Boolean,
        nullSafe: Boolean,
    ) {
        DartPsiUtils.createClassElement(project, className + suffix)?.also { clazz ->
            if (createDoc && !doc.isNullOrBlank()) {
                DartPsiUtils.createDocElement(project, "/// $doc")?.also { docElement ->
                    psiFile.add(docElement)
                }
            }

            val element = psiFile.add(clazz)
            if (generatorJsonSerializable) {
                DartPsiUtils.createCommonElement(project, "@JsonSerializable()")?.also {
                    element.addBefore(it, element.firstChild)
                }
            }

            val bodyElement = PsiTreeUtil.getChildOfType(element, DartClassBody::class.java)
            bodyElement?.also { body ->
                val rootElement = body.classMembers
                if (rootElement != null) {
                    createFieldOnJsonObject(
                        project, psiFile, jsonObject, rootElement, suffix,
                        createDoc, generatorJsonSerializable, nullSafe
                    )

                    val name = className + suffix
                    createClassConstructorAndSerializableMethod(
                        project, jsonObject, rootElement, name, nullSafe, true,
                        generatorJsonSerializable, generatorJsonSerializable
                    )
                }
            }
        }
    }

    /**
     * 创建类的构造函数及系列化方法
     */
    private fun createClassConstructorAndSerializableMethod(
        project: Project,
        jsonObject: JsonObject,
        classMembers: PsiElement,
        className: String,
        nullSafe: Boolean,
        createConstructor: Boolean,
        createFactory: Boolean,
        createToJson: Boolean,
    ) {

        if (createConstructor) {
            val constructor = StringBuilder("$className({")
            var index = 0
            jsonObject.keySet().forEach {
                val fieldName = if (it.contains("(") && it.contains(")")) {
                    // 兼容周卓接口文档JSON, "dataList (工序列表)":[]
                    val index2 = it.indexOf("(")
                    it.substring(0, index2).replace(" ", "")
                } else {
                    it
                }

                if (index > 0) {
                    constructor.append(", ")
                }

                if (nullSafe) {
                    constructor.append("required ")
                }

                constructor.append("this.$fieldName")
                index++
            }
            constructor.append("});")
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

    /**
     * 创建Dart类中普通字段
     */
    private fun addField(
        project: Project,
        key: String,
        jsonElement: JsonPrimitive?,
        parentElement: PsiElement,
        createDoc: Boolean,
        nullSafe: Boolean,
    ) {

        val methodElement = PsiTreeUtil.findChildOfAnyType(
            parentElement, DartFactoryConstructorDeclaration::class.java,
            DartMethodDeclaration::class.java
        )

        if (createDoc) {
            var doc = jsonElement?.toString()?.replace("\"", "")
            if (!doc.isNullOrBlank()) {
                doc = "/// $doc"
                DartPsiUtils.createDocElement(project, doc)?.also { docElement ->
                    if (methodElement != null) {
                        parentElement.addBefore(docElement, methodElement)
                    } else {
                        parentElement.add(docElement)
                    }
                }
            }
        }

        val content = if (jsonElement == null) {
            if (nullSafe) {
                "Object $key"
            } else {
                "Object? $key"
            }
        } else if (jsonElement.isBoolean) {
            if (nullSafe) {
                "bool $key"
            } else {
                "bool? $key"
            }
        } else if (jsonElement.isNumber) {
            val type = if (jsonElement.asString.contains(".")) "double" else "int"
            if (nullSafe) {
                "$type $key"
            } else {
                "$type? $key"
            }
        } else {
            if (nullSafe) {
                "String $key"
            } else {
                "String? $key"
            }
        }

        DartPsiUtils.createCommonElement(project, content)?.also {
            if (methodElement != null) {
                parentElement.addBefore(it, methodElement)
            } else {
                parentElement.add(it)
            }
        }

        DartPsiUtils.createSemicolonElement(project)?.also {
            if (methodElement != null) {
                parentElement.addBefore(it, methodElement)
            } else {
                parentElement.add(it)
            }
        }
    }

    /**
     * 创建Dart类中实体字段，字段对应的类型是另一个实体
     */
    private fun addFieldForObjType(
        project: Project,
        key: String,
        typeName: String?,
        isArray: Boolean,
        parentElement: PsiElement,
        doc: String?,
        createDoc: Boolean,
        nullSafe: Boolean,
    ) {
        val methodElement = PsiTreeUtil.findChildOfAnyType(
            parentElement, DartFactoryConstructorDeclaration::class.java,
            DartMethodDeclaration::class.java
        )

        if (createDoc && !doc.isNullOrBlank()) {
            DartPsiUtils.createDocElement(project, "/// $doc")?.also { docElement ->
                if (methodElement != null) {
                    parentElement.addBefore(docElement, methodElement)
                } else {
                    parentElement.add(docElement)
                }
            }
        }

        val content = if (typeName == null) {
            if (nullSafe) {
                "Object $key"
            } else {
                "Object? $key"
            }
        } else if (isArray) {
            if (nullSafe) {
                "List<$typeName> $key"
            } else {
                "List<$typeName>? $key"
            }
        } else {
            if (nullSafe) {
                "$typeName $key"
            } else {
                "$typeName? $key"
            }
        }
        DartPsiUtils.createCommonElement(project, content)?.also {
            if (methodElement != null) {
                parentElement.addBefore(it, methodElement)
            } else {
                parentElement.add(it)
            }
        }

        DartPsiUtils.createSemicolonElement(project)?.also {
            if (methodElement != null) {
                parentElement.addBefore(it, methodElement)
            } else {
                parentElement.add(it)
            }
        }
    }
}
