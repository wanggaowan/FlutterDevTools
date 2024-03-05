package com.wanggaowan.tools.actions.image

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.TempFileUtils
import com.wanggaowan.tools.utils.XUtils
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File


/**
 * 复制多个相同名称但是在不同分辨率下的图片
 *
 * @author Created by wanggaowan on 2023/3/6 13:09
 */
class CopyMultiSameNameImageAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val module = e.getData(LangDataKeys.MODULE) ?: return
        val project = module.project

        WriteCommandAction.runWriteCommandAction(project) {
            val copyCacheFolder = TempFileUtils.getCopyCacheFolder(module) ?: return@runWriteCommandAction
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
                        val parent = if (XUtils.isImageVariantsFolder(it.parent.name)) {
                            it.parent.parent
                        } else {
                            it.parent
                        }

                        parent?.children?.forEach { child ->
                            if (XUtils.isImageVariantsFolder(child.name)) {
                                copyFromFolders?.add(child.name)
                                try {
                                    copyToFolders.add(copyCacheFolder.createChildDirectory(module, child.name))
                                } catch (e: Exception) {
                                    return@runWriteCommandAction
                                }
                            }
                        }
                    }

                    copyFileToCacheFolder(module, it, copyCacheFolder, copyFromFolders, copyToFolders)
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

    /**
     * 复制需要粘贴的文件到临时的缓存目录
     */
    private fun copyFileToCacheFolder(
        module: Module,
        file: VirtualFile,
        copyToFolderParent: VirtualFile,
        copyFromFolderList: List<String>?,
        copyToFolderList: List<VirtualFile>
    ) {
        if (copyFromFolderList.isNullOrEmpty()) {
            return
        }

        val parentPath = if (XUtils.isImageVariantsFolder(file.parent.name)) {
            file.parent.parent.path
        } else {
            file.parent.path
        }

        val fileName = file.name
        var child = VirtualFileManager.getInstance().findFileByUrl("file://${parentPath}/$fileName")
        child?.copy(module, copyToFolderParent, fileName)
        copyFromFolderList.indices.forEach {
            val folderName = copyFromFolderList[it]
            child = VirtualFileManager.getInstance().findFileByUrl("file://${parentPath}/$folderName/$fileName")
            child?.copy(module, copyToFolderList[it], fileName)
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
