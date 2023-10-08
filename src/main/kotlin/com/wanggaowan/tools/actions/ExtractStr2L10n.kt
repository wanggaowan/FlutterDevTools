package com.wanggaowan.tools.actions

import com.aliyun.alimt20181012.Client
import com.aliyun.alimt20181012.models.TranslateGeneralRequest
import com.aliyun.teaopenapi.models.Config
import com.aliyun.teautil.models.RuntimeOptions
import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonPsiUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.util.LocalTimeCounter
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.jetbrains.lang.dart.psi.DartShortTemplateEntry
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.flutter.FlutterCommandLine
import com.wanggaowan.tools.utils.flutter.YamlUtils
import com.wanggaowan.tools.utils.msg.Toast
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.yaml.psi.YAMLKeyValue
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.io.File
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 提取文本为多语言
 *
 * @author Created by wanggaowan on 2023/10/7 10:56
 */
class ExtractStr2L10n : DumbAwareAction() {

    private var selectedPsiElement: PsiElement? = null
    private var selectedPsiFile: PsiFile? = null

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

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

        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (psiFile == null || psiFile.isDirectory) {
            e.presentation.isVisible = false
            return
        }

        if (!psiFile.name.endsWith(".dart")) {
            e.presentation.isVisible = false
            return
        }

        val psiElement: PsiElement? = psiFile.findElementAt(editor.selectionModel.selectionStart)
        if (psiElement == null || psiElement !is LeafPsiElement) {
            e.presentation.isVisible = false
            return
        }

        val parent = psiElement.getParentOfType<DartStringLiteralExpression>(true)
        if (parent == null) {
            e.presentation.isVisible = false
            return
        }

        selectedPsiFile = psiFile
        selectedPsiElement = parent
        e.presentation.isVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val selectedFile = selectedPsiFile ?: return
        val selectedElement = selectedPsiElement ?: return
        val pubRoot = PubRoot.forPsiFile(selectedFile) ?: return
        var rootDir = pubRoot.root
        val example = pubRoot.exampleDir
        if (example != null && selectedFile.virtualFile.path.startsWith(example.path)) {
            rootDir = example
        }

        var rootDirPath = rootDir.path
        var resDirPath = "lib/l10n"
        var resName = "app_en.arb"
        val config = rootDir.findChild("l10n.yaml")
        if (config != null) {
            val psiFile = config.toPsiFile(project)
            if (psiFile != null) {
                var element = YamlUtils.findElement(psiFile, "project-dir")
                if (element != null && element is YAMLKeyValue) {
                    element.value?.text?.also {
                        rootDirPath = it
                    }
                }

                element = YamlUtils.findElement(psiFile, "arb-dir")
                if (element != null && element is YAMLKeyValue) {
                    element.value?.text?.also {
                        resDirPath = it
                    }
                }

                element = YamlUtils.findElement(psiFile, "template-arb-file")
                if (element != null && element is YAMLKeyValue) {
                    element.value?.text?.also {
                        resName = it
                    }
                }
            }

        }

        val arbFilePath = rootDirPath + File.separator + resDirPath + File.separator + resName
        val arbFile = VirtualFileManager.getInstance().findFileByUrl("file://$arbFilePath")
        if (arbFile == null || arbFile.isDirectory) {
            NotificationUtils.showBalloonMsg(
                project,
                "未配置arb模板文件，请提供lib/l10n/app_en.arb模版文件或通过l10n.yaml配置",
                NotificationType.ERROR
            )
            return
        }

        val arbPsiFile = arbFile.toPsiFile(project) ?: return
        val jsonObject = arbPsiFile.getChildOfType<JsonObject>()
        var text = selectedElement.text.replace("\"", "").replace("'", "")
        var translateText = text
        val dartShortTemplateEntryList = mutableListOf<DartShortTemplateEntry>()
        findAllDartShortTemplateEntry(selectedElement.firstChild, dartShortTemplateEntryList)
        if (dartShortTemplateEntryList.isNotEmpty()) {
            dartShortTemplateEntryList.indices.forEach {
                val element = dartShortTemplateEntryList[it].text
                val index = text.indexOf(element)
                if (index != -1) {
                    text = text.replaceRange(index, index + element.length, "{param$it}")
                    translateText = translateText.replaceRange(index, index + element.length, "")
                }
            }
        }

