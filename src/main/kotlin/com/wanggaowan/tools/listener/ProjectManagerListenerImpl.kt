package com.wanggaowan.tools.listener

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * 程序监听器
 */
class ProjectManagerListenerImpl : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        super.projectOpened(project)
        ProjectManagerListenerImpl.project = project
        // EditorFactory.getInstance().eventMulticaster.addDocumentListener(VirtualFileListenerImpl(),object :Disposable {
        //     override fun dispose() {
        //     }
        // })
    }

    override fun projectClosed(project: Project) {
        super.projectClosed(project)
        ProjectManagerListenerImpl.project = null
    }

    companion object {
        var project: Project? = null
    }
}
