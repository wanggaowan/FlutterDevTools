package com.wanggaowan.tools.actions.image

import com.intellij.ide.util.DeleteHandler
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.file.PsiBinaryFileImpl
import com.wanggaowan.tools.utils.XUtils
import org.jetbrains.kotlin.idea.core.util.toPsiFile

/**
 * 删除多个相同名称但在不同分辨率下的文件
 *
 * @author Created by wanggaowan on 2023/3/19 18:06
 */
class DeleteMultiSameNameImageAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(LangDataKeys.PROJECT) ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (files.isNullOrEmpty()) {
            return
        }

        val allDeleteFiles = mutableListOf<PsiFile>()
        for (file in files) {
            var parent = file.parent
            if (parent == null) {
                allDeleteFiles.add(getPsiFile(project, file))
                continue
            }

            if (XUtils.isImageVariantsFolder(parent.name)) {
                parent = parent.parent
            }

            if (parent == null) {
                allDeleteFiles.add(getPsiFile(project, file))
                continue
            }

            findChildren(project, parent, file.name, allDeleteFiles)
        }

        try {
            DeleteHandler.deletePsiElement(allDeleteFiles.toTypedArray(), project)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getPsiFile(project: Project, file: VirtualFile, needFind: Boolean = true): PsiFile {
        val psiFile =
            file.toPsiFile(project) ?: throw RuntimeException("can not map virtual file:${file.name} to psi file")
        return if (psiFile is PsiBinaryFile) {
            PsiBinaryFileDelegate(psiFile, needFind)
        } else {
            psiFile
        }
    }

    private fun findChildren(project: Project, parent: VirtualFile, fileName: String, results: MutableList<PsiFile>) {
        var needFind = true
        var child = parent.findChild(fileName)
        if (child != null) {
            val psiFile = getPsiFile(project, child, needFind)
            results.add(psiFile)
            if (psiFile is PsiBinaryFile) {
                needFind = false
            }
        }

        child = parent.findChild("1.5x")
        if (child != null) {
            child = child.findChild(fileName)
            if (child != null) {
                val psiFile = getPsiFile(project, child, needFind)
                results.add(psiFile)
                if (psiFile is PsiBinaryFile) {
                    needFind = false
                }
            }
        }

        child = parent.findChild("2.0x")
        if (child != null) {
            child = child.findChild(fileName)
            if (child != null) {
                val psiFile = getPsiFile(project, child, needFind)
                results.add(psiFile)
                if (psiFile is PsiBinaryFile) {
                    needFind = false
                }
            }
        }

        child = parent.findChild("3.0x")
        if (child != null) {
            child = child.findChild(fileName)
            if (child != null) {
                val psiFile = getPsiFile(project, child, needFind)
                results.add(psiFile)
                if (psiFile is PsiBinaryFile) {
                    needFind = false
                }
            }
        }

        child = parent.findChild("4.0x")
        if (child != null) {
            child = child.findChild(fileName)
            if (child != null) {
                val psiFile = getPsiFile(project, child, needFind)
                results.add(psiFile)
            }
        }
    }
}

// 二进制文件代理，主要是删除不同分辨率相同图片时,存储在变体目录下的图片文件名称，同时展示所处分辨率目录
class PsiBinaryFileDelegate(val file: PsiFile, val needFind: Boolean = true) :
    PsiBinaryFileImpl(file.manager as PsiManagerImpl, file.viewProvider) {
    override fun getName(): String {
        val parentName = parent?.name
        if (XUtils.isImageVariantsFolder(parentName)) {
            return "${parentName}/${super.getName()}"
        }
        return super.getName()
    }

    override fun isEquivalentTo(another: PsiElement?): Boolean {
        return file == another
    }
}
