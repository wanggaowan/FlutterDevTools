package com.wanggaowan.tools.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.wanggaowan.tools.ui.ImagePreviewPanel
import com.wanggaowan.tools.utils.ex.flutterModules
import com.wanggaowan.tools.utils.ex.isFlutterProject


/**
 * 资源文件预览，目前仅支持预览图片
 *
 * @author Created by wanggaowan on 2022/6/17 13:09
 */
class ResourcePreviewToolWindowFactory : ToolWindowFactory {

    override fun isApplicable(project: Project): Boolean {
        return project.isFlutterProject
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (toolWindow.isDisposed) {
            return
        }

        val modules = project.flutterModules
        if (modules.isNullOrEmpty()) {
            return
        }

        val manager = toolWindow.contentManager
        if (modules.size == 1) {
            val panel = ImagePreviewPanel(modules[0])
            val content: Content = manager.factory.createContent(panel, "", false)
            manager.addContent(content)
            return
        }

        modules.forEach {
            val panel = ImagePreviewPanel(it)
            val content: Content = manager.factory.createContent(panel, it.name, false)
            manager.addContent(content)
        }
    }
}
