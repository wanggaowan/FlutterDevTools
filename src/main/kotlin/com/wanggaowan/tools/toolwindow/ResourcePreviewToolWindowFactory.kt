package com.wanggaowan.tools.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.wanggaowan.tools.ui.ImagePreviewPanel
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
        val manager = toolWindow.contentManager
        val panel = ImagePreviewPanel(project)
        val content: Content = manager.factory.createContent(panel, "", false)
        manager.addContent(content)
    }
}
