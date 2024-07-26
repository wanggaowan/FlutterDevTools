package com.wanggaowan.tools.listener

import com.intellij.ide.PasteProvider
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.wanggaowan.tools.actions.image.ImportSameImageResUtils
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.refactoring.project
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private var files: List<VirtualFile>? = null
private var importToFile: VirtualFile? = null

/**
 * 监听导入不同分辨率相同图片资源动作，满足条件则触发导入
 *
 * @author Created by wanggaowan on 2023/2/26 14:30
 */
class ImportSameImageListener : MoveHandlerDelegate(), PasteProvider {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    // <editor-fold desc="复制粘贴监听">
    override fun performPaste(dataContext: DataContext) {
        files?.also {
            ImportSameImageResUtils.import(dataContext.project, it, importToFile)
        }
        importToFile = null
        files = null
    }

    override fun isPastePossible(dataContext: DataContext): Boolean {
        return false
    }

    override fun isPasteEnabled(dataContext: DataContext): Boolean {
        val module = dataContext.getData(LangDataKeys.MODULE) ?: return false
        if (!module.isFlutterProject) {
            return false
        }

        if (!FileCopyPasteUtil.isFileListFlavorAvailable()) {
            return false
        }

        val contents = CopyPasteManager.getInstance().contents ?: return false
        val files = FileCopyPasteUtil.getFileList(contents)
        if (files.isNullOrEmpty()) {
            return false
        }
        return canImport(files.iterator(), dataContext.getData(LangDataKeys.PASTE_TARGET_PSI_ELEMENT))
    }
    // </editor-fold>

    private fun <T> canImport(iterator: Iterator<T>, targetElement: PsiElement?): Boolean {
        importToFile = null
        if (targetElement == null) {
            return false
        }

        if (targetElement is PsiDirectory) {
            val module = targetElement.module
            if (module != null) {
                val targetVirtualFile = targetElement.virtualFile
                val filePath = targetVirtualFile.path
                val imageRootPath = if (filePath.contains("/example/")) {
                    "${module.basePath}/${PluginSettings.getExampleImagesFileDir(module.project)}"
                } else {
                    "${module.basePath}/${PluginSettings.getImagesFileDir(module.project)}"
                }
                if (filePath.startsWith(imageRootPath)) {
                    importToFile = targetVirtualFile
                }
            }
        }

        if (importToFile == null) {
            return false
        }

        val virtualFiles = mutableListOf<VirtualFile>()
        for (file in iterator) {
            val isFile = file is File
            val isPsiFile = file is PsiFileSystemItem
            if (!isFile && !isPsiFile) {
                return false
            }

            val fileName = if (isFile) (file as File).name else (file as PsiFileSystemItem).name
            if (fileName.startsWith(".")) {
                // 隐藏文件忽略
                continue
            }

            val isDirectory = if (isFile) (file as File).isDirectory else (file as PsiFileSystemItem).isDirectory
            var isValidZipFile = false
            var isImage = false
            if (!isDirectory) {
                if (fileName.lowercase().endsWith(".zip")) {
                    val path = if (isFile) (file as File).path else (file as PsiFileSystemItem).virtualFile.path
                    val zipFile = ZipFile(path)
                    val entries = zipFile.entries()
                    isValidZipFile = zipFile.size() > 0
                    while (entries.hasMoreElements()) {
                        val zipEntry: ZipEntry = entries.nextElement() as ZipEntry
                        val zipEntryName: String = zipEntry.name.lowercase()
                        if (zipEntryName.startsWith(".")) {
                            continue
                        }

                        if (zipEntry.isDirectory) {
                            if (!zipEntryName.startsWith("__macosx")
                                && !zipEntryName.startsWith("mipmap")
                                && !zipEntryName.startsWith("drawable")) {
                                isValidZipFile = false
                                break
                            }
                        } else if (!XUtils.isImage(zipEntryName)) {
                            isValidZipFile = false
                            break
                        }
                    }
                } else {
                    isImage = XUtils.isImage(fileName)
                }
            }

            if (!isDirectory && !isValidZipFile && !isImage) {
                /// 不是目录且不是符合规范的压缩文件及不是图片则不处理
                return false
            }

            val virtualFile = if (isFile) {
                VirtualFileManager.getInstance().findFileByUrl("file://${(file as File).absolutePath}")
            } else {
                (file as PsiFileSystemItem).virtualFile
            }

            if (virtualFile == null) {
                // 只要导入的文件存在一个无法处理的文件则不拦截
                return false
            }

            if (isImage || isValidZipFile) {
                virtualFiles.add(virtualFile)
                continue
            }

            val children = virtualFile.children
            if (children.isNullOrEmpty()) {
                continue
            }

            val dirName = virtualFile.name
            val validDir = dirName.startsWith("drawable") || dirName.startsWith("mipmap")
            if (validDir) {
                virtualFiles.add(virtualFile)
                continue
            }

            for (child in children) {
                val name = child.name
                if (name.startsWith(".")) {
                    // 隐藏文件忽略
                    continue
                }

                // 存在非目录文件且不是drawable或mipmap则不拦截
                if (!child.isDirectory) {
                    if (!XUtils.isImage(name)) {
                        return false
                    }
                } else if (!name.startsWith("drawable") && !name.startsWith("mipmap")){
                    return false
                }
            }

            virtualFiles.add(virtualFile)
        }

        if (virtualFiles.isEmpty()) {
            return false
        }

        files = virtualFiles
        return true
    }

    // <editor-fold desc="项目内移动到指定目录监听">
    override fun isValidTarget(targetElement: PsiElement?, sources: Array<out PsiElement>?): Boolean {
        if (sources.isNullOrEmpty()) {
            return false
        }

        if (!targetElement?.module.isFlutterProject) {
            return false
        }

        return canImport(sources.iterator(), targetElement)
    }

    override fun doMove(
        project: Project,
        elements: Array<out PsiElement>?,
        targetContainer: PsiElement?,
        callback: MoveCallback?
    ) {

        files?.also {
            val clearFile = ArrayList(it)
            ImportSameImageResUtils.import(project, clearFile, importToFile) {
                // 移动的数据，要从原目录删除
                clearFile.forEach { file ->
                    file.delete(project)
                }
            }
        }
        importToFile = null
        files = null
    }
    // </editor-fold>
}
