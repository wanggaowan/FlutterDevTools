package com.wanggaowan.tools

import com.intellij.ProjectTopics
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.startup.StartupActivity
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
class ProjectOpenActivity : StartupActivity, DumbAware {

    private var toolWindowsInitialized = false

    override fun runActivity(project: Project) {
        // 项目打开
        val isFlutterProject = project.isFlutterProject
        if (isFlutterProject || WorkspaceCache.getInstance(project).isBazel) {
            SwingUtilities.invokeLater {
                ResourcePreviewToolWindowFactory.init(project)
            }
            CodeAnalysisService.startAnalysisModules(project, project.modules.toList())
        } else {
            project.messageBus.connect().subscribe(ProjectTopics.MODULES, object : ModuleListener {
                override fun modulesAdded(project: Project, modules: List<Module>) {
                    for (module in modules) {
                        if (!toolWindowsInitialized && module.isFlutterProject) {
                            toolWindowsInitialized = true
                            SwingUtilities.invokeLater {
                                ResourcePreviewToolWindowFactory.init(project)
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
}
