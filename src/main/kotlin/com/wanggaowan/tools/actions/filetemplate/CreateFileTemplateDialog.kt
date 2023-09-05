package com.wanggaowan.tools.actions.filetemplate

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBDimension
import com.wanggaowan.tools.ui.DartLanguageTextField
import com.wanggaowan.tools.ui.LineBorder
import com.wanggaowan.tools.ui.UIColor
import org.jetbrains.kotlin.idea.gradleTooling.get
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * 创建文件模版
 *
 * @author Created by wanggaowan on 2023/9/4 13:31
 */
class CreateFileTemplateDialog(val project: Project) : DialogWrapper(project, false) {
    private var mRootPanel: JPanel
    private var templateList = JBList<String>()
    private var templateChildrenList = JBList<String>()
    private var languageTextField = DartLanguageTextField(project)
    private val templateData = FileTemplateUtils.getTemplateList()

    private var selectTemplate: Template? = null
    private var selectTemplateChild: TemplateChild? = null
    private var dataChange: Boolean = false

    init {
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

        val centerRootPanel = JPanel()
        centerRootPanel.layout = BorderLayout()
        centerRootPanel.preferredSize = JBDimension(700, 400)
        rootPanel.add(centerRootPanel, BorderLayout.CENTER)

        centerRootPanel.add(initTemplateChildrenList(), BorderLayout.WEST)

        languageTextField.preferredSize = JBDimension(500, 400)
        languageTextField.border = BorderFactory.createEmptyBorder()
        languageTextField.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                super.documentChanged(event)
                dataChange = true
                selectTemplateChild?.tempContent = languageTextField.text
            }
        })
        centerRootPanel.add(languageTextField, BorderLayout.CENTER)

        return rootPanel
    }

    private fun initTemplateList(): JComponent {
        val rootPanel = JPanel()
        rootPanel.layout = BorderLayout()
        rootPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColor.LINE_COLOR),
            BorderFactory.createEmptyBorder(0, 2, 0, 2),
        )

        templateList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        templateList.visibleRowCount = 20
        templateList.border = BorderFactory.createEmptyBorder()
        val model = DefaultListModel<String>()
        model.addAll(templateData.map { it.name })
        templateList.model = model

        templateList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3) {
                    println("鼠标右键按下")
                }
            }
        })

        templateList.addListSelectionListener {
            val index = templateList.selectedIndex
            if (index < 0 || index >= templateData.size) {
                selectTemplate = null
                selectTemplateChild = null
                return@addListSelectionListener
            }

            selectTemplate = templateData[index]
            val children = selectTemplate?.children?.map { it.name }
            val childModel = templateChildrenList.model as DefaultListModel<String>
            childModel.removeAllElements()
            if (children != null) {
                childModel.addAll(children)
            }
            templateChildrenList.selectedIndex = 0
        }

        val scrollPane = JBScrollPane(templateList)
        scrollPane.preferredSize = JBDimension(200, 400)
        rootPanel.add(scrollPane, BorderLayout.CENTER)

        val box = Box.createHorizontalBox()
        box.border = LineBorder(UIColor.LINE_COLOR, 1, 0, 0, 0)
        box.add(Box.createHorizontalGlue())
        rootPanel.add(box, BorderLayout.SOUTH)

        val addBtn = JButton("+")
        addBtn.preferredSize = JBDimension(40, 40)
        box.add(addBtn)

        val deleteBtn = JButton("-")
        deleteBtn.preferredSize = JBDimension(40, 40)
        box.add(deleteBtn)

        return rootPanel
    }

    private fun initTemplateChildrenList(): JComponent {
        val rootPanel = JPanel()
        rootPanel.layout = BorderLayout()
        rootPanel.border = BorderFactory.createCompoundBorder(
            LineBorder(UIColor.LINE_COLOR, 1, 0, 1, 1),
            BorderFactory.createEmptyBorder(0, 2, 0, 2)
        )

        templateChildrenList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        templateChildrenList.visibleRowCount = 20
        templateChildrenList.border = BorderFactory.createEmptyBorder()
        val model = DefaultListModel<String>()
        templateChildrenList.model = model

        templateChildrenList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3) {
                    println("鼠标右键按下")
                } else {

                }
            }
        })

        templateChildrenList.addListSelectionListener {
            if (selectTemplate == null) {
                selectTemplateChild = null
                return@addListSelectionListener
            }

            val children = selectTemplate!!.children
            if (children.isNullOrEmpty()) {
                selectTemplateChild = null
                languageTextField.text = ""
                return@addListSelectionListener
            }

            val index2 = templateChildrenList.selectedIndex
            if (index2 < 0 || index2 >= children.size) {
                selectTemplateChild = null
                languageTextField.text = ""
                return@addListSelectionListener
            }

            selectTemplateChild = children[index2]
            languageTextField.text = selectTemplateChild?.content ?: ""
        }

        val scrollPane = JBScrollPane(templateChildrenList)
        scrollPane.preferredSize = JBDimension(200, 400)
        rootPanel.add(scrollPane, BorderLayout.CENTER)

        val box = Box.createHorizontalBox()
        box.border = LineBorder(UIColor.LINE_COLOR, 1, 0, 0, 0)
        box.add(Box.createHorizontalGlue())
        rootPanel.add(box, BorderLayout.SOUTH)

        val addBtn = JButton("+")
        addBtn.preferredSize = JBDimension(40, 40)
        box.add(addBtn)

        val deleteBtn = JButton("-")
        deleteBtn.preferredSize = JBDimension(40, 40)
        box.add(deleteBtn)

        return rootPanel
    }

    fun getTemplateData(): List<Template> {
        return templateData
    }

    fun getSelectTemplate(): Template? {
        return selectTemplate
    }
}

class Template {
    var name: String? = null
    var children: List<TemplateChild>? = null
}

class TemplateChild {
    var name: String? = null
    var content: String? = null

    @Transient
    var tempContent: String? = null
}

