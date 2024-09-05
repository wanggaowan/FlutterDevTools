package com.wanggaowan.tools.actions

import com.intellij.json.JsonElementTypes
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

/**
 * 删除多个arb文件相同key元素
 *
 * @author Created by wanggaowan on 2024/3/25 16:33
 */
class DeleteArbSameKeyElementAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }

        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (element == null) {
            e.presentation.isVisible = false
            return
        }

        val file = element.containingFile
        if (file?.name?.lowercase()?.endsWith(".arb") != true) {
            e.presentation.isVisible = false
            return
        }

        if (!e.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT) ?: return
        if (element !is JsonProperty) {
            return
        }

        val project = element.project
        ProgressUtils.runBackground(project, "delete arb same key") { indicator ->
            indicator.isIndeterminate = true
            WriteCommandAction.runWriteCommandAction(project) {
                val results = mutableListOf<PsiElement>()
                results.add(element)
                val jsonObject = element.findParentOfType<JsonObject>()
                if (isLastChild(jsonObject, element)) {
                    getWithElementDeleteOtherNodePrev(element, results)
                } else {
                    getWithElementDeleteOtherNode(element, results)
                }

                val name = element.name
                val file = element.containingFile?.virtualFile
                val parent = file?.parent
                if (parent != null && parent.isDirectory) {
                    getOtherArbSameElement(project, parent, file, name, results)
                }

                results.forEach {
                    it.delete()
                }
                FileDocumentManager.getInstance().saveAllDocuments()
                indicator.fraction = 1.0
            }
        }
    }

    // 获取与指定element需要一起删除的其它节点，如换行，','等
    private fun getWithElementDeleteOtherNode(element: PsiElement, results: MutableList<PsiElement>) {
        val nextElement = element.nextSibling ?: return
        if (nextElement is JsonProperty || !nextElement.isValid
            || nextElement.node.elementType == JsonElementTypes.R_CURLY) {
            return
        }

        if (nextElement.node.elementType == JsonElementTypes.COMMA) {
            results.add(nextElement)
        }
        getWithElementDeleteOtherNode(nextElement, results)
    }

    // 获取与指定element需要一起删除的其它节点，如换行，','等
    private fun getWithElementDeleteOtherNodePrev(element: PsiElement, results: MutableList<PsiElement>) {
        val nextElement = element.prevSibling ?: return
        if (nextElement is JsonProperty || !nextElement.isValid
            || nextElement.node.elementType == JsonElementTypes.L_CURLY) {
            return
        }

        if (nextElement.node.elementType == JsonElementTypes.COMMA) {
            results.add(nextElement)
        }
        getWithElementDeleteOtherNodePrev(nextElement, results)
    }

    private fun isLastChild(jsonObject: JsonObject?, element: PsiElement): Boolean {
        val list = jsonObject?.propertyList
        if (list.isNullOrEmpty()) {
            return false
        }
        return list.last() == element
    }

    private fun getOtherArbSameElement(
        project: Project,
        parent: VirtualFile,
        currentFile: VirtualFile?,
        key: String,
        results: MutableList<PsiElement>) {
        parent.children.forEach {
            val name = it.name
            if (name != currentFile?.name && name.lowercase().endsWith(".arb")) {
                val jsonObject = it.toPsiFile(project)?.getChildOfType<JsonObject>()
                val element = jsonObject?.findProperty(key)
                if (element != null) {
                    results.add(element)
                    if (isLastChild(jsonObject, element)) {
                        getWithElementDeleteOtherNodePrev(element, results)
                    } else {
                        getWithElementDeleteOtherNode(element, results)
                    }
                }
            }
        }
    }
}
