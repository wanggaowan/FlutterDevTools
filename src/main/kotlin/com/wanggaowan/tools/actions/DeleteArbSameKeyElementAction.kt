package com.wanggaowan.tools.actions

import com.intellij.json.JsonElementTypes
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import com.wanggaowan.tools.utils.DocumentUtils
import com.wanggaowan.tools.utils.EditorUtils
import com.wanggaowan.tools.utils.NotificationUtils
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
        if (!e.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        val caret = e.getData(CommonDataKeys.CARET)
        if (caret == null || !caret.hasSelection()) {
            // 非选中区域
            val element = e.getData(CommonDataKeys.PSI_ELEMENT)
            if (element == null) {
                e.presentation.isVisible = false
                return
            }
        }

        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (psiFile?.name?.lowercase()?.endsWith(".arb") != true) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val properties = getJsonProperties(e)
        if (properties.isEmpty()) {
            NotificationUtils.showBalloonMsg(
                project,
                "请将光标定位到需要删除的项",
                NotificationType.WARNING
            )
            return
        }

        ProgressUtils.runBackground(project, "delete arb same key") { indicator ->
            indicator.isIndeterminate = false
            indicator.fraction = 0.0
            val total = properties.size
            var index = 0
            for (property in properties) {
                index++
                indicator.text = "Deleting $index / $total"
                indicator.fraction = (index - 1) * 1.0 / total * 0.9

                // 收集及删除操作必须持有写锁，此操作耗时很短
                // PSI相关访问（findParentOfType、containingFile等）必须在持有锁的write action内执行
                WriteCommandAction.runWriteCommandAction(project) {
                    val results = mutableListOf<PsiElement>()
                    results.add(property)
                    val jsonObject = property.findParentOfType<JsonObject>()
                    if (isLastChild(jsonObject, property)) {
                        getWithElementDeleteOtherNodePrev(property, results)
                    } else {
                        getWithElementDeleteOtherNode(property, results)
                    }

                    val name = property.name
                    val file = property.containingFile?.virtualFile
                    val parent = file?.parent
                    if (parent != null && parent.isDirectory) {
                        getOtherArbSameElement(project, parent, file, name, results)
                    }
                    results.forEach {
                        it.delete()
                    }
                }
            }

            DocumentUtils.saveAllDocuments()
            indicator.fraction = 1.0
        }
    }

    /**
     * 获取光标所在的json项，需在EDT线程执行，不能在update中调用
     */
    private fun getJsonProperty(e: AnActionEvent): JsonProperty? {
        e.getData(CommonDataKeys.PSI_ELEMENT)?.findParentOfType<JsonProperty>(false)?.let {
            return it
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (editor != null && psiFile != null) {
            return psiFile.findElementAt(editor.caretModel.offset)?.findParentOfType<JsonProperty>(false)
        }

        return null
    }

    /**
     * 获取选中的json项，支持一次选中多项，需在EDT线程执行
     *
     * 编辑器存在选区时，取所有与选区有交集的json项，否则取光标所在项
     */
    private fun getJsonProperties(e: AnActionEvent): List<JsonProperty> {
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor != null) {
            val properties = EditorUtils.getSelectionAreaPsiElement(editor) {
                it.findParentOfType<JsonProperty>(false)
            }
            if (!properties.isNullOrEmpty()) {
                return properties
            }
        }

        return getJsonProperty(e)?.let { listOf(it) } ?: emptyList()
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
