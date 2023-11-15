package com.wanggaowan.tools.listener

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.wanggaowan.tools.utils.TempFileUtils
import com.wanggaowan.tools.utils.ex.getModules
import com.wanggaowan.tools.utils.ex.isFlutterProject

/**
 * 程序监听器
 */
class ProjectManagerListenerImpl : ProjectManagerListener {
    override fun projectClosed(project: Project) {
        super.projectClosed(project)
        if (project.isDisposed) {
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            project.getModules()?.forEach {
                if (it.isFlutterProject) {
                    TempFileUtils.clearCopyCacheFolder(it)
                }
            }
        }
    }
}
