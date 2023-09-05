package com.wanggaowan.tools.actions.filetemplate

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.wanggaowan.tools.actions.filetemplate.template.Page
import com.wanggaowan.tools.actions.filetemplate.template.SimplePage
import com.wanggaowan.tools.utils.PropertiesSerializeUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject

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
            return
        }

        var virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)?:return
        if (!virtualFile.isDirectory) {
            virtualFile = virtualFile.parent
        }
    }
}
