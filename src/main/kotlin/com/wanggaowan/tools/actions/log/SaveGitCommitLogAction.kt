package com.wanggaowan.tools.actions.log

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogDataKeys
import com.wanggaowan.tools.utils.NotificationUtils
import org.jetbrains.kotlin.idea.refactoring.project

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
        saveLog(event.dataContext)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    companion object {
        fun saveLog(event: DataContext) {
            val project = event.project
            val projectName = project.name

            val var2 = event.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as CommitMessage?
            if (var2 != null) {
                val text = var2.editorField.text
                LogUtils.save(projectName, text)
                return
            }

            val logProviders = event.getData(VcsLogDataKeys.VCS_LOG)?.logProviders
            if (logProviders.isNullOrEmpty()) {
                NotificationUtils.showBalloonMsg(project, "日志记录失败", NotificationType.ERROR)
                return
            }

            val commits = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)?.commits
            if (commits.isNullOrEmpty()) {
                NotificationUtils.showBalloonMsg(project, "日志记录失败", NotificationType.ERROR)
                return
            }

            ApplicationManager.getApplication().executeOnPooledThread {
                val hashesMap: MutableMap<VirtualFile, MutableList<String>> = mutableMapOf()
                commits.forEach {
                    var list = hashesMap[it.root]
                    if (list == null) {
                        list = mutableListOf()
                        hashesMap[it.root] = list
                    }
                    list.add(it.hash.asString())
                }

                hashesMap.forEach {
                    val provider = logProviders[it.key] ?: return@forEach
                    try {
                        provider.readMetadata(it.key, it.value) { logData ->
                            val message = logData.fullMessage
                            LogUtils.save(projectName, message)
                        }
                    } catch (_: Exception) {
                        NotificationUtils.showBalloonMsg(project, "日志记录过程发生异常", NotificationType.ERROR)
                        return@forEach
                    }
                }
            }
        }
    }
}


