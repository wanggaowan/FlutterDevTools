package com.wanggaowan.tools.ui.language

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField
import com.jetbrains.lang.dart.DartLanguage

/**
 * dart语言文本编辑器，提供语法高亮及智能补全等
 *
 * @author Created by wanggaowan on 2023/9/4 13:40
 */
class DartLanguageTextField(project: Project) :
    LanguageTextField(DartLanguage.INSTANCE, project, "", false) {

    override fun createEditor(): EditorEx {
        val editorEx = super.createEditor()
        editorEx.setVerticalScrollbarVisible(true)
        editorEx.setHorizontalScrollbarVisible(true)
        editorEx.setCaretEnabled(true)
        editorEx.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (editorEx.isDisposed) {
                    return
                }

                ApplicationManager.getApplication().invokeLater({
                    if (editorEx.isDisposed) {
                        return@invokeLater
                    }
                    CodeFoldingManager.getInstance(this@DartLanguageTextField.project).updateFoldRegions(editorEx)
                }, ModalityState.nonModal())
            }
        })

        val settings: EditorSettings = editorEx.settings
        settings.isLineNumbersShown = true
        settings.isUseSoftWraps = false
        settings.isAutoCodeFoldingEnabled = true
        settings.isFoldingOutlineShown = true
        settings.isAllowSingleLogicalLineFolding = true
        settings.additionalLinesCount = 5
        return editorEx
    }
}
