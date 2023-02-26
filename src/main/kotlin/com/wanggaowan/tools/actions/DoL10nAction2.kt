package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject

/**
 * 执行l10n生成多语言相关代码命令
 *
 * @author Created by wanggaowan on 2023/2/6 15:44
 */
class DoL10nAction2 : DoL10nAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        if (!project.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }
}
