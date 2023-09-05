package com.wanggaowan.tools.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.wanggaowan.tools.utils.NotificationUtils

/**
 * 删除多个相同名称但在不同分辨率下的文件
 *
 * @author Created by wanggaowan on 2023/3/19 18:06
 */
class DeleteMultiSameNameFileAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val module = e.getData(LangDataKeys.MODULE) ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (files.isNullOrEmpty()) {
            return
        }

        WriteCommandAction.runWriteCommandAction(module.project) {
            for (file in files) {
                deleteFile(module, file)
            }
            NotificationUtils.showBalloonMsg(module.project, "已删除", NotificationType.INFORMATION)
        }
    }

    private fun deleteFile(requestor: Any, file: VirtualFile) {
        var parent = file.parent
        file.delete(requestor)
        if (parent != null) {
            val name = parent.name
            if (isValidDir(name)) {
                parent = parent.parent
            }
            parent?.children?.forEach {
                if (!it.isDirectory) {
                    if (it.name == file.name) {
                        it.delete(requestor)
                    }
                } else if (isValidDir(it.name)) {
                    it.children.forEach { child ->
                        if (!child.isDirectory && child.name == file.name) {
                            child.delete(requestor)
                        }
                    }
                }
            }
        }
    }

    private fun isValidDir(name: String): Boolean {
        return name == "1.5x" || name == "2.0x" || name == "3.0x" || name == "4.0x"
    }
}
