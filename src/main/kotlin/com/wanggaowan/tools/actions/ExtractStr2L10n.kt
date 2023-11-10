package com.wanggaowan.tools.actions

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonPsiUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
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
import com.jetbrains.lang.dart.psi.DartLongTemplateEntry
import com.jetbrains.lang.dart.psi.DartPsiCompositeElement
import com.jetbrains.lang.dart.psi.DartShortTemplateEntry
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.flutter.FlutterCommandLine
import com.wanggaowan.tools.utils.flutter.YamlUtils
import com.wanggaowan.tools.utils.msg.Toast
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.json.JSONObject
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

private val LOG = logger<ExtractStr2L10n>()

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
        // 得到的结果格式：'xx', "xx", 'xx$a', "xx$a", 'xx${a??''}', "xx${a??''}"
        var text = selectedElement.text
        if (text.length > 2) {
            // 去除前后单引号或双引号
            text = text.substring(1, text.length - 1)
        }

        var translateText = text.trim()
        val dartTemplateEntryList = mutableListOf<DartPsiCompositeElement>()
        findAllDartTemplateEntry(selectedElement.firstChild, dartTemplateEntryList)
        if (dartTemplateEntryList.isNotEmpty()) {
            dartTemplateEntryList.indices.forEach {
                val element = dartTemplateEntryList[it].text
                var index = text.indexOf(element)
                if (index != -1) {
                    text = text.replaceRange(index, index + element.length, "{param$it}")
                }

                index = translateText.indexOf(element)
                if (index != -1) {
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

        changeData(
            project,
            selectedFile,
            selectedElement,
            existKey,
            dartTemplateEntryList,
            text,
            translateText,
            jsonObject,
            rootDir,
            arbPsiFile
        )
    }

    private fun changeData(
        project: Project,
        selectedFile: PsiFile,
        selectedElement: PsiElement,
        existKey: String?,
        dartTemplateEntryList: List<DartPsiCompositeElement>,
        originalText: String,
        translateText: String,
        jsonObject: JsonObject?,
        rootDir: VirtualFile,
        arbPsiFile: PsiFile
    ) {

        if (existKey != null) {
            WriteCommandAction.runWriteCommandAction(project) {
                replaceElement(selectedFile, selectedElement, dartTemplateEntryList, existKey)
            }
        } else {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Translate") {
                override fun run(progressIndicator: ProgressIndicator) {
                    progressIndicator.isIndeterminate = true
                    var finish = false
                    CoroutineScope(Dispatchers.Default).launch launch2@{
                        val translate = translate(translateText, dartTemplateEntryList.isNotEmpty())
                        CoroutineScope(Dispatchers.Main).launch {
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

                            finish = true
                            progressIndicator.isIndeterminate = false
                            progressIndicator.fraction = 1.0
                            if (showRename) {
                                val key = renameKey(project, translate, jsonObject) ?: return@launch
                                WriteCommandAction.runWriteCommandAction(project) {
                                    insertElement(project, rootDir, arbPsiFile, jsonObject, key, originalText)
                                    replaceElement(selectedFile, selectedElement, dartTemplateEntryList, key)

                                }
                            } else {
                                WriteCommandAction.runWriteCommandAction(project) {
                                    insertElement(project, rootDir, arbPsiFile, jsonObject, translate!!, originalText)
                                    replaceElement(selectedFile, selectedElement, dartTemplateEntryList, translate)
                                }
                            }
                        }
                    }

                    while (!finish) {
                        Thread.sleep(100)
                    }
                    progressIndicator.isIndeterminate = false
                    progressIndicator.fraction = 1.0
                }
            })
        }
    }

    private tailrec fun findAllDartTemplateEntry(
        psiElement: PsiElement?,
        list: MutableList<DartPsiCompositeElement>
    ) {
        if (psiElement == null) {
            return
        }

        if (psiElement is DartShortTemplateEntry) {
            list.add(psiElement)
        } else if (psiElement is DartLongTemplateEntry) {
            list.add(psiElement)
        }

        findAllDartTemplateEntry(psiElement.nextSibling, list)
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
        selectedFile: PsiFile,
        selectedElement: PsiElement,
        dartTemplateEntryList: List<DartPsiCompositeElement>,
        key: String
    ) {

        val content = if (dartTemplateEntryList.isEmpty()) {
            "S.current.$key"
        } else {
            val builder = StringBuilder("S.current.$key(")
            var index = 0
            dartTemplateEntryList.forEach {
                if (index > 0) {
                    builder.append(", ")
                }
                val text = it.text
                if (it is DartShortTemplateEntry) {
                    builder.append(text.substring(1))
                } else {
                    builder.append(it.text.substring(2, text.length - 1))
                }
                index++
            }
            builder.append(")")
            builder.toString()
        }

        val manager = FileDocumentManager.getInstance()
        val document = manager.getDocument(selectedFile.virtualFile)
        document?.replaceString(selectedElement.startOffset, selectedElement.endOffset, content)
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

        val manager = FileDocumentManager.getInstance()
        val document = manager.getDocument(arbPsiFile.virtualFile)
        if (document != null) {
            manager.saveDocument(document)
        } else {
            manager.saveAllDocuments()
        }
        FlutterSdk.getFlutterSdk(project)?.also { sdk ->
            val commandLine = FlutterCommandLine(sdk, rootDir, FlutterCommandLine.Type.GEN_L10N)
            commandLine.start()
        }
    }

    private suspend fun translate(text: String, isFormat: Boolean): String? {
        val uuid = UUID.randomUUID().toString()
        val dateformat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        dateformat.timeZone = TimeZone.getTimeZone("UTC")
        val time = dateformat.format(Date())

        var accessKeyId = "TFRBSTV0UnFrbzY3QThVeFZDOGt4dHNu"
        accessKeyId = String(mapValue(accessKeyId))
        val queryMap = mutableMapOf<String, String>()
        queryMap["AccessKeyId"] = accessKeyId
        queryMap["Action"] = "TranslateGeneral"
        queryMap["Format"] = "JSON"
        queryMap["FormatType"] = "text"
        queryMap["RegionId"] = "cn-hangzhou"
        queryMap["Scene"] = "general"
        queryMap["SignatureVersion"] = "1.0"
        queryMap["SignatureMethod"] = "HMAC-SHA1"
        queryMap["Status"] = "Available"
        queryMap["SignatureNonce"] = uuid
        queryMap["SourceLanguage"] = "zh"
        queryMap["SourceText"] = text
        queryMap["TargetLanguage"] = "en"
        queryMap["Timestamp"] = time
        queryMap["Version"] = "2018-10-12"
        var queryString = getCanonicalizedQueryString(queryMap, queryMap.keys.toTypedArray())

        val stringToSign = "GET" + "&" + encodeURI("/") + "&" + encodeURI(queryString)
        val signature = encodeURI(Base64.getEncoder().encodeToString(signatureMethod(stringToSign)))
        queryString += "&Signature=$signature"
        try {
            val response = HttpClient(CIO) {
                engine {
                    requestTimeout = 5000
                    endpoint {
                        connectTimeout = 5000
                    }
                }
            }.get("https://mt.cn-hangzhou.aliyuncs.com/?$queryString")
            val body = response.bodyAsText()
            if (body.isEmpty()) {
                return null
            }

            // {"RequestId":"A721413A-7DCD-51B0-8AEE-FCE433CEACA2","Data":{"WordCount":"4","Translated":"Test Translation"},"Code":"200"}
            val jsonObject = JSONObject(body)
            val code = jsonObject.getString("Code")
            if (code != "200") {
                LOG.error("阿里翻译失败：$body")
                return null
            }

            val data = jsonObject.getJSONObject("Data") ?: return null
            var value = data.getString("Translated")
            if (value.isNullOrEmpty()) {
                return null
            }

            // \pP：中的小写p是property的意思，表示Unicode属性，用于Unicode正表达式的前缀。
            //
            // P：标点字符
            //
            // L：字母；
            //
            // M：标记符号（一般不会单独出现）；
            //
            // Z：分隔符（比如空格、换行等）；
            //
            // S：符号（比如数学符号、货币符号等）；
            //
            // N：数字（比如阿拉伯数字、罗马数字等）；
            //
            // C：其他字符
            value = value.lowercase().replace(Regex("[\\pP\\pS]"), "_")
                .replace(" ", "_")
            if (isFormat) {
                value += "_format"
            }

            value = value.replace("_____", "_")
                .replace("____", "_")
                .replace("___", "_")
                .replace("__", "_")

            if (value.startsWith("_")) {
                value = value.substring(1, value.length)
            }

            if (value.endsWith("_")) {
                value = value.substring(0, value.length - 1)
            }

            return value
        } catch (e: Exception) {
            LOG.error("阿里翻译失败：${e.message}")
            return null
        }
    }

    @Throws(java.lang.Exception::class)
    private fun signatureMethod(stringToSign: String?): ByteArray? {
        val secret = "V3FWRGI3c210UW9rOGJUOXF2VHhENnYzbmF1bjU1Jg=="
        if (stringToSign == null) {
            return null
        }
        val sha256Hmac = Mac.getInstance("HmacSHA1")
        val secretKey = SecretKeySpec(mapValue(secret), "HmacSHA1")
        sha256Hmac.init(secretKey)
        return sha256Hmac.doFinal(stringToSign.toByteArray())
    }

    @Throws(java.lang.Exception::class)
    fun getCanonicalizedQueryString(
        query: Map<String, String?>,
        keys: Array<String>
    ): String {
        if (query.isEmpty()) {
            return ""
        }
        if (keys.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()
        Arrays.sort(keys)


        var key: String?
        var value: String?
        for (i in keys.indices) {
            key = keys[i]
            sb.append(encodeURI(key))
            value = query[key]
            sb.append("=")
            if (!value.isNullOrEmpty()) {
                sb.append(encodeURI(value))
            }
            sb.append("&")
        }
        return sb.deleteCharAt(sb.length - 1).toString()
    }

    private fun mapValue(value: String): ByteArray {
        return Base64.getDecoder().decode(value)
    }

    private fun encodeURI(content:String):String {
        return try {
            URLEncoder.encode(content, StandardCharsets.UTF_8.name()).replace("+", "%20").replace("%7E", "~")
        } catch (var2: UnsupportedEncodingException) {
            content
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
        content.minimumSize = Dimension(300, 35)
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

