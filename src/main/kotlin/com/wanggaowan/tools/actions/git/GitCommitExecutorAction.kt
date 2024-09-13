package com.wanggaowan.tools.actions.git

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.openapi.vcs.changes.actions.BaseCommitExecutorAction
import com.intellij.vcs.commit.CommitWorkflowHandler
import com.intellij.vcs.commit.commitExecutorProperty
import com.wanggaowan.tools.actions.log.SaveGitCommitLogAction

/**
 * 实现系统Commit功能并将当前提交message记录到每日工作日志
 *
 * @author Created by wanggaowan on 2024/9/13 下午2:50
 */
class GitCommitExecutorAction : BaseCommitExecutorAction() {
    override val executorId: String = "Git.Commit.Executor"

    override fun update(e: AnActionEvent) {
        val workflowHandler = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER)
        e.presentation.isVisible = workflowHandler != null
        e.presentation.isEnabled = workflowHandler != null
    }

    override fun getCommitExecutor(handler: CommitWorkflowHandler): CommitExecutor {
        return GitCommitExecutor()
    }

    override fun actionPerformed(e: AnActionEvent) {
        super.actionPerformed(e)
        // 此处存在一个问题，就是无论commit成功还是失败，都会被记录
        SaveGitCommitLogAction.saveLog(e.dataContext)
    }
}

private val key = Key.create<Boolean>("Git.Commit.IsPushAfterCommit")

// 是否提交后执行推送
private val CommitContext.isPushAfterCommit: Boolean by commitExecutorProperty(key)

internal class GitCommitExecutor : CommitExecutor {
    override fun getActionText(): String = "Commit"

    override fun useDefaultAction(): Boolean = false

    override fun requiresSyncCommitChecks(): Boolean = true

    override fun getId(): String = ID

    override fun supportsPartialCommit(): Boolean = true

    override fun createCommitSession(commitContext: CommitContext): CommitSession {
        return CommitSession.VCS_COMMIT
    }

    companion object {
        internal const val ID = "Git.Commit.Executor"
    }
}
