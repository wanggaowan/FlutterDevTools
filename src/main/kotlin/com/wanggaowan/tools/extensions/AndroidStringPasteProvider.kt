package com.wanggaowan.tools.extensions

import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ide.CopyPasteManager
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import java.awt.datatransfer.DataFlavor

/**
 * 粘贴android string.xml中定义的多语言文本时，如果粘贴位置为arb文件，则将其转化为arb支持的格式
 *
 * @author Created by wanggaowan on 2023/6/7 14:57
 */
class AndroidStringPasteProvider : PasteProvider {

    private var copyString: String? = null

    override fun performPaste(context: DataContext) {
        val text = copyString ?: return
        val editor = context.getData(CommonDataKeys.EDITOR) ?: return

        runWriteAction {
            val strings = text.split("\n")
            val stringBuilder = StringBuilder()
            var count = 0
            for (str in strings) {
                val index = str.indexOf("name=")
                if (index == -1) {
                    continue
                }
                val index2 = str.indexOf(">")
                if (index2 == -1) {
                    continue
                }

                val index3 = str.indexOf("</string>")
                if (index3 == -1) {
                    continue
                }

                val key = str.substring(index + ("name=".length), index2).trim()
                val value = str.substring(index2 + 1, index3)
                stringBuilder.append("$key: \"$value\"")
                if (strings.size > 1 && count < strings.size - 1) {
                    stringBuilder.append(",").append("\n")
                }
                count++
            }
            val replaceContent = stringBuilder.toString()
            editor.document.insertString(editor.selectionModel.selectionEnd, replaceContent)

            FileDocumentManager.getInstance().saveDocument(editor.document)
            context.getData(CommonDataKeys.PROJECT)?.also {
                context.getData(CommonDataKeys.PSI_FILE)?.also { file ->
                    DartPsiUtils.reformatFile(it, file)
                }
            }
        }
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
