package com.wanggaowan.tools.actions.log

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.CommitMessage

/**
 * 存储Git提交的每日日志
 *
 * @author Created by wanggaowan on 2024/6/26 下午2:17
 */
class SaveGitCommitLogAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val projectName = event.project?.name ?: ""

        val var2 = event.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as CommitMessage
        val text = var2.editorField.text
        LogUtils.save(projectName, text)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
