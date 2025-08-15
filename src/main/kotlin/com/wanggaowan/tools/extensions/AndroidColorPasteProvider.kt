package com.wanggaowan.tools.extensions

import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.TextRange
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import java.awt.datatransfer.DataFlavor

/**
 * 粘贴android colors.xml中定义的多语言文本时，将其转化为dart支持的格式
 *
 * @author Created by wanggaowan on 2025/8/15 08:41
 */
class AndroidColorPasteProvider : PasteProvider {

    private var copyString: String? = null

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun performPaste(context: DataContext) {
        val text = copyString ?: return
        val editor = context.getData(CommonDataKeys.EDITOR) ?: return
        val project = context.getData(CommonDataKeys.PROJECT) ?: return

        ProgressUtils.runBackground(project, "convert color string") { progressIndicator ->
            progressIndicator.isIndeterminate = false
            WriteCommandAction.runWriteCommandAction(project) {
                val strings = text.split("\n")
                val stringBuilder = StringBuilder()
                var count = 0
                for (j in strings.indices) {
                    val str = strings[j]
                    val index = str.indexOf("name=")
                    if (index == -1) {
                        progressIndicator.fraction = (j + 1) * 1.0 / strings.size * 0.9
                        continue
                    }
                    val index2 = str.indexOf(">")
                    if (index2 == -1) {
                        progressIndicator.fraction = (j + 1) * 1.0 / strings.size * 0.9
                        continue
                    }

                    val index3 = str.indexOf("</color>")
                    if (index3 == -1) {
                        progressIndicator.fraction = (j + 1) * 1.0 / strings.size * 0.9
                        continue
                    }

                    val key = str.substring(index + ("name=".length), index2).trim().replace("\"","")
                    var value = str.substring(index2 + 1, index3)
                    if (!value.startsWith("#")) {
                        progressIndicator.fraction = (j + 1) * 1.0 / strings.size * 0.9
                        continue
                    }

                    value = value.substring(1)
                    if(value.length == 6) {
                        value = "FF$value"
                    }

                    if(value.length == 3) {
                        var color = ""
                        for (char in value.toCharArray()) {
                            color += "$char$char"
                        }
                        value = "FF$color"
                    }

                    value = "0x${value}"
                    stringBuilder.append("static const Color $key = Color($value);").append("\n")
                    count++
                    progressIndicator.fraction = (j + 1) * 1.0 / strings.size * 0.9
                }

                val replaceContent = stringBuilder.toString()
                editor.document.insertString(editor.selectionModel.selectionEnd, replaceContent)
                val oldStarIndex = editor.selectionModel.selectionStart
                val endIndex = editor.selectionModel.selectionEnd + replaceContent.length
                // 将光标移动到结束位置
                editor.caretModel.moveToOffset(endIndex)
                FileDocumentManager.getInstance().saveDocument(editor.document)
                context.getData(CommonDataKeys.PSI_FILE)?.also { file ->
                    DartPsiUtils.reformatFile(
                        project,
                        file,
                        listOf(TextRange(oldStarIndex, endIndex))
                    )
                }
            }

            progressIndicator.fraction = 1.0
        }
    }

    private tailrec fun replace(
        startIndex: Int,
        value: String,
        placeholder: String,
        type: String,
        placeholderList: MutableList<Placeholder>
    ) {
        val indexOf = value.indexOf(placeholder, startIndex = startIndex)
        if (indexOf != -1) {
            val endIndex = indexOf + placeholder.length
            placeholderList.add(Placeholder(placeholder, type, indexOf))
            replace(endIndex, value, placeholder, type, placeholderList)
        }
    }

    override fun isPastePossible(dataContext: DataContext): Boolean {
        return false
    }

    override fun isPasteEnabled(dataContext: DataContext): Boolean {
        val file = dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file == null || !file.name.endsWith(".dart")) {
            return false
        }

        if (dataContext.getData(CommonDataKeys.EDITOR) == null) {
            return false
        }

        val module = dataContext.getData(LangDataKeys.MODULE) ?: return false
        if (!module.isFlutterProject) {
            return false
        }

        var contents = CopyPasteManager.getInstance().contents?.getTransferData(DataFlavor.stringFlavor)
        if (contents !is String) {
            return false
        }

        contents = contents.trim()
        if (!contents.startsWith("<color") || !contents.endsWith("</color>")) {
            return false
        }

        copyString = contents
        return true
    }
}

