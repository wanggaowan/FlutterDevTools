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
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.ProgressUtils
import com.wanggaowan.tools.utils.TranslateUtils
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import java.awt.datatransfer.DataFlavor

/**
 * 粘贴android string.xml中定义的多语言文本时，如果粘贴位置为arb文件，则将其转化为arb支持的格式
 *
 * @author Created by wanggaowan on 2023/6/7 14:57
 */
class AndroidStringPasteProvider : PasteProvider {

    private var copyString: String? = null

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun performPaste(context: DataContext) {
        val text = copyString ?: return
        val editor = context.getData(CommonDataKeys.EDITOR) ?: return
        val project = context.getData(CommonDataKeys.PROJECT) ?: return

        ProgressUtils.runBackground(project, "convert android string") { progressIndicator ->
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

                    val index3 = str.indexOf("</string>")
                    if (index3 == -1) {
                        progressIndicator.fraction = (j + 1) * 1.0 / strings.size * 0.9
                        continue
                    }

                    val key = str.substring(index + ("name=".length), index2).trim()
                    var value = str.substring(index2 + 1, index3)
                    val placeholderList = mutableListOf<Placeholder>()
                    if (value.isNotEmpty()) {
                        val placeholderSuffixes = listOf("s", "d", "f")
                        placeholderSuffixes.forEach {
                            val regex = Regex("%[-+.#(0-9\\s\\\\$]*($it|${it.uppercase()})")
                            val type = when (it) {
                                "s" -> "String"
                                "d" -> "int"
                                else -> "double"
                            }
                            replace(regex, value, type, placeholderList)
                        }
                    }

                    var placeholderStr: String? = null
                    if (placeholderList.isNotEmpty()) {
                        placeholderList.sortBy { it.index }
                        if (!PluginSettings.getCopyAndroidStrUseSimpleMode(project)) {
                            placeholderStr = "\"@${key.replace("\"", "")}\": { \"placeholders\": { %s } }"
                            val stringBuilder2 = StringBuilder()
                            for (i in placeholderList.indices) {
                                val placeholder = placeholderList[i]
                                val indexOf = value.indexOf(placeholder.placeholder)
                                val paramName = "param$i"
                                value = value.replaceRange(
                                    indexOf,
                                    indexOf + placeholder.placeholder.length,
                                    "{$paramName}"
                                )
                                stringBuilder2.append("\"$paramName\": { \"type\": \"${placeholder.type}\" }")
                                if (placeholderList.size > 1 && i < placeholderList.size - 1) {
                                    stringBuilder2.append(",")
                                }
                            }
                            placeholderStr = placeholderStr.format(stringBuilder2.toString())
                        } else {
                            for (i in placeholderList.indices) {
                                val placeholder = placeholderList[i]
                                val indexOf = value.indexOf(placeholder.placeholder)
                                val paramName = "param$i"
                                value = value.replaceRange(
                                    indexOf,
                                    indexOf + placeholder.placeholder.length,
                                    "{$paramName}"
                                )
                            }
                        }
                    }

                    // 处理单引号多余的转义斜杠。以下正则匹配单引号前面的反斜杠
                    var regex = Regex("[\\\\\\s]*'")
                    value = TranslateUtils.fixEscapeFormatError(regex, value,false)
                    // 处理双引号缺失的转义斜杠。以下正则匹配双引号前面的反斜杠
                    regex = Regex("[\\\\\\s]*\"")
                    value = TranslateUtils.fixEscapeFormatError(regex, value)

                    stringBuilder.append("$key: \"$value\"")
                    if (placeholderStr != null || (strings.size > 1 && count < strings.size - 1)) {
                        stringBuilder.append(",").append("\n")
                    }

                    if (placeholderStr != null) {
                        stringBuilder.append(placeholderStr)
                        if (strings.size > 1 && count < strings.size - 1) {
                            stringBuilder.append(",").append("\n")
                        }
                    }

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
        regex: Regex,
        value: String,
        type: String,
        placeholderList: MutableList<Placeholder>,
        startIndex: Int? = null,
    ) {
        val matchResult = regex.find(value, startIndex ?: 0) ?: return
        val placeholder = value.substring(matchResult.range)
        placeholderList.add(Placeholder(placeholder, type, matchResult.range.first))
        replace(regex, value, type, placeholderList, matchResult.range.last + 1)
    }

    override fun isPastePossible(dataContext: DataContext): Boolean {
        return false
    }

    override fun isPasteEnabled(dataContext: DataContext): Boolean {
        val file = dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file == null || !file.name.endsWith(".arb")) {
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
        if (!contents.startsWith("<string") || !contents.endsWith("</string>")) {
            return false
        }

        copyString = contents
        return true
    }

}

class Placeholder(val placeholder: String, val type: String, val index: Int)