        var existKey: String? = null
        if (jsonObject != null) {
            for (property in jsonObject.propertyList) {
                if (property.value?.textMatches("\"$text\"") == true) {
                    existKey = property.name
                    break
                }
            }
        }

        if (existKey != null) {
            WriteCommandAction.runWriteCommandAction(project) {
                replaceElement(project, selectedElement, dartShortTemplateEntryList, existKey)
            }
        } else {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Translate") {
                override fun run(progressIndicator: ProgressIndicator) {
                    progressIndicator.isIndeterminate = true
                    CoroutineScope(Dispatchers.Default).launch launch2@{
                        val translate = translate(translateText, dartShortTemplateEntryList.isNotEmpty())
                        CoroutineScope(Dispatchers.Main).launch {
                            progressIndicator.isIndeterminate = false
                            progressIndicator.fraction = 1.0

                            var showRename = false
                            if (translate == null || PluginSettings.getExtractStr2L10nShowRenameDialog(project)) {
                                showRename = true
                            } else {
                                val propertyList = jsonObject?.propertyList
                                if (!propertyList.isNullOrEmpty()) {
                                    for (property in propertyList) {
                                        if (property.name == translate) {
                                            showRename = true
                                            break
                                        }
                                    }
                                }
                            }

                            if (showRename) {
                                val key = renameKey(project, translate, jsonObject) ?: return@launch
                                WriteCommandAction.runWriteCommandAction(project) {
                                    replaceElement(project, selectedElement, dartShortTemplateEntryList, key)
                                    insertElement(project, rootDir, arbPsiFile, jsonObject, key, text)
                                }
                            } else {
                                WriteCommandAction.runWriteCommandAction(project) {
                                    replaceElement(project, selectedElement, dartShortTemplateEntryList, translate!!)
                                    insertElement(project, rootDir, arbPsiFile, jsonObject, translate, text)
                                }
                            }
                        }
                    }
                }
            })
        }
    }

    private tailrec fun findAllDartShortTemplateEntry(
        psiElement: PsiElement?,
        list: MutableList<DartShortTemplateEntry>
    ) {
        if (psiElement == null) {
            return
        }

        if (psiElement is DartShortTemplateEntry) {
            list.add(psiElement)
        }
        findAllDartShortTemplateEntry(psiElement.nextSibling, list)
    }

    // 重命名多语言在arb文件中的key
    private fun renameKey(project: Project, translate: String?, jsonObject: JsonObject?): String? {
        val dialog = InputKeyDialog(project, translate, jsonObject)
        dialog.show()
        if (dialog.exitCode != DialogWrapper.OK_EXIT_CODE) {
            return null
        }

        return dialog.getValue()
    }

    private fun replaceElement(
        project: Project,
        selectedElement: PsiElement,
        dartShortTemplateEntryList: List<DartShortTemplateEntry>,
        key: String
    ) {

        val content = if (dartShortTemplateEntryList.isEmpty()) {
            "S.current.$key"
        } else {
            val builder = StringBuilder("S.current.$key(")
            var index = 0
            dartShortTemplateEntryList.forEach {
                if (index > 0) {
                    builder.append(", ")
                }
                builder.append(it.text.substring(1))
                index++
            }
            builder.append(")")
            builder.toString()
        }

        DartPsiUtils.createArgumentItem(project, content)?.also {
            selectedElement.replace(it)
        }
    }

    private fun insertElement(
        project: Project,
        rootDir: VirtualFile,
        arbPsiFile: PsiFile,
        jsonObject: JsonObject?,
        key: String,
        value: String,
    ) {
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "dummy.${JsonFileType.INSTANCE.defaultExtension}",
            JsonFileType.INSTANCE,
            "{\"$key\": \"$value\"}",
            LocalTimeCounter.currentTime(),
            false
        )

        if (jsonObject != null) {
            val temp = psiFile.getChildOfType<JsonObject>()
            if (temp != null) {
                val propertyList = temp.propertyList
                if (propertyList.isNotEmpty()) {
                    JsonPsiUtil.addProperty(jsonObject, propertyList[0], false)
                }
            }
        } else {
            arbPsiFile.add(psiFile.firstChild)
        }

        FlutterSdk.getFlutterSdk(project)?.also { sdk ->
            FileDocumentManager.getInstance().saveAllDocuments()
            val commandLine = FlutterCommandLine(sdk, rootDir, FlutterCommandLine.Type.GEN_L10N)
            commandLine.start()
        }
    }

    private fun translate(text: String, isFormat: Boolean): String? {
        try {
            val config = Config()
            config.accessKeyId = "LTAI5tRqko67A8UxVC8kxtsn"
            config.accessKeySecret = "WqVDb7smtQok8bT9qvTxD6v3naun55"
            config.endpoint = "mt.cn-hangzhou.aliyuncs.com"
            config.readTimeout = 5000
            config.connectTimeout = 5000
            val client = Client(config)

            val runtimeOption = RuntimeOptions()
            runtimeOption.connectTimeout = 5000
            runtimeOption.readTimeout = 5000
            // 开启自动重试机制
            runtimeOption.autoretry = false
            // 设置自动重试次数
            // runtimeOption.maxAttempts = 3

            val translateRequest = TranslateGeneralRequest()
            translateRequest.formatType = "text"
            translateRequest.sourceText = text
            translateRequest.sourceLanguage = "zh"
            translateRequest.targetLanguage = "en"

            val response = client.translateGeneralWithOptions(translateRequest, runtimeOption)
            if (response.getStatusCode() != 200) {
                return null
            }

            var value = response.body.data.translated
            if (value.isNullOrEmpty()) {
                return null
            }

            value = value.lowercase()
                .replace(",", "")
                .replace(".", "")
                .replace(" ", "_")
            if (isFormat) {
                value += "_format"
            }
            return value
        } catch (e: Exception) {
            return null
        }
    }
}

