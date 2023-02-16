package com.wanggaowan.tools.actions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import java.io.File

/**
 * 生成图片资源引用，并在pubspec.yaml中生成图片位置声明
 *
 * @author Created by wanggaowan on 2023/2/16 20:33
 */
class GeneratorImageRefAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = getEventProject(e) ?: return
        val basePath = project.basePath ?: return
        val virtualFileManager = VirtualFileManager.getInstance()
        val projectFile = virtualFileManager.findFileByUrl("file://${basePath}") ?: return
        val imagesDir = virtualFileManager.findFileByUrl("file://${basePath}/assets/images") ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "GsonFormat") {
            override fun run(progressIndicator: ProgressIndicator) {
                progressIndicator.isIndeterminate = true
                WriteCommandAction.runWriteCommandAction(project) {
                    val imagesFile = findOrCreateResourcesDir(project,projectFile)
                    val pubspec = findOrCreatePubspec(project,projectFile)
                    val dirs = mutableListOf<VirtualFile>()
                    imagesDir.children.forEach {
                        if (it.isDirectory) {
                            dirs.add(it)
                        } else {

                        }
                    }

                    progressIndicator.isIndeterminate = false
                    progressIndicator.fraction = 1.0
                }
            }
        })
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    /**
     * 查找或创建/lib/resources/images.dart目录
     */
    private fun findOrCreateResourcesDir(project: Project, parent: VirtualFile): VirtualFile {
        var virtualFile = parent.findChild("lib")
        var newCreate = false
        if (virtualFile == null) {
            virtualFile = parent.createChildData(project, "lib")
            newCreate = true
        }

        if (newCreate) {
            virtualFile = virtualFile.createChildData(project, "resources")
        } else {
            val child = virtualFile.findChild("resources")
            if (child == null) {
                virtualFile = virtualFile.createChildData(project, "resources")
                newCreate = true
            }
        }

        if (newCreate) {
            virtualFile = virtualFile.createChildData(project, "images.dart")
        } else {
            val child = virtualFile.findChild("images.dart")
            if (child == null) {
                virtualFile = virtualFile.createChildData(project, "images.dart")
            }
        }

        return virtualFile
    }

    /**
     * 查找或创建pubspec.yaml文件
     */
    private fun findOrCreatePubspec(project: Project, parent: VirtualFile): VirtualFile {
        var virtualFile = parent.findChild("pubspec.yaml")
        if (virtualFile == null) {
            virtualFile = parent.createChildData(project, "pubspec.yaml")
        }
        return virtualFile
    }
}
