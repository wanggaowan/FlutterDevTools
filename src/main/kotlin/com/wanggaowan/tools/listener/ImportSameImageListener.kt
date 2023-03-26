package com.wanggaowan.tools.listener

import com.intellij.ide.PasteProvider
import com.intellij.ide.dnd.FileCopyPasteUtil
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
import com.wanggaowan.tools.actions.ImportSameImageResUtils
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.idea.refactoring.project
import java.io.File

/**
 * 监听导入不同分辨率相同图片资源动作，满足条件则触发导入
 *
 * @author Created by wanggaowan on 2023/2/26 14:30
 */
class ImportSameImageListener : MoveHandlerDelegate(), PasteProvider {

    // override fun getActionUpdateThread(): ActionUpdateThread {
    //     return ActionUpdateThread.BGT
    // }

    override fun performPaste(dataContext: DataContext) {
        files?.also {
            ImportSameImageResUtils.import(dataContext.project, it, importToFile)
            importToFile = null
            files = null
        }
    }

    override fun isPastePossible(dataContext: DataContext): Boolean {
        return false
    }

    override fun isPasteEnabled(dataContext: DataContext): Boolean {
        if (!dataContext.project.isFlutterProject) {
            return false
        }

        if (isAndroidRes(dataContext, null)) {
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
        return canImport(files.iterator(), dataContext)
    }

    private fun isAndroidRes(dataContext: DataContext?, psiElement: PsiElement?): Boolean {
        if (dataContext != null) {
            dataContext.getData(LangDataKeys.PASTE_TARGET_PSI_ELEMENT)?.also {
                if (it is PsiDirectory) {
                    val path = it.virtualFile.path
                    return path.contains("/android")
                        || path.contains("/ios")
                        || path.contains("/web")
                }
            }

            return false
        }

        if (psiElement is PsiDirectory) {
            val path = psiElement.virtualFile.path
            return path.contains("/android")
                || path.contains("/ios")
                || path.contains("/web")
        }

        return false
    }

    private fun <T> canImport(iterator: Iterator<T>, dataContext: DataContext?): Boolean {
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
            if (!isDirectory) {
                // 只处理目录数据
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

            val dirName = virtualFile.name
            val validDir = dirName.startsWith("drawable") || dirName.startsWith("mipmap")
            if (validDir) {
                virtualFiles.add(virtualFile)
                continue
            }

            for (child in virtualFile.children) {
                val name = child.name
                if (name.startsWith(".")) {
                    // 隐藏文件忽略
                    continue
                }

                // 存在非目录文件且不是drawable或mipmap则不拦截
                if (!child.isDirectory || (!name.startsWith("drawable") && !name.startsWith("mipmap"))) {
                    return false
                }
            }

            virtualFiles.add(virtualFile)
        }

        if (virtualFiles.isEmpty()) {
            return false
        }

        files = virtualFiles
        importToFile = null
        dataContext?.getData(LangDataKeys.PASTE_TARGET_PSI_ELEMENT)?.also {
            if (it is PsiDirectory) {
                importToFile = it.virtualFile
            }
        }
        return true
    }

    private fun canImportOneFile(virtualFile: VirtualFile): VirtualFile? {
        if (virtualFile.isDirectory) {
            val name = virtualFile.name
            if (name.startsWith("drawable") || name.startsWith("mipmap")) {
                return virtualFile.parent
            }

            for (child in virtualFile.children) {
                val name2 = child.name
                if (!child.isDirectory || (!name2.startsWith("drawable") && !name2.startsWith("mipmap"))) {
                    return null
                }
            }

            return virtualFile
        }

        var name = virtualFile.name
        if (!XUtils.isImage(name)) {
            return null
        }

        val parent = virtualFile.parent ?: return null
        name = parent.name
        if (!name.startsWith("drawable") && !name.startsWith("mipmap")) {
            return null
        }
        return parent.parent
    }

    override fun isValidTarget(targetElement: PsiElement?, sources: Array<out PsiElement>?): Boolean {
        if (sources.isNullOrEmpty()) {
            return false
        }

        if (!sources[0].project.isFlutterProject) {
            return false
        }

        if (isAndroidRes(null, targetElement)) {
            return false
        }

        return canImport(sources.iterator(), null)
    }

    override fun doMove(
        project: Project?,
        elements: Array<out PsiElement>?,
        targetContainer: PsiElement?,
        callback: MoveCallback?
    ) {
        files?.also {
            importToFile = null
            if (targetContainer is PsiDirectory) {
                importToFile = targetContainer.virtualFile
            }
            ImportSameImageResUtils.import(project, it, importToFile)
            importToFile = null
            files = null
        }
    }

    companion object {
        private var files: List<VirtualFile>? = null
        private var importToFile: VirtualFile? = null
    }

}
