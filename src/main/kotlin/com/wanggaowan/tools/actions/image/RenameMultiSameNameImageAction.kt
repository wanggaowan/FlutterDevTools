package com.wanggaowan.tools.actions.image

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.wanggaowan.tools.ui.ImagesRenameDialog
import com.wanggaowan.tools.ui.RenameEntity
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.XUtils


/**
 * 重命令多个相同名称但是在不同分辨率下的文件
 *
 * @author Created by wanggaowan on 2023/5/25 10:40
 */
class RenameMultiSameNameImageAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val module = e.getData(LangDataKeys.MODULE) ?: return
        val project = module.project
        val selectFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        if (selectFiles.isEmpty()) {
            return
        }

        val distinctList = distinctFile(selectFiles)
        val dialog = ImagesRenameDialog(project, distinctList)
        dialog.setOkActionListener {
            val fileList = dialog.getRenameFileList()
            rename(project, fileList)
        }
        dialog.isVisible = true
    }

    /**
     * 去除重复的数据
     */
    private fun distinctFile(array: Array<VirtualFile>): List<VirtualFile> {
        val list = mutableListOf<VirtualFile>()
        array.forEach {
            if (!it.isDirectory) {
                val exist = list.find { file -> file.name == it.name }
                if (exist == null) {
                    list.add(it)
                }
            }
        }
        return list
    }

    private fun rename(project: Project, data: List<RenameEntity>) {
        ProgressUtils.runBackground(project,"images rename") {progressIndicator->
            progressIndicator.isIndeterminate = true
            WriteCommandAction.runWriteCommandAction(project) {
                data.forEach {
                    if (it.oldName.isNotEmpty() && it.newName.isNotEmpty()
                        && it.oldName != it.newName
                        && (!it.existFile || it.coverExistFile)
                    ) {
                        val parentName = it.oldFile.parent.name
                        val parent = if (XUtils.isImageVariantsFolder(parentName)) {
                            it.oldFile.parent.parent
                        } else {
                            it.oldFile.parent
                        }

                        parent.findChild(it.oldName)?.also { file ->
                            file.rename(project, it.newName)
                        }

                        parent.findChild("1.5x")?.findChild(it.oldName)?.also { file ->
                            file.rename(project, it.newName)
                        }

                        parent.findChild("2.0x")?.findChild(it.oldName)?.also { file ->
                            file.rename(project, it.newName)
                        }

                        parent.findChild("3.0x")?.findChild(it.oldName)?.also { file ->
                            file.rename(project, it.newName)
                        }

                        parent.findChild("4.0x")?.findChild(it.oldName)?.also { file ->
                            file.rename(project, it.newName)
                        }
                    }
                }

                NotificationUtils.showBalloonMsg(project, "图片已重命名", NotificationType.INFORMATION)
            }

            progressIndicator.isIndeterminate = false
            progressIndicator.fraction = 1.0
        }
    }
}
