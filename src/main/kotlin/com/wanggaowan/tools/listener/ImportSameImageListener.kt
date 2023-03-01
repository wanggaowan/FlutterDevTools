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
import com.intellij.psi.PsiFile
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.wanggaowan.tools.actions.ImportSameImageResUtils
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.idea.refactoring.project

/**
 * 监听导入不同分辨率相同图片资源动作，满足条件则触发导入
 *
 * @author Created by wanggaowan on 2023/2/26 14:30
 */
class ImportSameImageListener : MoveHandlerDelegate(), PasteProvider {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

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

        if (!FileCopyPasteUtil.isFileListFlavorAvailable()) {
            return false
        }

        val contents = CopyPasteManager.getInstance().contents ?: return false
        val files = FileCopyPasteUtil.getFileList(contents)
        if (files.isNullOrEmpty()) {
            return false
        }

        val virtualFiles = mutableListOf<VirtualFile>()
        for (file in files) {
            val fileName = file.name
            if (fileName.startsWith(".")) {
                // 隐藏文件忽略
                continue
            }

            if (!file.isDirectory) {
                return false
            }

            val virtualFile =
                VirtualFileManager.getInstance().findFileByUrl("file://${file.absolutePath}") ?: return false
            for (child in virtualFile.children) {
                val name = child.name
                if (name.startsWith(".")) {
                    // 隐藏文件忽略
                    continue
                }

                if (!child.isDirectory
                    || (!name.startsWith("drawable")
                        && !name.startsWith("mipmap"))
                ) {
                    return false
                }
            }

            virtualFiles.add(virtualFile)
        }

        if (virtualFiles.isEmpty()) {
            return false
        }

        Companion.files = virtualFiles
        importToFile = null
        dataContext.getData(LangDataKeys.PASTE_TARGET_PSI_ELEMENT)?.also {
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

        val files = mutableListOf<VirtualFile>()
        for (element in sources) {
            if (element is PsiFile) {
                if (element.name.startsWith(".")) {
                    // 隐藏文件忽略
                    continue
                } else {
                    return false
                }
            }

            if (element !is PsiDirectory) {
                continue
            }

            val file = element.virtualFile
            if (file.name.startsWith(".")) {
                // 隐藏文件忽略
                continue
            }

            for (child in file.children) {
                val name = child.name
                if (name.startsWith(".")) {
                    // 隐藏文件忽略
                    continue
                }

                if (!child.isDirectory
                    || (!name.startsWith("drawable")
                        && !name.startsWith("mipmap"))
                ) {
                    return false
                }
            }
            files.add(file)
        }

        if (files.isEmpty()) {
            return false
        }

        Companion.files = files
        return true
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
