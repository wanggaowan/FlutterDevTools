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
import com.wanggaowan.tools.extensions.toolwindow.resourcePreview.ResourcePreviewToolWindowFactory
import com.wanggaowan.tools.utils.ex.isFlutterProject
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
        if (project.isDisposed) {
            return
        }

        val isFlutterProject = project.isFlutterProject
        if (isFlutterProject) {
            init(project)
            CodeAnalysisService.startAnalysisModules(project, project.modules.toList())
        }

        project.messageBus.connect().subscribe(ModuleListener.TOPIC, object : ModuleListener {
            override fun modulesAdded(project: Project, modules: List<Module>) {
                CodeAnalysisService.startAnalysisModules(project, modules)
                for (module in modules) {
                    if (!toolWindowsInitialized && module.isFlutterProject) {
                        init(project)
                        break
                    }
                }
            }
        })
    }

    private fun init(project: Project) {
        toolWindowsInitialized = true
        SwingUtilities.invokeLater {
            initLater(project)
        }
        CoroutineScope(Dispatchers.Default).launch {
            FileTemplateUtils.initDefaultTemplate()
        }
    }

    private fun initLater(project: Project) {
        val window = ToolWindowManager.getInstance(project).getToolWindow(ResourcePreviewToolWindowFactory.WINDOW_ID)
        if (window != null) {
            window.isAvailable = true
        }
    }
}
