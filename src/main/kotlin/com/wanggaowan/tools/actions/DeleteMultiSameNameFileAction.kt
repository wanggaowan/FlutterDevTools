package com.wanggaowan.tools.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.isFlutterProject

/**
 * 删除多个相同名称但在不同分辨率下的文件
 *
 * @author Created by wanggaowan on 2023/3/19 18:06
 */
class DeleteMultiSameNameFileAction : DumbAwareAction() {

    // override fun getActionUpdateThread(): ActionUpdateThread {
    //     return ActionUpdateThread.BGT
    // }

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

        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (virtualFiles.isNullOrEmpty()) {
            e.presentation.isVisible = false
            return
        }

        val basePath = module.basePath
        val imageDir = PluginSettings.getImagesFileDir(module.project)
        if (!virtualFiles[0].path.startsWith("$basePath/$imageDir") && !virtualFiles[0].path.startsWith("$basePath/example/$imageDir")) {
            e.presentation.isVisible = false
            return
        }

        for (file in virtualFiles) {
            if (file.isDirectory) {
                e.presentation.isVisible = false
                return
            }
        }

        e.presentation.isVisible = true
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