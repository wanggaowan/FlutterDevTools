package com.wanggaowan.tools.actions.filetemplate

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.jetbrains.lang.dart.DartFileType
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

/**
 * 创建模版文件
 *
 * @author Created by wanggaowan on 2023/9/4 13:12
 */
class CreateFileTemplateAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val module = e.getData(LangDataKeys.MODULE)
        if (module == null) {
            e.presentation.isVisible = false
            return
        }

        if (!module.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile == null) {
            e.presentation.isVisible = false
            return
        }

        if (!virtualFile.isDirectory && virtualFile.parent?.isDirectory != true) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val dialog = CreateFileTemplateDialog(project)
        dialog.show()
        if (dialog.exitCode != DialogWrapper.OK_EXIT_CODE) {
            justSaveTemplateChange(project, dialog)
            return
        }

        ProgressUtils.runBackground(project, "create file template") { progressIndicator ->
            progressIndicator.isIndeterminate = true
            WriteCommandAction.runWriteCommandAction(project) {
                if (dialog.dataChange) {
                    FileTemplateUtils.saveTemplateList(dialog.templateData)
                }

                val template = dialog.selectTemplate ?: return@runWriteCommandAction
                val children = template.children
                if (children.isNullOrEmpty()) {
                    return@runWriteCommandAction
                }

                var virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return@runWriteCommandAction
                if (!virtualFile.isDirectory) {
                    virtualFile = virtualFile.parent
                }

                dialog.placeholderMap.keys.forEach {
                    when (it) {
                        "${'$'}DATE${'$'}" -> {
                            dialog.placeholderMap[it] = SimpleDateFormat("yyyy-MM-dd").format(Date())
                        }

                        "${'$'}TIME${'$'}" -> {
                            dialog.placeholderMap[it] = SimpleDateFormat("HH:mm").format(Date())
                        }

                        "${'$'}DATETIME${'$'}" -> {
                            dialog.placeholderMap[it] = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
                        }

                        "${'$'}USER${'$'}" -> {
                            dialog.placeholderMap[it] = System.getenv("USER") ?: ""
                        }
                    }
                }

                try {
                    children.forEach {
                        var content = it.tempContent ?: it.content ?: ""
                        if (content.isNotEmpty()) {
                            dialog.placeholderMap.forEach { map ->
                                content = content.replace(map.key, map.value)
                            }
                        }

                        val file =
                            File("${virtualFile.path}/${it.name!!}.${DartFileType.INSTANCE.defaultExtension}")
                        if (!file.exists()) {
                            file.createNewFile()
                            val fw = FileWriter(file.absoluteFile, Charset.forName("UTF-8"))
                            val bw = BufferedWriter(fw)
                            bw.write(content)
                            bw.close()
                        } else {
                            throw RuntimeException("${file.path} already exist")
                        }

                        // 采用以下方式创建，创建的文件dart语法解析不会主动触发，不明白缘由
                        // val psiParent = virtualFile.toPsiDirectory(project)
                        // val file = DartPsiUtils.createFile(project, it.name!!, content)
                        // file?.also { child ->
                        //     psiParent.add(child)
                        // }
                    }
                    virtualFile.refresh(false, false)
                } catch (e: Exception) {
                    NotificationUtils.showBalloonMsg(project, e.message ?: "文件创建失败", NotificationType.ERROR)
                }
            }
            progressIndicator.isIndeterminate = false
            progressIndicator.fraction = 1.0
        }
    }

    private fun justSaveTemplateChange(project: Project, dialog: CreateFileTemplateDialog) {
        WriteCommandAction.runWriteCommandAction(project) {
            if (dialog.dataChange) {
                FileTemplateUtils.saveTemplateList(dialog.templateData)
            }
        }
    }
}
