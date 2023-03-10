package com.wanggaowan.tools.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File


/**
 * 复制多个相同名称但是在不同分辨率下的文件
 *
 * @author Created by wanggaowan on 2023/3/6 13:09
 */
class CopyMultiSameNameFileAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val projectRootFolder = VirtualFileManager.getInstance().findFileByUrl("file://${basePath}") ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            var ideaFolder = projectRootFolder.findChild(".idea")
            if (ideaFolder == null) {
                try {
                    ideaFolder = projectRootFolder.createChildDirectory(null, ".idea")
                } catch (e: Exception) {
                    return@runWriteCommandAction
                }
            }
            var copyCacheFolder = ideaFolder.findChild("copyCache")
            if (copyCacheFolder == null) {
                try {
                    copyCacheFolder = ideaFolder.createChildDirectory(null, "copyCache")
                } catch (e: Exception) {
                    return@runWriteCommandAction
                }
            }

            copyCacheFolder.children?.forEach { it.delete(null) }
            val selectFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return@runWriteCommandAction
            if (selectFiles.isEmpty()) {
                return@runWriteCommandAction
            }

            var copyFromFolders: MutableList<String>? = null
            val copyToFolders: MutableList<VirtualFile> = mutableListOf()
            distinctFile(selectFiles).forEach {
                if (!it.isDirectory) {
                    if (copyFromFolders == null) {
                        copyFromFolders = mutableListOf()
                        val parent = if (it.parent.name == "1.5x"
                            || it.parent.name == "2.0x"
                            || it.parent.name == "3.0x"
                            || it.parent.name == "4.0x"
                        ) {
                            it.parent.parent
                        } else {
                            it.parent
                        }

                        parent?.children?.forEach { child ->
                            if (child.name == "1.5x"
                                || child.name == "2.0x"
                                || child.name == "3.0x"
                                || child.name == "4.0x"
                            ) {
                                copyFromFolders?.add(child.name)
                                try {
                                    copyToFolders.add(copyCacheFolder.createChildDirectory(project, child.name))
                                } catch (e: Exception) {
                                    return@runWriteCommandAction
                                }
                            }
                        }
                    }

                    copyFileToCacheFolder(it, copyCacheFolder, copyFromFolders, copyToFolders)
                }
            }

            val needCopyFile = mutableListOf<File>()
            copyCacheFolder.children?.forEach {
                if (!it.isDirectory || (it.children != null && it.children.isNotEmpty())) {
                    needCopyFile.add(File(it.path))
                }
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(FileTransferable(needCopyFile), null)
            NotificationUtils.showBalloonMsg(project, "已复制到剪切板", NotificationType.INFORMATION)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project?.isFlutterProject != true) {
            e.presentation.isVisible = false
            return
        }

        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (virtualFiles.isNullOrEmpty()) {
            e.presentation.isVisible = false
            return
        }

        for (file in virtualFiles) {
            if (!file.isDirectory) {
                e.presentation.isVisible = true
                return
            }
        }

        e.presentation.isVisible = false
    }

    /**
     * 复制需要粘贴的文件到临时的缓存目录
     */
    private fun copyFileToCacheFolder(
        file: VirtualFile,
        copyToFolderParent: VirtualFile,
        copyFromFolderList: List<String>?,
        copyToFolderList: List<VirtualFile>
    ) {
        if (copyFromFolderList.isNullOrEmpty()) {
            return
        }

        val parentPath = if (file.parent.name == "1.5x"
            || file.parent.name == "2.0x"
            || file.parent.name == "3.0x"
            || file.parent.name == "4.0x"
        ) {
            file.parent.parent.path
        } else {
            file.parent.path
        }

        val fileName = file.name
        var child = VirtualFileManager.getInstance().findFileByUrl("file://${parentPath}/$fileName")
        child?.copy(null, copyToFolderParent, fileName)
        copyFromFolderList.indices.forEach {
            val folderName = copyFromFolderList[it]
            child = VirtualFileManager.getInstance().findFileByUrl("file://${parentPath}/$folderName/$fileName")
            child?.copy(null, copyToFolderList[it], fileName)
        }
    }

    /**
     * 去除重复的数据
     */
    private fun distinctFile(array: Array<VirtualFile>): List<VirtualFile> {
        val list = mutableListOf<VirtualFile>()
        array.forEach {
            val exist = list.find { file -> file.name == it.name }
            if (exist == null) {
                list.add(it)
            }
        }
        return list
    }
}

/**
 * 复制的文件数据
 */
class FileTransferable(private val files: List<*>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return arrayOf(DataFlavor.javaFileListFlavor)
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
        return DataFlavor.javaFileListFlavor.equals(flavor)
    }

    override fun getTransferData(flavor: DataFlavor?): Any {
        return if (!DataFlavor.javaFileListFlavor.equals(flavor)) {
            throw UnsupportedFlavorException(flavor)
        } else {
            files
        }
    }
}
