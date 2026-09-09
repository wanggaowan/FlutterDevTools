package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.usages.Usage
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.wanggaowan.tools.extensions.findusage.FindProgress
import com.wanggaowan.tools.extensions.findusage.FindUsageManager
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.idea.core.util.toPsiFile

/**
 * 删除未使用的文件
 *
 * @author Created by wanggaowan on 2024/3/11 13:06
 */
class DeleteUnusedFilesAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }

        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (virtualFiles.isNullOrEmpty()) {
            e.presentation.isVisible = false
            return
        }

        if (!e.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        if (!(DartAnalysisServerService.getInstance(project).isServerProcessActive)) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true

    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(LangDataKeys.PROJECT) ?: return
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        ProgressUtils.runBackground(project, "Count all need check delete files", true) { indicator ->
            indicator.isIndeterminate = true
            // 收集待检查文件属于VFS/PSI读取，持有read action即可，不需要write action
            val allFiles = mutableListOf<PsiFile>()
            ReadAction.run<RuntimeException> {
                getAllFiles(project, virtualFiles, allFiles)
            }
            indicator.isIndeterminate = false
            indicator.fraction = 1.0

            if (allFiles.isEmpty()) {
                return@runBackground
            }

            val totalCount = allFiles.size
            // findUsages内部会启动独立的后台任务并显示自己的进度，此处需要在EDT线程发起
            ProgressUtils.computeInEdt {
                FindUsageManager(project).findUsages(
                    allFiles.toTypedArray(),
                    onlyFindOneUse = true,
                    progressTitle = {
                        when (it) {
                            null -> {
                                "Find unused files and delete them"
                            }

                            is PsiNamedElement -> {
                                "Find unused file:${it.name}"
                            }

                            else -> {
                                "Find unused"
                            }
                        }
                    },
                    findProgress = object : FindProgress() {
                        var find = false

                        override fun startFindElement(indicator: ProgressIndicator, target: PsiElement) {
                            super.startFindElement(indicator, target)
                            indicator.text = "index: ${allFiles.indexOf(target)}/$totalCount"
                            indicator.text2 = if (target is PsiNamedElement) target.name else null
                            find = false
                        }

                        override fun find(target: PsiElement, usage: Usage) {
                            find = true
                        }

                        override fun endFindElement(indicator: ProgressIndicator, target: PsiElement) {
                            super.endFindElement(indicator, target)
                            if (!find && target is PsiFile) {
                                deleteFile(project, target)
                            }
                        }
                    })
            }
        }
    }

    @Suppress("UnsafeVfsRecursion")
    private fun getAllFiles(project: Project, virtualFiles: Array<VirtualFile>, files: MutableList<PsiFile>) {
        val dirs = mutableListOf<VirtualFile>()
        virtualFiles.forEach {
            if (!it.isDirectory) {
                if(!it.name.lowercase().endsWith("arb")) {
                    // arb文件不会被直接引用，因此直接跳过检查
                    it.toPsiFile(project)?.also { psiFile ->
                        files.add(psiFile)
                    }
                }
            } else {
                dirs.add(it)
            }
        }

        dirs.forEach {
            getAllFiles(project, it.children, files)
        }
    }

    private fun deleteFile(project: Project, element: PsiFile) {
        WriteCommandAction.runWriteCommandAction(project) {
            element.delete()
            element.parent?.also {
                if (it.children.isEmpty()) {
                    it.delete()
                }
            }
        }
    }
}
