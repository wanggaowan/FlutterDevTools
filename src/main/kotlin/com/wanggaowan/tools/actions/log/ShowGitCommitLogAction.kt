package com.wanggaowan.tools.actions.log

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.wanggaowan.tools.ui.UIColor
import com.wanggaowan.tools.ui.language.PlainTextLanguageTextField
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * 展示Git提交的每日日志
 *
 * @author Created by wanggaowan on 2024/6/26 下午2:35
 */
class ShowGitCommitLogAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        GitCommitLogDialog(project).show()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

/**
 * 创建文件模版
 *
 * @author Created by wanggaowan on 2023/9/4 13:31
 */
class GitCommitLogDialog(val project: Project) : DialogWrapper(project, false) {
    private val mRootPanel: JPanel
    private val templateList = JBList<String>()
    private val languageTextField: LanguageTextField = initLanguageTextField()
    private val clearBtn = JButton("清空")

    private val templateData = mutableListOf<String>()

    init {
        LogUtils.getLogKeys()?.also {
            templateData.addAll(it.reversed())
        }
        mRootPanel = initPanel()
        init()
        templateList.selectedIndex = 0
    }

    override fun createCenterPanel(): JComponent = mRootPanel

    override fun getPreferredFocusedComponent(): JComponent = templateList

    private fun initPanel(): JPanel {
        val rootPanel = JPanel()
        rootPanel.layout = BorderLayout()
        rootPanel.add(initTemplateList(), BorderLayout.WEST)

        rootPanel.add(languageTextField, BorderLayout.CENTER)
        rootPanel.preferredSize = JBDimension(960, 400)
        return rootPanel
    }

    private fun initTemplateList(): JComponent {
        val rootPanel = JPanel()
        rootPanel.layout = BorderLayout()
        rootPanel.border = BorderFactory.createLineBorder(UIColor.LINE_COLOR)

        templateList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        templateList.visibleRowCount = 20
        // top,left,bottom,right
        templateList.border = BorderFactory.createEmptyBorder(0, 10, 0, 10)
        val model = DefaultListModel<String>()
        model.addAll(templateData)
        templateList.model = model

        templateList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3) {
                    val index = templateList.selectedIndex
                    if (index == -1) {
                        return
                    }

                    showMenu(e, index)
                }
            }
        })

        templateList.addListSelectionListener {
            val index = templateList.selectedIndex
            if (index < 0 || index >= templateData.size) {
                languageTextField.document.setReadOnly(false)
                languageTextField.text = ""
                languageTextField.document.setReadOnly(true)
                return@addListSelectionListener
            }

            languageTextField.document.setReadOnly(false)
            languageTextField.text = LogUtils.getLog(templateData[index])
            languageTextField.editor?.scrollingModel?.disableAnimation()
            languageTextField.editor?.scrollingModel?.scroll(0, 0)
            languageTextField.document.setReadOnly(true)
        }

        val scrollPane = JBScrollPane(templateList)
        scrollPane.preferredSize = JBDimension(260, 400)
        scrollPane.border = BorderFactory.createEmptyBorder()
        rootPanel.add(scrollPane, BorderLayout.CENTER)

        val box = Box.createHorizontalBox()
        box.border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(UIColor.LINE_COLOR, 1, 0, 0, 0),
            BorderFactory.createEmptyBorder(0, 5, 0, 5)
        )
        rootPanel.add(box, BorderLayout.SOUTH)

        clearBtn.preferredSize = JBDimension(50, 40)
        clearBtn.addActionListener {
            clear()
        }
        box.add(clearBtn)
        box.add(Box.createHorizontalGlue())
        return rootPanel
    }

    private fun initLanguageTextField(): LanguageTextField {
        val languageTextField = PlainTextLanguageTextField(project, isUseSoftWraps = true)
        languageTextField.autoscrolls = false
        languageTextField.border = BorderFactory.createEmptyBorder()
        languageTextField.editor?.setBorder(BorderFactory.createEmptyBorder())
        languageTextField.preferredSize = JBDimension(700, 400)
        return languageTextField
    }

    private fun showMenu(e: MouseEvent, index: Int) {
        val pop = JBPopupMenu()
        val menu = JBMenuItem("删除")
        menu.addActionListener {
            delete(index)
        }
        pop.add(menu)
        pop.show(e.component, e.x, e.y)
    }

    private fun delete(index: Int) {
        val key = templateData.removeAt(index)
        val model = templateList.model as DefaultListModel<String>
        model.removeElementAt(index)
        LogUtils.delete(key)
    }

    private fun clear() {
        val model = templateList.model as DefaultListModel<String>
        model.removeAllElements()
        LogUtils.clear()
    }
}
