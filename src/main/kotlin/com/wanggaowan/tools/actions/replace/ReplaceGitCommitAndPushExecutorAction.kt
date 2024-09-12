package com.wanggaowan.tools.actions.replace

import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.wanggaowan.tools.actions.log.SaveGitCommitLogAction

/**
 * 替换系统默认的GitCommitAndPushExecutorAction
 *
 * @author Created by wanggaowan on 2024/9/11 下午4:43
 */
class ReplaceGitCommitAndPushExecutorAction(val project: Project) :
    com.intellij.openapi.vcs.changes.actions.BaseCommitExecutorAction() {
    override val executorId: String

    init {
        templatePresentation.setText(DvcsBundle.messagePointer("action.commit.and.push.text"))
        this.executorId = "Git.Commit.And.Push.Executor"
    }

    override fun actionPerformed(e: AnActionEvent) {
        super.actionPerformed(e)
        // 此处存在一个问题，就是无论commit成功还是失败，都会被记录
        SaveGitCommitLogAction.saveLog(e.dataContext)
    }
}