class InputKeyDialog(
    val project: Project,
    private var defaultValue: String?,
    private val jsonObject: JsonObject?
) : DialogWrapper(project, false) {

    private val rootPanel: JComponent
    private var contentTextField: JBTextField? = null
    private var existKey: Boolean = false

    init {
        rootPanel = createRootPanel()
        init()
    }

    override fun createCenterPanel(): JComponent = rootPanel

    override fun getPreferredFocusedComponent(): JComponent? {
        return contentTextField
    }

    private fun createRootPanel(): JComponent {
        val builder = FormBuilder.createFormBuilder()
        builder.addComponent(JLabel("输入多语言key："))

        val existKeyHint = JLabel("已存在相同key")
        existKeyHint.foreground = JBColor.RED
        existKeyHint.font = UIUtil.getFont(UIUtil.FontSize.SMALL, existKeyHint.font)
        existKey = if (defaultValue.isNullOrEmpty()) false else isExistKey(defaultValue!!)
        existKeyHint.isVisible = existKey

        val content = JBTextField()
        content.text = defaultValue
        content.preferredSize = Dimension(240, 30)
        content.minimumSize = Dimension(240, 30)
        contentTextField = content
        content.addFocusListener(object : FocusListener {
            override fun focusGained(p0: FocusEvent?) {
                if (this@InputKeyDialog.defaultValue == null) {
                    contentTextField?.also {
                        Toast.show(it, MessageType.WARNING, "翻译失败，请输入多语言key")
                    }
                    this@InputKeyDialog.defaultValue = ""
                }
            }

            override fun focusLost(p0: FocusEvent?) {

            }
        })

        content.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(p0: DocumentEvent?) {
                val str = content.text.trim()
                existKey = isExistKey(str)
                existKeyHint.isVisible = existKey
            }

            override fun removeUpdate(p0: DocumentEvent?) {
                val str = content.text.trim()
                existKey = isExistKey(str)
                existKeyHint.isVisible = existKey
            }

            override fun changedUpdate(p0: DocumentEvent?) {
                val str = content.text.trim()
                existKey = isExistKey(str)
                existKeyHint.isVisible = existKey
            }
        })

        builder.addComponent(content)
        builder.addComponent(existKeyHint)
        return builder.addComponentFillVertically(JPanel(), 0).panel
    }

    private fun isExistKey(text: String): Boolean {
        val propertyList = jsonObject?.propertyList
        if (!propertyList.isNullOrEmpty()) {
            for (property in propertyList) {
                if (property.name == text) {
                    return true
                }
            }
        }
        return false
    }

    fun getValue(): String {
        return contentTextField?.text ?: ""
    }

    override fun doOKAction() {
        val value = getValue()
        if (value.isEmpty()) {
            contentTextField?.also {
                Toast.show(it, MessageType.WARNING, "请输入多语言key")
            }
            return
        }

        if (existKey) {
            contentTextField?.also {
                Toast.show(it, MessageType.WARNING, "已存在相同的key")
            }
            return
        }

        super.doOKAction()
    }
}

