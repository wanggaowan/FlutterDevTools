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
 * 将当前arb文件中选中的一项，翻译并写入其它语言arb文件
 *
 * 与[TranslateArbAction]差异：
 * [TranslateArbAction]是以文件为维度，将模板arb文件中所有内容翻译到指定arb文件；
 * 此处是以项为维度，仅将选中的一项翻译并写入同目录下所有其它arb文件，已存在相同key的文件不处理
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

        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (psiFile?.virtualFile?.name?.lowercase()?.endsWith(".arb") != true) {
            e.presentation.isVisible = false
            return
        }

        val property = getJsonProperty(e)
        if (property != null && property.name.startsWith("@@")) {
            // @@locale、@@locale_alias等为文件级别配置，不需要翻译
            e.presentation.isVisible = false
            return
        }

        e.presentation.text = if (property?.name?.startsWith("@") == true) SYNC_TEXT else TRANSLATE_TEXT
        e.presentation.isVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val property = getJsonProperty(e)
        if (property == null) {
            NotificationUtils.showBalloonMsg(
                project,
                "请将光标定位到需要翻译的项",
                NotificationType.WARNING
            )
            return
        }

        val key = property.name
        if (key.startsWith("@@")) {
            return
        }

        val psiFile = property.containingFile ?: return
        val jsonObject = property.findParentOfType<JsonObject>() ?: return
        val sourceLanguage = getLocale(jsonObject)
        if (sourceLanguage == null) {
            NotificationUtils.showBalloonMsg(
                project,
                "${psiFile.name}未配置@@locale属性或@@locale_alias属性",
                NotificationType.WARNING
            )
            return
        }

        // 原始内容，字符串类型时包含前后双引号，如"xxx"，@key描述项时为json对象文本
        val valueText = property.value?.text
        if (valueText.isNullOrEmpty()) {
            NotificationUtils.showBalloonMsg(project, "[$key]没有可翻译的内容", NotificationType.WARNING)
            return
        }

        // @key为描述项，不需要翻译，直接同步到其它arb文件
        val isMetadata = key.startsWith("@")

        val targets = mutableListOf<TargetArbFile>()
        var existCount = 0
        var noLocaleCount = 0
        psiFile.parent?.files?.forEach { file ->
            if (file == psiFile || file.virtualFile?.name?.lowercase()?.endsWith(".arb") != true) {
                return@forEach
            }

            val json = file.getChildOfType<JsonObject>() ?: return@forEach
            if (json.findProperty(key) != null) {
                // 已存在相同key，不处理
                existCount++
                return@forEach
            }

            val targetLanguage = getLocale(json)
            if (targetLanguage == null) {
                noLocaleCount++
                return@forEach
            }

            targets.add(TargetArbFile(file, json, targetLanguage))
        }

        if (targets.isEmpty()) {
            val msg = when {
                noLocaleCount > 0 -> "存在${noLocaleCount}个arb文件未配置@@locale属性或@@locale_alias属性"
                existCount > 0 -> "其它arb文件中已存在[$key]"
                else -> "未找到其它arb文件"
            }
            NotificationUtils.showBalloonMsg(project, msg, NotificationType.INFORMATION)
            return
        }

        val useEscaping = isUseEscaping(project, psiFile)
        val rootDir = getRootDir(psiFile)
        ProgressUtils.runBackground(project, "Translate", true) { indicator ->
            indicator.isIndeterminate = false
            val total = targets.size
            var current = 0.0
            var successCount = 0
            val failedFiles = mutableListOf<String>()
            CoroutineScope(Dispatchers.IO).launch launch2@{
                targets.forEach { target ->
                    if (indicator.isCanceled) {
                        return@launch2
                    }

                    current++
                    indicator.text = "${current.toInt()} / $total Translating: ${target.psiFile.name}"
                    indicator.fraction = current / total * 0.95

                    val result = if (isMetadata || target.language == sourceLanguage) {
                        // 描述项或目标语言与源语言一致，直接复制内容
                        valueText
                    } else {
                        translateValue(valueText, sourceLanguage, target.language, useEscaping)
                    }

                    if (result == null) {
                        failedFiles.add(target.psiFile.name)
                        return@forEach
                    }

                    if (writeResult(project, target, key, result)) {
                        successCount++
                    } else {
                        failedFiles.add(target.psiFile.name)
                    }
                }

                indicator.fraction = 1.0
                if (successCount > 0) {
                    // 生成新的多语言文件
                    genL10n(project, rootDir)
                }

                val msg = buildString {
                    if (successCount > 0) {
                        append("[$key]已写入${successCount}个arb文件")
                    }
                    if (existCount > 0) {
                        if (isNotEmpty()) {
                            append("，")
                        }
                        append("${existCount}个arb文件已存在[$key]，未处理")
                    }
                    if (failedFiles.isNotEmpty()) {
                        if (isNotEmpty()) {
                            append("，")
                        }
                        append("${failedFiles.joinToString("、")}写入失败，请重试")
                    }
                }
                NotificationUtils.showBalloonMsg(
                    project,
                    msg,
                    if (failedFiles.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING
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
     * 将[key]：[valueText]写入[target]对应的arb文件，返回是否写入成功
     */
    private fun writeResult(project: Project, target: TargetArbFile, key: String, valueText: String): Boolean {
        var success = false
        WriteCommandAction.runWriteCommandAction(project) {
            val dummyFile = PsiFileFactory.getInstance(project).createFileFromText(
                "dummy.${JsonFileType.INSTANCE.defaultExtension}",
                JsonFileType.INSTANCE,
                "{\"$key\": $valueText}",
                LocalTimeCounter.currentTime(),
                false
            )

            val property =
                dummyFile.getChildOfType<JsonObject>()?.propertyList?.firstOrNull() ?: return@runWriteCommandAction

            JsonPsiUtil.addProperty(target.jsonObject, property, false)
            val document = target.psiFile.viewProvider.document
            if (document != null) {
                FileDocumentManager.getInstance().saveDocument(document)
            } else {
                FileDocumentManager.getInstance().saveAllDocuments()
            }
            success = true
        }
        return success
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

    private fun getJsonProperty(element: PsiElement?): JsonProperty? {
        if (element == null) {
            return null
        }
        return element.findParentOfType<JsonProperty>(false)
    }

    /**
     * 获取当前选中的json项
     */
    private fun getJsonProperty(e: AnActionEvent): JsonProperty? {
        getJsonProperty(e.getData(CommonDataKeys.PSI_ELEMENT))?.let {
            return it
        }

        // 从光标位置查找，此方式需在EDT线程执行，仅在actionPerformed中生效
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (editor != null && psiFile != null) {
            return getJsonProperty(psiFile.findElementAt(editor.caretModel.offset))
        }

        return null
    }

    private class TargetArbFile(
        val psiFile: PsiFile,
        val jsonObject: JsonObject,
        val language: String,
    )

    companion object {
        private const val TRANSLATE_TEXT = "翻译此项到其它arb文件"
        private const val SYNC_TEXT = "同步此项到其它arb文件"
    }
}
