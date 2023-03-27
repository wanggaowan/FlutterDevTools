package com.wanggaowan.tools.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.wanggaowan.tools.ui.ImagePreviewPanel
import com.wanggaowan.tools.utils.ex.flutterModules


/**
 * 资源文件预览，目前仅支持预览图片
 *
 * @author Created by wanggaowan on 2022/6/17 13:09
 */
class ResourcePreviewToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        DumbService.getInstance(project).runWhenSmart {
            if (toolWindow.isDisposed) {
                return@runWhenSmart
            }

            val modules = project.flutterModules
            if (modules.isNullOrEmpty()) {
                return@runWhenSmart
            }

            val manager = toolWindow.contentManager
            if (modules.size == 1) {
                val panel = ImagePreviewPanel(modules[0])
                val content: Content = manager.factory.createContent(panel, "", false)
                manager.addContent(content)
                return@runWhenSmart
            }

            modules.forEach {
                val panel = ImagePreviewPanel(it)
                val content: Content = manager.factory.createContent(panel, it.name, false)
                manager.addContent(content)
            }
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return false
    }

    companion object {
        fun init(project: Project) {
            val window = ToolWindowManager.getInstance(project).getToolWindow("Flutter Resource Manager")
            if (window != null) {
                window.isAvailable = true
            }
        }
    }
}
