package com.wanggaowan.tools

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.vcs.commit.*
import com.wanggaowan.tools.actions.filetemplate.FileTemplateUtils
import com.wanggaowan.tools.actions.log.SaveGitCommitLogAction
import com.wanggaowan.tools.actions.replace.ReplaceGitCommitAndPushExecutorAction
import com.wanggaowan.tools.extensions.complete.CodeAnalysisService
import com.wanggaowan.tools.extensions.toolwindow.resourcePreview.ResourcePreviewToolWindowFactory
import com.wanggaowan.tools.utils.ex.isFlutterProject
import io.flutter.bazel.WorkspaceCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.SwingUtilities


/**
 * 项目打开监听
 *
 * @author Created by wanggaowan on 2023/3/6 22:03
 */
class ProjectOpenActivity : ProjectActivity, DumbAware {

    private var toolWindowsInitialized = false

    override suspend fun execute(project: Project) {
        // 项目打开
        val isFlutterProject = project.isFlutterProject
        if (isFlutterProject || WorkspaceCache.getInstance(project).isBazel) {
            SwingUtilities.invokeLater {
                init(project)
            }
            CodeAnalysisService.startAnalysisModules(project, project.modules.toList())
        } else {
            project.messageBus.connect().subscribe(ModuleListener.TOPIC, object : ModuleListener {
                override fun modulesAdded(project: Project, modules: List<Module>) {
                    for (module in modules) {
                        if (!toolWindowsInitialized && module.isFlutterProject) {
                            toolWindowsInitialized = true
                            SwingUtilities.invokeLater {
                                init(project)
                            }
                            break
                        }
                    }
                    CodeAnalysisService.startAnalysisModules(project, modules)
                }
            })
        }

        disposeGitCommit(project)

        if (isFlutterProject) {
            CoroutineScope(Dispatchers.Default).launch {
                FileTemplateUtils.initDefaultTemplate()
            }
        }
    }

    private fun init(project: Project) {
        val window = ToolWindowManager.getInstance(project).getToolWindow(ResourcePreviewToolWindowFactory.WINDOW_ID)
        if (window != null) {
            window.isAvailable = true
        }
    }

    private fun disposeGitCommit(project: Project) {
        // 监听git commit按钮点击
        project.messageBus.connect().subscribe(ChangesViewWorkflowManager.TOPIC,
            ChangesViewWorkflowManager.ChangesViewWorkflowListener {
                val newWorkflowHandler: NonModalCommitWorkflowHandler<ChangesViewCommitWorkflow, ChangesViewCommitWorkflowUi>? =
                    ChangesViewWorkflowManager.getInstance(project).commitWorkflowHandler
                val ui = newWorkflowHandler?.ui
                if (ui is ChangesViewCommitPanel) {
                    ui.commitActionsPanel.addExecutorListener(object : CommitExecutorListener {
                        override fun executorCalled(executor: CommitExecutor?) {
                            val context = DataManager.getInstance().getDataContext(ui)
                            // 此处存在一个问题，就是无论commit成功还是失败，都会被记录
                            SaveGitCommitLogAction.saveLog(context)
                        }
                    }) { }
                }
            })

        // 替换git commit and push action
        ActionManager.getInstance().replaceAction("Git.Commit.And.Push.Executor",
            ReplaceGitCommitAndPushExecutorAction(project))
    }
}
