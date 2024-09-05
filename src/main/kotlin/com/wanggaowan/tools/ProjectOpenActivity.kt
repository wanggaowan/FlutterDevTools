package com.wanggaowan.tools

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.wanggaowan.tools.actions.filetemplate.FileTemplateUtils
import com.wanggaowan.tools.extensions.complete.CodeAnalysisService
import com.wanggaowan.tools.extensions.toolwindow.ResourcePreviewToolWindowFactory
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
}
