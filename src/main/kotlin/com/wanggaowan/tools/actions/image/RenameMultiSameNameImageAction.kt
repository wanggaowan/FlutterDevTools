package com.wanggaowan.tools.actions.image

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.wanggaowan.tools.ui.ImagesRenameDialog
import com.wanggaowan.tools.ui.RenameEntity
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.XUtils


/**
 * 重命令多个相同名称但是在不同分辨率下的文件
 *
 * @author Created by wanggaowan on 2023/5/25 10:40
 */
class RenameMultiSameNameImageAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val module = e.getData(LangDataKeys.MODULE) ?: return
        val project = module.project
        val selectFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        if (selectFiles.isEmpty()) {
            return
        }

        RenameImageHandel(project).rename(selectFiles)
    }
}

class RenameImageHandel(val project: Project) {

    /**
     * 对文件进行重命名
     * [renameSameNameOtherFiles]表示是否重命名与当前文件名称相同但是在不同分辨率下的图片
     */
    fun rename(files: Array<VirtualFile>, renameSameNameOtherFiles: Boolean = true) {
        // 基本不会有单个图片重命名的需求，因此始终修改所有分辨率图片

        val dialog = ImagesRenameDialog(project, files, renameSameNameOtherFiles)
        dialog.setOkActionListener {
            val fileList = dialog.getRenameFileList()
            rename(fileList)
        }
        dialog.isVisible = true
    }


    private fun rename(data: List<RenameEntity>) {
        ProgressUtils.runBackground(project, "images rename") { progressIndicator ->
            progressIndicator.isIndeterminate = true
            WriteCommandAction.runWriteCommandAction(project) {
                data.forEach {
                    it.usages.forEach { usage ->
                        renameReference(usage, it)
                    }

                    if (it.oldName.isNotEmpty() && it.newName.isNotEmpty()
                        && it.oldName != it.newName
                        && (!it.existFile || it.coverExistFile)
                    ) {
                        val parentName = it.oldFile.parent.name
                        val parent = if (XUtils.isImageVariantsFolder(parentName)) {
                            it.oldFile.parent.parent
                        } else {
                            it.oldFile.parent
                        }

                        parent.findChild(it.oldName)?.also { file ->
                            file.rename(project, it.newName)
                        }

                        parent.findChild("1.5x")?.findChild(it.oldName)?.also { file ->
                            file.rename(project, it.newName)
                        }

                        parent.findChild("2.0x")?.findChild(it.oldName)?.also { file ->
                            file.rename(project, it.newName)
                        }

                        parent.findChild("3.0x")?.findChild(it.oldName)?.also { file ->
                            file.rename(project, it.newName)
                        }

                        parent.findChild("4.0x")?.findChild(it.oldName)?.also { file ->
                            file.rename(project, it.newName)
                        }
                    }
                }

                NotificationUtils.showBalloonMsg(project, "图片已重命名", NotificationType.INFORMATION)
            }

            progressIndicator.isIndeterminate = false
            progressIndicator.fraction = 1.0
        }
    }

    private fun renameReference(it: Usage, renameEntity: RenameEntity) {
        if (it !is UsageInfo2UsageAdapter) {
            return
        }
        val parent = renameEntity.oldFile.parent ?: return
        val oldKey = XUtils.imageFileToImageKey(project, parent, renameEntity.oldName) ?: return
        val newKey = XUtils.imageFileToImageKey(project, parent, renameEntity.newName) ?: return

        val element = it.element ?: return
        val file = element.containingFile ?: return
        val manager = PsiDocumentManager.getInstance(project)
        val document = manager.getDocument(file) ?: return
        manager.commitDocument(document)
        val textRange = element.textRange
        val text = element.text.replace(oldKey, "{replace}").replace("{replace}", newKey)
        document.replaceString(textRange.startOffset, textRange.endOffset, text)
        manager.commitDocument(document)
    }
}
