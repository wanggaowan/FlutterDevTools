package com.wanggaowan.tools.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.wanggaowan.tools.ui.ImagesRenameDialog
import com.wanggaowan.tools.ui.RenameEntity
import com.wanggaowan.tools.utils.NotificationUtils


/**
 * 重命令多个相同名称但是在不同分辨率下的文件
 *
 * @author Created by wanggaowan on 2023/5/25 10:40
 */
class RenameMultiSameNameFileAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    // override fun update(e: AnActionEvent) {
    //     val module = e.getData(LangDataKeys.MODULE)
    //     if (module == null) {
    //         e.presentation.isVisible = false
    //         return
    //     }
    //
    //     if (!module.isFlutterProject) {
    //         e.presentation.isVisible = false
    //         return
    //     }
    //
    //     val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    //     if (virtualFiles.isNullOrEmpty()) {
    //         e.presentation.isVisible = false
    //         return
    //     }
    //
    //     val basePath = module.basePath
    //     val imageDir = PluginSettings.getImagesFileDir(module.project)
    //     if (!virtualFiles[0].path.startsWith("$basePath/$imageDir") && !virtualFiles[0].path.startsWith("$basePath/example/$imageDir")) {
    //         e.presentation.isVisible = false
    //         return
    //     }
    //
    //     for (file in virtualFiles) {
    //         if (file.isDirectory) {
    //             e.presentation.isVisible = false
    //             return
    //         }
    //     }
    //
    //     e.presentation.isVisible = true
    // }

    override fun actionPerformed(e: AnActionEvent) {
        val module = e.getData(LangDataKeys.MODULE) ?: return
        val project = module.project
        val selectFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        if (selectFiles.isEmpty()) {
            return
        }

        val distinctList = distinctFile(selectFiles)
        val dialog = ImagesRenameDialog(project, distinctList)
        dialog.setOkActionListener {
            val fileList = dialog.getRenameFileList()
            rename(project, fileList)
        }
        dialog.isVisible = true
    }

    /**
     * 去除重复的数据
     */
    private fun distinctFile(array: Array<VirtualFile>): List<VirtualFile> {
        val list = mutableListOf<VirtualFile>()
        array.forEach {
            if (!it.isDirectory) {
                val exist = list.find { file -> file.name == it.name }
                if (exist == null) {
                    list.add(it)
                }
            }
        }
        return list
    }

    private fun rename(project: Project, data: List<RenameEntity>) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "images rename") {
            override fun run(progressIndicator: ProgressIndicator) {
                progressIndicator.isIndeterminate = true
                WriteCommandAction.runWriteCommandAction(project) {
                    data.forEach {
                        if (it.oldName.isNotEmpty() && it.newName.isNotEmpty()
                            && it.oldName != it.newName
                            && (!it.existFile || it.coverExistFile)
                        ) {
                            val parentName = it.oldFile.parent.name
                            val parent = if (parentName == "1.5x"
                                || parentName == "2.0x"
                                || parentName == "3.0x"
                                || parentName == "4.0x"
                            ) {
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
        })
    }

    private fun reanmePsiElement(project: Project, psiElement: PsiElement, newName: String) {
        val elementProcessor = RenamePsiElementProcessor.forElement(psiElement)
        elementProcessor.setToSearchInComments(psiElement, true)
        elementProcessor.setToSearchForTextOccurrences(psiElement, true)
        val processor = RenameProcessor(
            project,
            psiElement,
            newName,
            GlobalSearchScope.projectScope(project),
            true,
            true
        )

        val var3: Iterator<*> = AutomaticRenamerFactory.EP_NAME.extensionList.iterator()
        while (var3.hasNext()) {
            val factory = var3.next() as AutomaticRenamerFactory

            if (factory.isApplicable(psiElement) && factory.optionName != null && factory.isEnabled) {
                processor.addRenamerFactory(factory)
            }
        }

        val prepareSuccessfulCallback = Runnable {
            // 执行完成
        }
        processor.setPrepareSuccessfulSwingThreadCallback(prepareSuccessfulCallback)
        processor.setPreviewUsages(true)
        processor.run()
    }
}
