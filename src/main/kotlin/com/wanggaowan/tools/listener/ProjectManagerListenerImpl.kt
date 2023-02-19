package com.wanggaowan.tools.listener

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * 程序监听器
 */
class ProjectManagerListenerImpl : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        super.projectOpened(project)
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
