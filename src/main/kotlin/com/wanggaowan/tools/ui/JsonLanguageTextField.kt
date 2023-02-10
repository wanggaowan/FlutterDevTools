package com.wanggaowan.tools.ui

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.json.JsonLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField

/**
 *
 *
 * @author Created by wanggaowan on 2023/2/7 16:56
 */
class JsonLanguageTextField(project: Project) :
    LanguageTextField(JsonLanguage.INSTANCE, project, "", false) {

    override fun createEditor(): EditorEx {
        val editorEx = super.createEditor()
        editorEx.setVerticalScrollbarVisible(true)
        editorEx.setHorizontalScrollbarVisible(true)
        editorEx.setCaretEnabled(true)
        editorEx.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                ApplicationManager.getApplication().invokeLater({
                    CodeFoldingManager.getInstance(this@JsonLanguageTextField.project).updateFoldRegions(editorEx)
                }, ModalityState.NON_MODAL)
            }
        })

        val settings: EditorSettings = editorEx.settings
        settings.isLineNumbersShown = true
        settings.isUseSoftWraps = true
        settings.isAutoCodeFoldingEnabled = true
        settings.isFoldingOutlineShown = true
        settings.isAllowSingleLogicalLineFolding = true
        settings.additionalLinesCount = 5
        return editorEx
    }
}
