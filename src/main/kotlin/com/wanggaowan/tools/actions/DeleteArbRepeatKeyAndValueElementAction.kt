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
import com.intellij.psi.PsiElement
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

/**
 * 删除arb文件重复key且内容也一致的元素
 *
 * @author Created by wanggaowan on 2024/3/25 16:33
 */
class DeleteArbRepeatKeyAndValueElementAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }

        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file == null || !file.name.lowercase().endsWith(".arb")) {
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
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        ProgressUtils.runBackground(project, "delete arb repeat key") { indicator ->
            indicator.isIndeterminate = true
            WriteCommandAction.runWriteCommandAction(project) {
                val jsonObject = file.getChildOfType<JsonObject>() ?: return@runWriteCommandAction
                val list = jsonObject.propertyList
                val map = mutableMapOf<String, MutableList<JsonProperty>>()
                for (property in list) {
                    val key = property.name + (property.value?.text?:"")
                    var list = map[key]
                    if (list == null) {
                        list = mutableListOf()
                        map[key] = list
                    }
                    list.add(property)
                }

                map.entries.toList().forEach {
                    if (it.value.size <= 1) {
                        map.remove(it.key)
                    }
                }

                map.values.forEach {
                    for (i in 1 until it.size) {
                        val property = it[i]
                        getWithElementDeleteOtherNode(property)?.delete()
                        property.delete()
                    }
                }
                getWithElementDeleteOtherNodePrev(jsonObject.lastChild)?.delete()

                FileDocumentManager.getInstance().saveAllDocuments()
                indicator.fraction = 1.0
            }
        }
    }

    // 获取与指定element需要一起删除的其它节点，如换行，','等
    private fun getWithElementDeleteOtherNodePrev(element: PsiElement): PsiElement? {
        val nextElement = element.prevSibling ?: return null
        if (nextElement is JsonProperty || !nextElement.isValid
            || nextElement.node.elementType == JsonElementTypes.L_CURLY) {
            return null
        }

        if (nextElement.node.elementType == JsonElementTypes.COMMA) {
            return nextElement
        }
        return getWithElementDeleteOtherNodePrev(nextElement)
    }

    // 获取与指定element需要一起删除的其它节点，如换行，','等
    private fun getWithElementDeleteOtherNode(element: PsiElement): PsiElement? {
        val nextElement = element.nextSibling ?: return null
        if (nextElement is JsonProperty || !nextElement.isValid
            || nextElement.node.elementType == JsonElementTypes.R_CURLY) {
            return null
        }

        if (nextElement.node.elementType == JsonElementTypes.COMMA) {
            return nextElement
        }
        return getWithElementDeleteOtherNode(nextElement)
    }
}
