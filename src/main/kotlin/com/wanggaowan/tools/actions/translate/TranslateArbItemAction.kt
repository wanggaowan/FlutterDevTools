package com.wanggaowan.tools.actions.translate

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonPsiUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.findParentOfType
import com.intellij.util.LocalTimeCounter
import com.wanggaowan.tools.utils.EditorUtils
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.TranslateUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.flutter.FlutterCommandLine
import com.wanggaowan.tools.utils.flutter.YamlUtils
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * 将当前arb文件中选中的一项或多项，翻译并写入其它语言arb文件
 *
 * 与[TranslateArbAction]差异：
 * [TranslateArbAction]是以文件为维度，将模板arb文件中所有内容翻译到指定arb文件；
 * 此处是以项为维度，仅将选中的项翻译并写入同目录下所有其它arb文件，已存在相同key的文件不处理
 *
 * 支持一次选中多项：编辑器中选中一段包含多条json项的文本后执行即可
 *
 * @author Created by wanggaowan on 2026/9/8
 */
class TranslateArbItemAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        if (!e.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        val caret = e.getData(CommonDataKeys.CARET)
        if (caret == null || !caret.hasSelection()) {
            // 非选中区域
            val element = e.getData(CommonDataKeys.PSI_ELEMENT)
            if (element == null) {
                e.presentation.isVisible = false
                return
            }
        }

        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (psiFile?.name?.lowercase()?.endsWith(".arb") != true) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val properties = getJsonProperties(e).filter { !it.name.startsWith("@@") }
        if (properties.isEmpty()) {
            NotificationUtils.showBalloonMsg(
                project,
                "请将光标定位到需要翻译的项，或选中需要翻译的多项",
                NotificationType.WARNING
            )
            return
        }

        val psiFile = properties[0].containingFile ?: return
        val jsonObject = properties[0].findParentOfType<JsonObject>() ?: return
        val sourceLanguage = getLocale(jsonObject)
        if (sourceLanguage == null) {
            NotificationUtils.showBalloonMsg(
                project,
                "${psiFile.name}未配置@@locale属性或@@locale_alias属性",
                NotificationType.WARNING
            )
            return
        }

        val items = mutableListOf<ArbItem>()
        properties.forEach { property ->
            val key = property.name
            // 原始内容，字符串类型时包含前后双引号，如"xxx"，@key描述项时为json对象文本
            val valueText = property.value?.text
            // @key为描述项，不需要翻译，直接同步到其它arb文件
            items.add(ArbItem(key, valueText, key.startsWith("@")))
        }

        val targets = mutableListOf<TargetArbFile>()
        var existItemCount = 0
        var noLocaleCount = 0
        psiFile.parent?.files?.forEach { file ->
            if (file == psiFile || file.virtualFile?.name?.lowercase()?.endsWith(".arb") != true) {
                return@forEach
            }

            val json = file.getChildOfType<JsonObject>() ?: return@forEach
            val targetLanguage = getLocale(json)
            if (targetLanguage == null) {
                noLocaleCount++
                return@forEach
            }

            // 已存在相同key的项不处理
            val needItems = items.filter { json.findProperty(it.key) == null }
            existItemCount += items.size - needItems.size
            if (needItems.isEmpty()) {
                return@forEach
            }

            targets.add(TargetArbFile(file, json, targetLanguage, needItems))
        }

        if (targets.isEmpty()) {
            val msg = when {
                noLocaleCount > 0 -> "存在${noLocaleCount}个arb文件未配置@@locale属性或@@locale_alias属性"
                existItemCount > 0 -> "其它arb文件中已存在选中项"
                else -> "未找到其它arb文件"
            }
            NotificationUtils.showBalloonMsg(project, msg, NotificationType.WARNING)
            return
        }

        val useEscaping = isUseEscaping(project, psiFile)
        val rootDir = getRootDir(psiFile)
        val total = targets.sumOf { it.items.size }
        ProgressUtils.runBackground(project, "Translate", true) { indicator ->
            indicator.isIndeterminate = false
            var current = 0.0
            var successItemCount = 0
            var successFileCount = 0
            var failedItemCount = 0
            val failedFiles = mutableListOf<String>()
            CoroutineScope(Dispatchers.IO).launch launch2@{
                targets.forEach { target ->
                    if (indicator.isCanceled) {
                        return@launch2
                    }

                    val values = mutableListOf<Pair<String, String>>()
                    target.items.forEach { item ->
                        if (indicator.isCanceled) {
                            return@launch2
                        }

                        current++
                        indicator.text = "${current.toInt()} / $total ${target.psiFile.name}: ${item.key}"
                        indicator.fraction = current / total * 0.95

                        val result =
                            if (item.isMetadata || target.language == sourceLanguage || item.valueText.isNullOrEmpty()) {
                                // 描述项或目标语言与源语言一致，直接复制内容
                                item.valueText
                            } else {
                                translateValue(item.valueText, sourceLanguage, target.language, useEscaping)
                            }

                        if (result == null) {
                            failedItemCount++
                            return@forEach
                        }

                        values.add(item.key to result)
                    }

                    if (values.isEmpty()) {
                        failedFiles.add(target.psiFile.name)
                        return@forEach
                    }

                    val writeCount = writeResult(project, target, values)
                    if (writeCount > 0) {
                        successFileCount++
                        successItemCount += writeCount
                    }

                    if (writeCount < values.size) {
                        failedItemCount += values.size - writeCount
                        failedFiles.add(target.psiFile.name)
                    }
                }

                indicator.fraction = 1.0
                if (successItemCount > 0) {
                    // 生成新的多语言文件
                    genL10n(project, rootDir)
                }

                var msg = buildString {
                    if (successItemCount > 0) {
                        append("已写入${successItemCount}项到${successFileCount}个arb文件")
                    }
                    if (existItemCount > 0) {
                        if (isNotEmpty()) {
                            append("，")
                        }
                        append("${existItemCount}项已存在未处理")
                    }
                    if (failedItemCount > 0) {
                        if (isNotEmpty()) {
                            append("，")
                        }
                        append("${failedItemCount}项翻译失败")
                    }
                    if (failedFiles.isNotEmpty()) {
                        if (isNotEmpty()) {
                            append("，")
                        }
                        append("${failedFiles.distinct().joinToString("、")}写入失败，请重试")
                    }
                }

                if (msg.isEmpty()) {
                    msg = "翻译失败，请重试"
                }

                NotificationUtils.showBalloonMsg(
                    project,
                    msg,
                    if (failedItemCount > 0 || failedFiles.isNotEmpty()) {
                        NotificationType.WARNING
                    } else {
                        NotificationType.INFORMATION
                    }
                )
            }
        }
    }

    /**
     * 获取当前arb文件key对应内容翻译为[targetLanguage]后的json值文本，包含前后双引号
     */
    private fun translateValue(
        valueText: String,
        sourceLanguage: String,
        targetLanguage: String,
        useEscaping: Boolean
    ): String? {
        val content = if (valueText.length >= 2 && valueText.startsWith("\"") && valueText.endsWith("\"")) {
            valueText.substring(1, valueText.length - 1)
        } else {
            valueText
        }

        if (content.isEmpty()) {
            return valueText
        }

        val translate = TranslateUtils.translate(content, sourceLanguage, targetLanguage) ?: return null
        // 当前内容来源于模板arb文件，占位符格式为{param0}，因此isByTemplate传true
        val result = TranslateUtils.fixTranslateError(translate, useEscaping, true) ?: return null
        return "\"$result\""
    }

    /**
     * 将[values]中的key-value写入[target]对应的arb文件，返回成功写入的项数
     */
    private fun writeResult(project: Project, target: TargetArbFile, values: List<Pair<String, String>>): Int {
        var count = 0
        WriteCommandAction.runWriteCommandAction(project) {
            values.forEach { (key, valueText) ->
                val dummyFile = PsiFileFactory.getInstance(project).createFileFromText(
                    "dummy.${JsonFileType.INSTANCE.defaultExtension}",
                    JsonFileType.INSTANCE,
                    "{\"$key\": $valueText}",
                    LocalTimeCounter.currentTime(),
                    false
                )

                val property = dummyFile.getChildOfType<JsonObject>()?.propertyList?.firstOrNull()
                    ?: return@forEach

                JsonPsiUtil.addProperty(target.jsonObject, property, false)
                count++
            }

            val document = target.psiFile.viewProvider.document
            if (document != null) {
                FileDocumentManager.getInstance().saveDocument(document)
            } else {
                FileDocumentManager.getInstance().saveAllDocuments()
            }
        }
        return count
    }

    /**
     * 获取json对象配置的语言，优先取@@locale_alias
     */
    private fun getLocale(jsonObject: JsonObject): String? {
        var locale = jsonObject.findProperty("@@locale_alias")?.value?.text?.replace("\"", "")
        if (locale.isNullOrEmpty()) {
            locale = jsonObject.findProperty("@@locale")?.value?.text?.replace("\"", "")
        }
        return if (locale.isNullOrEmpty()) null else locale
    }

    /**
     * 获取arb文件是否启用转义字符
     */
    private fun isUseEscaping(project: Project, psiFile: PsiFile): Boolean {
        val rootDir = getRootDir(psiFile) ?: return false
        val config = rootDir.findChild("l10n.yaml") ?: return false
        val l10nPsiFile = config.toPsiFile(project) ?: return false
        val element = YamlUtils.findElement(l10nPsiFile, "use-escaping")
        if (element is YAMLKeyValue) {
            return "true".equals(element.value?.text, true)
        }
        return false
    }

    /**
     * 获取执行flutter命令的根目录
     */
    private fun getRootDir(psiFile: PsiFile): VirtualFile? {
        val pubRoot = PubRoot.forPsiFile(psiFile) ?: return null
        var rootDir = pubRoot.root
        val example = pubRoot.exampleDir
        if (example != null && psiFile.virtualFile.path.startsWith(example.path)) {
            rootDir = example
        }
        return rootDir
    }

    private fun genL10n(project: Project, rootDir: VirtualFile?) {
        if (rootDir == null) {
            return
        }

        FlutterSdk.getFlutterSdk(project)?.also { sdk ->
            val commandLine = FlutterCommandLine(sdk, rootDir, FlutterCommandLine.Type.GEN_L10N)
            commandLine.start()
        }
    }

    /**
     * 获取光标所在的json项，需在EDT线程执行，不能在update中调用
     */
    private fun getJsonProperty(e: AnActionEvent): JsonProperty? {
        e.getData(CommonDataKeys.PSI_ELEMENT)?.findParentOfType<JsonProperty>(false)?.let {
            return it
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (editor != null && psiFile != null) {
            return psiFile.findElementAt(editor.caretModel.offset)?.findParentOfType<JsonProperty>(false)
        }

        return null
    }

    /**
     * 获取选中的json项，支持一次选中多项，需在EDT线程执行
     *
     * 编辑器存在选区时，取所有与选区有交集的json项，否则取光标所在项
     */
    private fun getJsonProperties(e: AnActionEvent): List<JsonProperty> {
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor != null) {
            val properties = EditorUtils.getSelectionAreaPsiElement(editor) {
                it.findParentOfType<JsonProperty>(false)
            }
            if (!properties.isNullOrEmpty()) {
                return properties
            }
        }

        return getJsonProperty(e)?.let { listOf(it) } ?: emptyList()
    }

    private class ArbItem(
        val key: String,
        val valueText: String?,
        // @key描述项不需要翻译
        val isMetadata: Boolean,
    )

    private class TargetArbFile(
        val psiFile: PsiFile,
        val jsonObject: JsonObject,
        val language: String,
        // 当前文件中不存在，需要写入的项
        val items: List<ArbItem>,
    )
}
