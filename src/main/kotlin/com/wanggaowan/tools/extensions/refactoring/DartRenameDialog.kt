// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.wanggaowan.tools.extensions.refactoring

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.usages.*
import com.intellij.util.ui.JBUI
import com.intellij.xml.util.XmlStringUtil
import com.jetbrains.lang.dart.DartBundle
import com.wanggaowan.tools.extensions.findusage.FindProgress
import com.wanggaowan.tools.extensions.findusage.FindUsageManager
import com.wanggaowan.tools.utils.ProgressUtils
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class DartRenameDialog(
    project: Project,
    private val mFile: PsiFile,
) :
    RefactoringDialog(project, true) {
    private val myNewNamePrefix = JLabel("")
    private var myNameSuggestionsField: NameSuggestionsField? = null

    init {
        title = DartBundle.message("dialog.title.rename.0", "Dart File")
        createNewNameComponent()
        init()
        previewAction.isEnabled = false
        refactorAction.isEnabled = false
    }

    override fun doAction() {
        if (isPreviewUsages) {
            close(OK_EXIT_CODE)
            findUsages(true)
        } else {
            close(OK_EXIT_CODE)
            findUsages(false)
        }
    }

    @Throws(ConfigurationException::class)
    override fun canRun() {
        if (Comparing.strEqual(newName, mFile.name)) {
            throw ConfigurationException(null)
        }
        super.canRun()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbConstraints = GridBagConstraints()

        gbConstraints.insets = JBUI.insetsBottom(4)
        gbConstraints.weighty = 0.0
        gbConstraints.weightx = 1.0
        gbConstraints.gridwidth = GridBagConstraints.REMAINDER
        gbConstraints.fill = GridBagConstraints.BOTH
        val nameLabel = JLabel()
        panel.add(nameLabel, gbConstraints)
        nameLabel.text = XmlStringUtil.wrapInHtml(
            XmlStringUtil.escapeString(
                labelText, false
            )
        )

        gbConstraints.insets = JBUI.insetsBottom(4)
        gbConstraints.gridwidth = 1
        gbConstraints.fill = GridBagConstraints.NONE
        gbConstraints.weightx = 0.0
        gbConstraints.gridx = 0
        gbConstraints.anchor = GridBagConstraints.WEST
        panel.add(myNewNamePrefix, gbConstraints)

        gbConstraints.insets = JBUI.insetsBottom(8)
        gbConstraints.gridwidth = 2
        gbConstraints.fill = GridBagConstraints.BOTH
        gbConstraints.weightx = 1.0
        gbConstraints.gridx = 0
        gbConstraints.weighty = 1.0
        panel.add(myNameSuggestionsField!!.component, gbConstraints)

        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return myNameSuggestionsField!!.focusableComponent
    }

    private fun createNewNameComponent() {
        val suggestedNames = suggestedNames
        myNameSuggestionsField = object : NameSuggestionsField(suggestedNames, myProject, FileTypes.PLAIN_TEXT, null) {
            override fun shouldSelectAll(): Boolean {
                return true
            }
        }

        myNameSuggestionsField?.addDataChangedListener { this.processNewNameChanged() }
    }

    private val labelText: String
        get() = RefactoringBundle.message("rename.0.and.its.usages.to", "File ", mFile.name)

    private val newName: String
        get() = myNameSuggestionsField!!.enteredName.trim()

    private val suggestedNames: Array<String>
        get() = arrayOf(mFile.name)

    private fun processNewNameChanged() {
        val isEnable = newName != mFile.name
        previewAction.isEnabled = isEnable
        refactorAction.isEnabled = isEnable
    }

    override fun hasPreviewButton(): Boolean {
        return true
    }

    private fun findUsages(isPreview: Boolean) {
        val usages = mutableSetOf<Usage>()
        FindUsageManager(project).findUsages(mFile, findProgress = object : FindProgress() {
            override fun find(usage: Usage) {
                usages.add(usage)
            }

            override fun end(indicator: ProgressIndicator) {
                if (isPreview) {
                    ApplicationManager.getApplication().invokeLater {
                        previewRefactoring(usages)
                    }
                } else {
                    doRename(usages, indicator)
                }
            }
        })
    }

    private fun previewRefactoring(usages: Set<Usage>) {
        val presentation = UsageViewPresentation()
        presentation.tabText = RefactoringBundle.message("usageView.tabText")
        presentation.isShowCancelButton = true
        presentation.targetsNodeText = RefactoringBundle.message("0.to.be.renamed.to.1.2", mFile.name, "", newName)
        presentation.nonCodeUsagesString = DartBundle.message("usages.in.comments.to.rename")
        presentation.codeUsagesString = DartBundle.message("usages.in.code.to.rename")
        presentation.setDynamicUsagesString(DartBundle.message("dynamic.usages.to.rename"))
        presentation.isUsageTypeFilteringAvailable = false

        val targets = arrayOf(PsiElement2UsageTargetAdapter(mFile, true))
        val usageArray = usages.toTypedArray()
        val usageView = UsageViewManager.getInstance(myProject).showUsages(targets, usageArray, presentation)
        usageView.addPerformOperationAction(
            {
                ProgressUtils.runBackground(project, "Renaming File", true) {
                    it.isIndeterminate = true
                    doRename(usages, it)
                    it.isIndeterminate = false
                    it.fraction = 1.0
                }
            },
            "Rename File '${mFile.name} to '${newName}'",
            DartBundle.message("rename.need.reRun"),
            RefactoringBundle.message("usageView.doAction"), false
        )
    }

    private fun doRename(usages: Set<Usage>, indicator: ProgressIndicator) {
        WriteCommandAction.runWriteCommandAction(project) {
            val oldName = mFile.name
            indicator.text = "Renaming $oldName"
            val newName = newName
            mFile.name = newName
            usages.forEach {
                if (indicator.isCanceled) {
                    return@forEach
                }

                renameReference(it, oldName, newName)
            }
        }
    }

    private fun renameReference(it: Usage, oldName: String, newName: String) {
        if (it !is UsageInfo2UsageAdapter) {
            return
        }

        val element = it.element ?: return
        val file = element.containingFile ?: return
        val manager = PsiDocumentManager.getInstance(project)
        val document = manager.getDocument(file) ?: return
        manager.commitDocument(document)
        val textRange = element.textRange
        val text = element.text.replace(oldName, "{replace}").replace("{replace}", newName)
        document.replaceString(textRange.startOffset, textRange.endOffset, text)
        manager.commitDocument(document)
    }
}
