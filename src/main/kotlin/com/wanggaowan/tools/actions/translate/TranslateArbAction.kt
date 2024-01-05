package com.wanggaowan.tools.actions.translate

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonPsiUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.util.LocalTimeCounter
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.TranslateUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.flutter.YamlUtils
import io.flutter.pub.PubRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.yaml.psi.YAMLKeyValue
import java.io.File

/**
 * 翻译arb文件
 *
 * @author Created by wanggaowan on 2024/1/5 14:48
 */
class TranslateArbAction : DumbAwareAction() {

    private var file: VirtualFile? = null

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        file = null
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }
        if (!project.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file == null || file.isDirectory) {
            e.presentation.isVisible = false
            return
        }

        if (!file.name.lowercase().endsWith(".arb")) {
            e.presentation.isVisible = false
            return
        }

        this.file = file
        e.presentation.isVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val selectedFile = file ?: return
        val project = event.project ?: return
        val pubRoot = PubRoot.forFile(selectedFile) ?: return
        var rootDir = pubRoot.root
        val example = pubRoot.exampleDir
        if (example != null && selectedFile.path.startsWith(example.path)) {
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

        val tempArbPsiFile = arbFile.toPsiFile(project) ?: return
        val arbPsiFile = selectedFile.toPsiFile(project) ?: return
        val tempJsonObject = tempArbPsiFile.getChildOfType<JsonObject>() ?: return
        val jsonObject = arbPsiFile.getChildOfType<JsonObject>()
        val targetLanguage = jsonObject?.findProperty("@@locale")?.value?.text?.replace("\"", "")
        if (targetLanguage == null) {
            NotificationUtils.showBalloonMsg(
                project,
                "未配置arb文件@@locale属性",
                NotificationType.WARNING
            )
            return
        }

        ProgressUtils.runBackground(project,"Translate",true) {progressIndicator->
            progressIndicator.isIndeterminate = false
            ApplicationManager.getApplication().runReadAction {
                val needTranslateMap = mutableMapOf<String, String?>()
                tempJsonObject.propertyList.forEach {
                    val name = it.name
                    val find = jsonObject.findProperty(name)
                    if (find == null) {
                        needTranslateMap[name] = it.value?.text?.replace("\"", "")
                    }
                }

                if (needTranslateMap.isEmpty()) {
                    progressIndicator.fraction = 1.0
                    return@runReadAction
                }

                progressIndicator.fraction = 0.05
                var existTranslateFailed = false
                CoroutineScope(Dispatchers.Default).launch launch2@{
                    var count = 1.0
                    val total = needTranslateMap.size
                    needTranslateMap.forEach { (key, value) ->
                        if (progressIndicator.isCanceled) {
                            return@launch2
                        }

                        var translateStr =
                            if (value.isNullOrEmpty()) value else TranslateUtils.translate(value, targetLanguage)
                        progressIndicator.fraction = count / total * 0.94 + 0.05
                        if (translateStr == null) {
                            existTranslateFailed = true
                            needTranslateMap[key] = null
                        } else {
                            translateStr =
                                TranslateUtils.fixTranslateError(translateStr, targetLanguage, useEscaping)
                            needTranslateMap[key] = translateStr ?: ""
                        }
                        count++
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        WriteCommandAction.runWriteCommandAction(project) {
                            if (progressIndicator.isCanceled) {
                                return@runWriteCommandAction
                            }

                            val document = arbPsiFile.viewProvider.document
                            if (document != null) {
                                PsiDocumentManager.getInstance(project).commitDocument(document)
                            }
                            needTranslateMap.forEach { (key, value) ->
                                if (value != null) {
                                    insertElement(project, arbPsiFile, jsonObject, key, value)
                                }
                            }
                            if (document != null) {
                                PsiDocumentManager.getInstance(project).commitDocument(document)
                            } else {
                                PsiDocumentManager.getInstance(project).commitAllDocuments()
                            }
                            if (existTranslateFailed) {
                                NotificationUtils.showBalloonMsg(
                                    project,
                                    "部分内容未翻译或插入成功，请重试",
                                    NotificationType.WARNING
                                )
                            }
                            progressIndicator.fraction = 1.0
                        }
                    }
                }
            }
        }
    }

    private fun insertElement(
        project: Project,
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
    }
}
