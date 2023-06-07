package com.wanggaowan.tools

import com.intellij.ProjectTopics
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.wanggaowan.tools.extensions.toolwindow.ResourcePreviewToolWindowFactory
import com.wanggaowan.tools.utils.ex.isFlutterProject
import io.flutter.bazel.WorkspaceCache
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
        if (project.isFlutterProject || WorkspaceCache.getInstance(project).isBazel) {
            SwingUtilities.invokeLater {
                ResourcePreviewToolWindowFactory.init(project)
            }
        } else {
            project.messageBus.connect().subscribe(ProjectTopics.MODULES, object : ModuleListener {
                override fun moduleAdded(project: Project, module: Module) {
                    if (!toolWindowsInitialized && module.isFlutterProject) {
                        toolWindowsInitialized = true
                        SwingUtilities.invokeLater {
                            ResourcePreviewToolWindowFactory.init(project)
                        }
                    }
                }
            })
        }
    }
}
