package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.wanggaowan.tools.utils.ex.isFlutterProject

/**
 * 根据项目中DART类生成对应的.g.dart文件
 *
 * @author Created by wanggaowan on 2023/2/6 15:44
 */
class GeneratorGFileAction2 : GeneratorGFileAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        if (!project.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }

    override fun onCommandEnd(context: DataContext) {
        context.getData(CommonDataKeys.VIRTUAL_FILE)?.parent?.refresh(true, false)
    }
}
