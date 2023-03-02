package com.wanggaowan.tools.listener

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * 程序监听器
 */
class ProjectManagerListenerImpl : ProjectManagerListener {

    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun projectOpened(project: Project) {
        ProjectManagerListenerImpl.project = project
    }

    override fun projectClosed(project: Project) {
        super.projectClosed(project)
        ProjectManagerListenerImpl.project = null
    }

    companion object {
        var project: Project? = null
    }
}
