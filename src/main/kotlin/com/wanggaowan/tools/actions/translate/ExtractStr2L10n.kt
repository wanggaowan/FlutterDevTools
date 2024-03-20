package com.wanggaowan.tools.actions.translate

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonPsiUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
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
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.LocalTimeCounter
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.lang.dart.psi.DartLongTemplateEntry
import com.jetbrains.lang.dart.psi.DartPsiCompositeElement
import com.jetbrains.lang.dart.psi.DartShortTemplateEntry
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.ui.UIColor
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.TranslateUtils
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
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.yaml.psi.YAMLKeyValue
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.io.File
import javax.swing.*
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
        var useEscaping = false
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

                element = YamlUtils.findElement(psiFile, "use-escaping")
                if (element != null && element is YAMLKeyValue) {
                    element.value?.text?.also {
                        useEscaping = "true".equals(it, true)
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
                NotificationType.WARNING
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

        val otherArbFile = mutableListOf<TranslateArbFile>()
        if (PluginSettings.getExtractStr2L10nTranslateOther(project)) {
            arbPsiFile.parent?.files?.let { files ->
                for (file in files) {
                    val name = file.name
                    if (!name.endsWith(".arb")) {
                        continue
                    }

                    if (name == arbPsiFile.name) {
                        continue
                    }

                    val json = file.getChildOfType<JsonObject>() ?: continue

                    if (existKey != null && json.findProperty(existKey) != null) {
                        // 其它语言已存在当前key
                        continue
                    }

                    val targetLanguage = json.findProperty("@@locale")?.value?.text?.replace("\"", "")
                    if (targetLanguage != null) {
                        otherArbFile.add(TranslateArbFile(targetLanguage, file, json))
                    }
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
            arbPsiFile,
            otherArbFile,
            useEscaping
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
        arbPsiFile: PsiFile,
        otherArbFile: List<TranslateArbFile>,
        useEscaping: Boolean
    ) {

        if (existKey != null) {
            WriteCommandAction.runWriteCommandAction(project) {
                replaceElement(selectedFile, selectedElement, dartTemplateEntryList, existKey)
            }
        } else {
            ProgressUtils.runBackground(project, "Translate",true) { progressIndicator ->
                progressIndicator.isIndeterminate = false
                val totalCount = 1.0 + otherArbFile.size
                CoroutineScope(Dispatchers.Default).launch launch2@{
                    val enTranslate = TranslateUtils.translate(translateText, "en")
                    val isFormat = dartTemplateEntryList.isNotEmpty()
                    val key = TranslateUtils.mapStrToKey(enTranslate, isFormat)
                    if (progressIndicator.isCanceled) {
                        return@launch2
                    }

                    var current = 1.0
                    progressIndicator.fraction = current / totalCount * 0.95
                    otherArbFile.forEach { file ->
                        if (file.targetLanguage == "en" && !isFormat) {
                            file.translate = enTranslate
                        } else {
                            val translate2 = TranslateUtils.fixTranslateError(
                                TranslateUtils.translate(originalText, file.targetLanguage),
                                file.targetLanguage,
                                useEscaping,
                                dartTemplateEntryList.size,
                            )
                            file.translate = translate2
                        }

                        if (progressIndicator.isCanceled) {
                            return@launch2
                        }

                        current++
                        progressIndicator.fraction = current / totalCount * 0.95
                    }

                    if (progressIndicator.isCanceled) {
                        return@launch2
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        var showRename = false
                        if (key == null || PluginSettings.getExtractStr2L10nShowRenameDialog(project)) {
                            showRename = true
                        } else {
                            if (jsonObject?.findProperty(key) != null) {
                                showRename = true
                            }
                        }

                        if (progressIndicator.isCanceled) {
                            return@launch
                        }

                        if (showRename) {
                            progressIndicator.fraction = 1.0
                            val newKey = renameKey(project, key, jsonObject, otherArbFile) ?: return@launch
                            WriteCommandAction.runWriteCommandAction(project) {
                                insertElement(project, rootDir, arbPsiFile, jsonObject, newKey, originalText)
                                replaceElement(selectedFile, selectedElement, dartTemplateEntryList, newKey)
                                otherArbFile.forEach { file ->
                                    val tl = file.translate
                                    if (!tl.isNullOrEmpty()) {
                                        insertElement(
                                            project,
                                            rootDir,
                                            file.arbFile,
                                            file.jsonObject,
                                            newKey,
                                            tl,
                                            false
                                        )
                                    }
                                }
                            }
                        } else {
                            WriteCommandAction.runWriteCommandAction(project) {
                                insertElement(project, rootDir, arbPsiFile, jsonObject, key!!, originalText)
                                replaceElement(selectedFile, selectedElement, dartTemplateEntryList, key)
                                otherArbFile.forEach { file ->
                                    val tl = file.translate
                                    if (!tl.isNullOrEmpty()) {
                                        insertElement(
                                            project,
                                            rootDir,
                                            file.arbFile,
                                            file.jsonObject,
                                            key,
                                            tl,
                                            false
                                        )
                                    }
                                }
                                progressIndicator.fraction = 1.0
                            }
                        }
                    }
                }
            }
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
    private fun renameKey(
        project: Project,
        translate: String?,
        jsonObject: JsonObject?,
        otherArbFile: List<TranslateArbFile>
    ): String? {
        val dialog = InputKeyDialog(project, translate, jsonObject, otherArbFile)
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
        doGenL10n: Boolean = true
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

        if (doGenL10n) {
            FlutterSdk.getFlutterSdk(project)?.also { sdk ->
                val commandLine = FlutterCommandLine(sdk, rootDir, FlutterCommandLine.Type.GEN_L10N)
                commandLine.start()
            }
        }
    }
}

class InputKeyDialog(
    val project: Project,
    private var defaultValue: String?,
    private val jsonObject: JsonObject?,
    private val otherArbFile: List<TranslateArbFile>,
) : DialogWrapper(project, false) {

    private val rootPanel: JComponent
    private var contentTextField: JBTextArea? = null
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

        val content = JBTextArea()
        content.text = defaultValue
        content.minimumSize = Dimension(300, 40)
        content.lineWrap = true
        content.wrapStyleWord = true
        contentTextField = content

        val jsp = JBScrollPane(content)
        jsp.minimumSize = Dimension(300, 40)
        jsp.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        )

        content.addFocusListener(object : FocusListener {
            override fun focusGained(p0: FocusEvent?) {
                jsp.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIColor.INPUT_FOCUS_COLOR, 2, true),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)
                )
                if (this@InputKeyDialog.defaultValue == null) {
                    contentTextField?.also {
                        Toast.show(it, MessageType.WARNING, "翻译失败，请输入多语言key")
                    }
                    this@InputKeyDialog.defaultValue = ""
                }
            }

            override fun focusLost(p0: FocusEvent?) {
                jsp.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)
                )
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

        builder.addComponent(jsp)
        builder.addComponent(existKeyHint)

        if (otherArbFile.isNotEmpty()) {
            val label = JLabel("以下为其它语言翻译内容：")
            label.border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
            builder.addComponent(label)

            otherArbFile.forEach {
                val box = Box.createHorizontalBox()
                box.border = BorderFactory.createEmptyBorder(4, 0, 0, 0)

                val label2 = JLabel("${it.targetLanguage}：")
                label2.preferredSize = Dimension(40, 60)
                box.add(label2)

                val textArea = JBTextArea(it.translate)
                textArea.minimumSize = Dimension(260, 60)
                textArea.lineWrap = true
                textArea.wrapStyleWord = true

                val jsp2 = JBScrollPane(textArea)
                jsp2.minimumSize = Dimension(260, 60)
                box.add(jsp2)

                textArea.addFocusListener(object : FocusListener {
                    override fun focusGained(p0: FocusEvent?) {
                        jsp2.border = BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(UIColor.INPUT_FOCUS_COLOR, 2, true),
                            BorderFactory.createEmptyBorder(2, 2, 2, 2)
                        )
                    }

                    override fun focusLost(p0: FocusEvent?) {
                        jsp2.border = BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true),
                            BorderFactory.createEmptyBorder(2, 2, 2, 2)
                        )
                    }
                })

                textArea.document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(p0: DocumentEvent?) {
                        val str = textArea.text.trim()
                        it.translate = str
                    }

                    override fun removeUpdate(p0: DocumentEvent?) {
                        val str = textArea.text.trim()
                        it.translate = str
                    }

                    override fun changedUpdate(p0: DocumentEvent?) {
                        val str = textArea.text.trim()
                        it.translate = str
                    }
                })

                builder.addComponent(box)
            }
        }

        val rootPanel: JPanel = if (otherArbFile.size > 5) {
            builder.addComponentFillVertically(JPanel(), 0).panel
        } else {
            builder.panel
        }

        val jb = JBScrollPane(rootPanel)
        jb.preferredSize = JBUI.size(300, 40 + 60 * (otherArbFile.size).coerceAtMost(5))
        jb.border = BorderFactory.createEmptyBorder()
        return jb
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

// 需要翻译的arbFile数据
data class TranslateArbFile(
    val targetLanguage: String,
    val arbFile: PsiFile,
    val jsonObject: JsonObject?,
    var translate: String? = null
)

