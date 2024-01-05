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
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.psi.*
import com.wanggaowan.tools.ui.JsonToDartDialog
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.PropertiesSerializeUtils
import com.wanggaowan.tools.utils.StringUtils
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.flutter.FlutterCommandUtils
import io.flutter.actions.FlutterSdkAction.showMissingSdkDialog
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType


/**
 * JSON文件转Dart
 *
 * @author Created by wanggaowan on 2023/2/3 16:26
 */
class JsonToDartAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        if (!e.isFlutterProject) {
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
        val jsonValue = dialog.getJsonValue()
        val config = Config(
            dialog.getSuffix(),
            dialog.isGeneratorDoc(),
            dialog.isGeneratorJsonSerializable(),
            dialog.isNullSafe(),
            dialog.isCreateFromList(),
            dialog.isSetConverters(),
            dialog.getConvertersValue()
        )

        PropertiesSerializeUtils.putString(project, JsonToDartDialog.CONVERTERS_VALUE, config.convertersValue)
        PropertiesSerializeUtils.putBoolean(project, JsonToDartDialog.SET_CONVERTERS, config.setConverters)
        ProgressUtils.runBackground(project, "GsonFormat") { progressIndicator ->
            progressIndicator.isIndeterminate = true
            WriteCommandAction.runWriteCommandAction(project) {
                if (rootElement == null) {
                    addClass(project, psiFile, null, className, jsonValue, config)
                } else {
                    // 用户选中了对应的Dart class
                    createFieldOnJsonObject(project, psiFile, rootElement, jsonValue, config)
                    selectedClassInit(
                        project, selectedClazzElement, rootElement, selectedClazzName,
                        jsonValue, config
                    )
                }

                if (config.generatorJsonSerializable) {
                    addJsonSerializableImport(project, psiFile)
                    addPartImport(project, psiFile)
                }

                DartPsiUtils.reformatFile(project, psiFile)

                flutterSdk?.also {
                    FileDocumentManager.getInstance().saveAllDocuments()
                    executeCommand(project, psiFile, it)
                }
            }

            progressIndicator.isIndeterminate = false
            progressIndicator.fraction = 1.0
        }
    }

    /**
     * 如果生产序列化相关数据，需要执行添加依赖及生成序列化命令
     */
    private fun executeCommand(project: Project, psiFile: PsiFile, sdk: FlutterSdk) {
        val pubRoot = PubRoot.forPsiFile(psiFile) ?: return
        val module = pubRoot.getModule(project) ?: return
        ApplicationManager.getApplication().runReadAction {
            GeneratorGFileAction.addGeneratorGFileDependencies(module, sdk, pubRoot) {
                // 只生成当前文件的.g.dart
                val virtualFile = psiFile.virtualFile
                FlutterCommandUtils.startGeneratorJsonSerializable(
                    module, pubRoot, sdk,
                    includeFiles = listOf(virtualFile),
                    onDone = {
                        virtualFile.parent?.refresh(true, false)
                    })
            }
        }
    }

    /**
     * 初始化用户选中的类
     */
    private fun selectedClassInit(
        project: Project, selectedClazzElement: PsiElement?, classMembers: PsiElement,
        selectedClazzName: String, jsonObject: JsonObject, config: Config
    ) {
        if (config.generatorJsonSerializable && selectedClazzElement != null) {
            var exist = false
            for (child in selectedClazzElement.children) {
                if (child is DartMetadata && child.text.contains("@JsonSerializable(")) {
                    exist = true
                    break
                }
            }

            if (!exist) {
                val text = if (config.setConverters) {
                    "@JsonSerializable(converters: ${config.convertersValue})"
                } else {
                    "@JsonSerializable()"
                }

                DartPsiUtils.createCommonElement(project, text)?.also {
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
        var existFromList = false
        for (child in classMembers.children) {
            if (child is DartFactoryConstructorDeclaration) {
                val children = child.getChildrenOfType<DartComponentName>()
                if (children.size >= 2 && children[0].textMatches(selectedClazzName) && children[1].textMatches("fromJson")) {
                    existFactory = true
                }
            }

            if (child is DartMethodDeclaration) {
                val element = child.getChildOfType<DartComponentName>()
                if (element != null) {
                    if (element.textMatches(selectedClazzName)) {
                        existConstructor = true
                    } else if (element.textMatches("toJson")) {
                        existToJson = true
                    } else if (config.createFromList && element.textMatches("fromJsonList")) {
                        existFromList = true
                    }
                }
            }

            if (existConstructor && existFactory && existToJson && existFromList) {
                break
            }
        }

        createClassConstructorAndSerializableMethod(
            project,
            jsonObject,
            classMembers,
            selectedClazzName,
            !existConstructor,
            !existFactory && (config.generatorJsonSerializable || config.createFromList),
            !existToJson && config.generatorJsonSerializable,
            !existFromList && config.createFromList,
            config.nullSafe
        )
    }

    /**
     * 添加序列化需要的Import导入
     */
    private fun addJsonSerializableImport(project: Project, psiFile: PsiFile) {
        DartPsiUtils.addImport(project, psiFile, "import 'package:json_annotation/json_annotation.dart';")
    }

    /**
     * 添加序列化需要的part导入
     */
    private fun addPartImport(project: Project, psiFile: PsiFile) {
        val fileName = psiFile.name.replace(".dart", "")
        val part = "part '$fileName.g.dart';"
        DartPsiUtils.addPartImport(project, psiFile, part)
    }

    /**
     * 根据json创建类中的字段
     */
    private fun createFieldOnJsonObject(
        project: Project, psiFile: PsiFile, parentElement: PsiElement,
        jsonObject: JsonObject, config: Config
    ) {
        jsonObject.keySet().forEach {
            val obj = jsonObject.get(it)
            if (obj == null || obj.isJsonNull) {
                addField(project, it, null, parentElement, config)
            } else if (obj.isJsonPrimitive) {
                addField(project, it, obj as JsonPrimitive, parentElement, config)
            } else if (obj.isJsonObject) {
                val (key, doc) = getFieldName(it)
                val className = StringUtils.lowerCamelCase(key)
                addFieldForObjType(project, key, className + config.suffix, false, parentElement, doc, config)
                addClass(project, psiFile, doc, className, obj as JsonObject, config)
            } else if (obj.isJsonArray) {
                val (key, doc) = getFieldName(it)
                val className = StringUtils.lowerCamelCase(key)
                obj.asJsonArray.let { jsonArray ->
                    val (type, json) = getJsonArrayType(jsonArray, className + config.suffix)
                    addFieldForObjType(project, key, type, true, parentElement, doc, config)
                    if (json != null) {
                        addClass(project, psiFile, doc, className, json, config)
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
                jsonKey.substring(0, index).replace(" ", ""),
                jsonKey.substring(index + 1, jsonKey.length - 1),
            )
        } else {
            Pair(jsonKey, "")
        }
    }

    /**
     * 创建Dart类
     */
    private fun addClass(
        project: Project, psiFile: PsiFile, doc: String?,
        className: String, jsonObject: JsonObject, config: Config
    ) {
        DartPsiUtils.createClassElement(project, className + config.suffix)?.also { clazz ->
            if (config.generatorDoc && !doc.isNullOrBlank()) {
                DartPsiUtils.createDocElement(project, "/// $doc")?.also { docElement ->
                    psiFile.add(docElement)
                }
            }

            val element = psiFile.add(clazz)
            if (config.generatorJsonSerializable) {
                val text = if (config.setConverters) {
                    "@JsonSerializable(converters: ${config.convertersValue})"
                } else {
                    "@JsonSerializable()"
                }

                DartPsiUtils.createCommonElement(project, text)?.also {
                    element.addBefore(it, element.firstChild)
                }
            }

            val bodyElement = PsiTreeUtil.getChildOfType(element, DartClassBody::class.java)
            bodyElement?.also { body ->
                val rootElement = body.classMembers
                if (rootElement != null) {
                    createFieldOnJsonObject(project, psiFile, rootElement, jsonObject, config)

                    val name = className + config.suffix
                    createClassConstructorAndSerializableMethod(
                        project, jsonObject, rootElement, name, true,
                        config.generatorJsonSerializable, config.generatorJsonSerializable,
                        config.createFromList, config.nullSafe
                    )
                }
            }
        }
    }

    /**
     * 创建类的构造函数及序列化方法
     */
    private fun createClassConstructorAndSerializableMethod(
        project: Project, jsonObject: JsonObject, classMembers: PsiElement,
        className: String, createConstructor: Boolean, createFactory: Boolean, createToJson: Boolean,
        createFromList: Boolean, nullSafe: Boolean
    ) {

        if (createFactory) {
            val fromJson =
                "factory ${className}.fromJson(Map<String, dynamic> json) => _\$${className}FromJson(json);"
            DartPsiUtils.createClassMember(project, fromJson)?.also {
                classMembers.add(it)
            }
        }

        if (createFromList) {
            val fromJson =
                "static List<${className}> fromJsonList(List<dynamic> json) => json.map((e) => ${className}.fromJson(e as Map<String, dynamic>)).toList();"
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

        if (createConstructor) {
            val keySet = jsonObject.keySet()
            val constructorStr: String
            if (keySet.isEmpty()) {
                constructorStr = "$className();"
            } else {
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
                constructorStr = constructor.toString()
            }

            DartPsiUtils.createCommonElement(project, constructorStr)?.also {
                val parent = classMembers.parent
                parent.addAfter(it, parent.firstChild)
            }
        }
    }

    /**
     * 创建Dart类中普通字段
     */
    private fun addField(
        project: Project, key: String, jsonElement: JsonPrimitive?,
        parentElement: PsiElement, config: Config
    ) {

        val methodElement = PsiTreeUtil.findChildOfAnyType(
            parentElement, DartFactoryConstructorDeclaration::class.java,
            DartMethodDeclaration::class.java
        )

        if (config.generatorDoc) {
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
            if (config.nullSafe) {
                "Object $key"
            } else {
                "Object? $key"
            }
        } else if (jsonElement.isBoolean) {
            if (config.nullSafe) {
                "bool $key"
            } else {
                "bool? $key"
            }
        } else if (jsonElement.isNumber) {
            val type = if (jsonElement.asString.contains(".")) "double" else "int"
            if (config.nullSafe) {
                "$type $key"
            } else {
                "$type? $key"
            }
        } else {
            if (config.nullSafe) {
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
        project: Project, key: String, typeName: String?, isArray: Boolean,
        parentElement: PsiElement, doc: String?, config: Config
    ) {
        val methodElement = PsiTreeUtil.findChildOfAnyType(
            parentElement, DartFactoryConstructorDeclaration::class.java,
            DartMethodDeclaration::class.java
        )

        if (config.generatorDoc && !doc.isNullOrBlank()) {
            DartPsiUtils.createDocElement(project, "/// $doc")?.also { docElement ->
                if (methodElement != null) {
                    parentElement.addBefore(docElement, methodElement)
                } else {
                    parentElement.add(docElement)
                }
            }
        }

        val content = if (typeName == null) {
            if (config.nullSafe) {
                "Object $key"
            } else {
                "Object? $key"
            }
        } else if (isArray) {
            if (config.nullSafe) {
                "List<$typeName> $key"
            } else {
                "List<$typeName>? $key"
            }
        } else {
            if (config.nullSafe) {
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

/**
 * JSON转dart配置类
 */
private class Config(
    val suffix: String,
    val generatorDoc: Boolean,
    val generatorJsonSerializable: Boolean,
    val nullSafe: Boolean,
    val createFromList: Boolean,
    val setConverters: Boolean,
    val convertersValue: String
)
