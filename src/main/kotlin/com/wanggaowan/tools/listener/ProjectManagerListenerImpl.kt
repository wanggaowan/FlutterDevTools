package com.wanggaowan.tools.listener

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.wanggaowan.tools.utils.ex.isFlutterProjectMap

/**
 * 程序监听器
 */
class ProjectManagerListenerImpl : ProjectManagerListener {
    override fun projectClosed(project: Project) {
        super.projectClosed(project)
        isFlutterProjectMap.remove(project)
    }
}
