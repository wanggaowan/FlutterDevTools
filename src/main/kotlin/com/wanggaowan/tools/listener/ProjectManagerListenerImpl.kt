package com.wanggaowan.tools.listener

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * 程序监听器
 */
class ProjectManagerListenerImpl : ProjectManagerListener {
    override fun projectClosed(project: Project) {
        super.projectClosed(project)
        if (project.isDisposed) {
            return
        }

        val basePath = project.basePath ?: return
        val projectRootFolder = VirtualFileManager.getInstance().findFileByUrl("file://${basePath}")
            ?: return
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                val ideaFolder = projectRootFolder.findChild(".idea")
                    ?: return@runWriteCommandAction
                val copyCacheFolder = ideaFolder.findChild("copyCache")
                    ?: return@runWriteCommandAction
                copyCacheFolder.children?.forEach {
                    if (it.exists()) {
                        it.delete(null)
                    }
                }
            }
        } catch (e: Exception) {
            //
        }
    }
}
