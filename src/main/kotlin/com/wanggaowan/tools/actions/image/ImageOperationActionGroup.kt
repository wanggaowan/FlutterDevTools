package com.wanggaowan.tools.actions.image

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.wanggaowan.tools.Sdk

/**
 * 图片操作分组
 *
 * @author Created by wanggaowan on 2023/8/31 14:51
 */
class ImageOperationActionGroup : DefaultActionGroup() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }

        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (virtualFiles.isNullOrEmpty()) {
            e.presentation.isVisible = false
            return
        }

        val sdk = Sdk.getInstance(project)
        val filePath = virtualFiles[0].path
        if (sdk.getPubRoot(filePath) == null) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }
}
