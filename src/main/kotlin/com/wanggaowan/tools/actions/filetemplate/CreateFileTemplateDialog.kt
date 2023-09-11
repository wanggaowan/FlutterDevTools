package com.wanggaowan.tools.actions.filetemplate

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.json.JsonFileType
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBMenu
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.LocalTimeCounter
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBDimension
import com.jetbrains.lang.dart.DartFileType
import com.jetbrains.lang.dart.psi.DartFile
import com.wanggaowan.tools.actions.ImportSameImageResAction
import com.wanggaowan.tools.actions.ImportSameImageResUtils
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.ui.DartLanguageTextField
import com.wanggaowan.tools.ui.LineBorder
import com.wanggaowan.tools.ui.UIColor
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.PropertiesSerializeUtils
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.flutterModules
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.msg.Toast
import kotlinx.serialization.json.JsonNull.content
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.preloading.ProfilingInstrumenterExample.e
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

/**
 * 创建文件模版
 *
 * @author Created by wanggaowan on 2023/9/4 13:31
 */
class CreateFileTemplateDialog(val project: Project) : DialogWrapper(project, false) {
    private val mRootPanel: JPanel
    private val templateList = JBList<String>()
    private val templateChildrenList = JBList<String>()
    private val languageTextField = DartLanguageTextField(project)
    private val addTemplateBtn = JButton("+")
    private val importTemplateBtn = JButton("导入")
    private val exportTemplateBtn = JButton("导出")
    private val exportAllTemplateBtn = JButton("导出全部")
    private val addTemplateChildBtn = JButton("+")
    private val applyLanguageTextBtn = JButton("应用")
    private val specialPlaceHolderDescBtn = JButton("特殊占位符说明")

    val templateData = FileTemplateUtils.getTemplateList()

    var selectTemplate: TemplateEntity? = null
        private set

    private var selectTemplateChild: TemplateChildEntity? = null

    var dataChange: Boolean = false
        private set

    private val placeholders = mutableSetOf<String>()

    var placeholderMap = mutableMapOf<String, String>()
        private set

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

        centerRootPanel.add(initLanguageTextField(), BorderLayout.CENTER)

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
        model.addAll(templateData.map { it.name })
        templateList.model = model

        templateList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3) {
                    val index = templateList.selectedIndex
                    if (index == -1) {
                        return
                    }

                    showMenu(e, false, index)
                }
            }
        })

        templateList.addListSelectionListener {
            val index = templateList.selectedIndex
            if (index < 0 || index >= templateData.size) {
                selectTemplate = null
                selectTemplateChild = null
                (templateChildrenList.model as DefaultListModel<String>).removeAllElements()
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
        scrollPane.preferredSize = JBDimension(260, 400)
        scrollPane.border = BorderFactory.createEmptyBorder()
        rootPanel.add(scrollPane, BorderLayout.CENTER)

        val box = Box.createHorizontalBox()
        box.border = BorderFactory.createCompoundBorder(
            LineBorder(UIColor.LINE_COLOR, 1, 0, 0, 0),
            BorderFactory.createEmptyBorder(0, 5, 0, 5)
        )
        rootPanel.add(box, BorderLayout.SOUTH)

        importTemplateBtn.preferredSize = JBDimension(50, 40)
        importTemplateBtn.addActionListener {
            chooseFolder(0, null)
        }
        box.add(importTemplateBtn)

        exportTemplateBtn.preferredSize = JBDimension(50, 40)
        exportTemplateBtn.addActionListener {
            if (selectTemplate == null) {
                Toast.show(exportTemplateBtn, MessageType.WARNING, "请选择要导出的模版")
            } else {
                chooseFolder(1, selectTemplate)
            }
        }
        box.add(exportTemplateBtn)

        exportAllTemplateBtn.preferredSize = JBDimension(80, 40)
        exportAllTemplateBtn.addActionListener {
            chooseFolder(2, null)
        }
        box.add(exportAllTemplateBtn)

        box.add(Box.createHorizontalGlue())

        addTemplateBtn.preferredSize = JBDimension(40, 40)
        addTemplateBtn.addActionListener {
            addTemplate()
        }
        box.add(addTemplateBtn)

        return rootPanel
    }

    private fun initTemplateChildrenList(): JComponent {
        val rootPanel = JPanel()
        rootPanel.layout = BorderLayout()
        rootPanel.border = LineBorder(UIColor.LINE_COLOR, 1, 0, 1, 1)

        templateChildrenList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        templateChildrenList.visibleRowCount = 20
        templateChildrenList.border = BorderFactory.createEmptyBorder(0, 10, 0, 10)
        val model = DefaultListModel<String>()
        templateChildrenList.model = model

        templateChildrenList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3) {
                    val index = templateChildrenList.selectedIndex
                    if (index == -1) {
                        return
                    }

                    showMenu(e, true, index)
                }
            }
        })

        templateChildrenList.addListSelectionListener {
            if (selectTemplate == null) {
                selectTemplateChild = null
                languageTextField.text = ""
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
            languageTextField.autoscrolls = false
        }

        val scrollPane = JBScrollPane(templateChildrenList)
        scrollPane.preferredSize = JBDimension(200, 400)
        scrollPane.border = BorderFactory.createEmptyBorder()
        rootPanel.add(scrollPane, BorderLayout.CENTER)

        val box = Box.createHorizontalBox()
        box.border = BorderFactory.createCompoundBorder(
            LineBorder(UIColor.LINE_COLOR, 1, 0, 0, 0),
            BorderFactory.createEmptyBorder(0, 10, 0, 10)
        )
        rootPanel.add(box, BorderLayout.SOUTH)

        box.add(Box.createHorizontalGlue())

        addTemplateChildBtn.preferredSize = JBDimension(40, 40)
        addTemplateChildBtn.addActionListener {
            addTemplateChild()
        }
        box.add(addTemplateChildBtn)

        return rootPanel
    }

    private fun initLanguageTextField(): JComponent {
        val rootPanel = JPanel()
        rootPanel.layout = BorderLayout()
        rootPanel.border = LineBorder(UIColor.LINE_COLOR, 1, 0, 1, 1)

        languageTextField.preferredSize = JBDimension(500, 400)
        languageTextField.border = BorderFactory.createEmptyBorder()
        languageTextField.editor?.setBorder(BorderFactory.createEmptyBorder())
        languageTextField.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                super.documentChanged(event)
                val tempContent = languageTextField.text
                selectTemplateChild?.tempContent = tempContent
                val content = selectTemplateChild?.content ?: ""
                applyLanguageTextBtn.isEnabled = tempContent != content
            }
        })

        rootPanel.add(languageTextField, BorderLayout.CENTER)

        val box = Box.createHorizontalBox()
        box.border = BorderFactory.createCompoundBorder(
            LineBorder(UIColor.LINE_COLOR, 1, 0, 0, 0),
            BorderFactory.createEmptyBorder(0, 5, 0, 5)
        )
        rootPanel.add(box, BorderLayout.SOUTH)

        specialPlaceHolderDescBtn.preferredSize = JBDimension(120, 40)
        specialPlaceHolderDescBtn.addActionListener {
            SpecialPlaceHolderDescDialog(project).show()
        }
        box.add(specialPlaceHolderDescBtn)

        box.add(Box.createHorizontalGlue())

        applyLanguageTextBtn.preferredSize = JBDimension(50, 40)
        applyLanguageTextBtn.addActionListener {
            applyTextChange()
        }
        applyLanguageTextBtn.isEnabled = false
        box.add(applyLanguageTextBtn)

        return rootPanel
    }

    private fun addTemplate() {
        val dialog = InputNameDialog(project)
        dialog.show()
        if (dialog.exitCode != OK_EXIT_CODE) {
            return
        }

        dataChange = true
        val name = dialog.textField.text.toString().trim()
        val entity = TemplateEntity()
        entity.name = name
        templateData.add(entity)
        val model = templateList.model as DefaultListModel<String>
        model.addElement(name)
        templateList.selectedIndex = model.size() - 1
    }

    private fun deleteTemplate() {
        if (selectTemplate == null) {
            return
        }

        val index = templateList.selectedIndex
        if (index == -1) {
            return
        }

        dataChange = true
        templateData.remove(selectTemplate)
        val model = templateList.model as DefaultListModel<String>
        model.removeElementAt(index)
    }

    private fun addTemplateChild() {
        if (selectTemplate == null) {
            return
        }

        val dialog = InputNameDialog(project)
        dialog.show()
        if (dialog.exitCode != OK_EXIT_CODE) {
            return
        }

        dataChange = true
        val name = dialog.textField.text.toString().trim()
        val entity = TemplateChildEntity()
        entity.name = name

        var children = selectTemplate?.children
        if (children == null) {
            children = mutableListOf()
            selectTemplate?.children = children
        }
        children.add(entity)

        val model = templateChildrenList.model as DefaultListModel<String>
        model.addElement(name)
        templateChildrenList.selectedIndex = model.size() - 1
    }

    private fun deleteTemplateChild() {
        if (selectTemplate == null || selectTemplateChild == null) {
            return
        }

        val index = templateChildrenList.selectedIndex
        if (index == -1) {
            return
        }

        dataChange = true
        selectTemplate?.children?.remove(selectTemplateChild)
        val model = templateChildrenList.model as DefaultListModel<String>
        model.removeElementAt(index)
    }

    private fun applyTextChange() {
        applyLanguageTextBtn.isEnabled = false
        if (selectTemplateChild == null) {
            return
        }

        dataChange = true
        val tempContent = selectTemplateChild?.tempContent ?: return
        selectTemplateChild?.content = tempContent
    }

    override fun doOKAction() {
        placeholders.clear()
        placeholderMap.clear()
        val template = selectTemplate
        if (template == null) {
            super.doOKAction()
            return
        }

        val children = template.children
        if (children.isNullOrEmpty()) {
            super.doOKAction()
            return
        }

        children.forEach {
            val content = it.tempContent ?: it.content ?: ""
            val placeholders = FileTemplateUtils.getPlaceHolders(content)
            this.placeholders.addAll(placeholders)
        }

        if (placeholders.isEmpty()) {
            super.doOKAction()
            return
        }

        val dialog = InputPlaceHolderDialog(project, placeholders)
        dialog.show()
        if (dialog.exitCode != OK_EXIT_CODE) {
            return
        }
        placeholderMap = dialog.placeholderMap
        super.doOKAction()
    }

    private fun showMenu(e: MouseEvent, isChild: Boolean, index: Int) {
        val pop = JBPopupMenu()
        var menu = JBMenuItem("重命名")
        menu.addActionListener {
            rename(isChild, index)
        }
        pop.add(menu)

        menu = JBMenuItem("删除")
        menu.addActionListener {
            if (isChild) {
                deleteTemplateChild()
            } else {
                deleteTemplate()
            }
        }
        pop.add(menu)

        if (!isChild) {
            menu = JBMenuItem("复制模版")
            menu.addActionListener {
                copy()
            }
            pop.add(menu)
        }

        menu = JBMenuItem("移动到第一个")
        menu.addActionListener {
            if (isChild) {
                moveChild(index, true, null)
            } else {
                move(index, true, null)
            }
        }
        pop.add(menu)

        menu = JBMenuItem("移动到最后一个")
        menu.addActionListener {
            if (isChild) {
                moveChild(index, false, null)
            } else {
                move(index, false, null)
            }
        }
        pop.add(menu)

        menu = JBMenuItem("往上移动")
        menu.addActionListener {
            if (isChild) {
                moveChild(index, null, true)
            } else {
                move(index, null, true)
            }
        }
        pop.add(menu)

        menu = JBMenuItem("往下移动")
        menu.addActionListener {
            if (isChild) {
                moveChild(index, null, false)
            } else {
                move(index, null, false)
            }
        }
        pop.add(menu)
        pop.show(e.component, e.x, e.y)
    }

    private fun rename(isChild: Boolean, index: Int) {
        if (isChild) {
            if (selectTemplateChild == null) {
                return
            }
            val dialog = InputNameDialog(project, selectTemplateChild?.name ?: "")
            dialog.show()
            if (dialog.exitCode != OK_EXIT_CODE) {
                return
            }

            dataChange = true
            val name = dialog.textField.text.trim()
            selectTemplateChild?.name = name
            val model = templateChildrenList.model as DefaultListModel<String>
            model.set(index, name)
            return
        }

        if (selectTemplate == null) {
            return
        }

        val dialog = InputNameDialog(project, selectTemplate?.name ?: "")
        dialog.show()
        if (dialog.exitCode != OK_EXIT_CODE) {
            return
        }

        dataChange = true
        val name = dialog.textField.text.trim()
        selectTemplate?.name = name
        val model = templateList.model as DefaultListModel<String>
        model.set(index, name)
    }

    private fun move(index: Int, first: Boolean?, up: Boolean?) {
        if (templateData.isEmpty()) {
            return
        }

        if ((first == true || up == true) && index == 0) {
            return
        }

        if ((first == false || up == false) && index == templateData.size - 1) {
            return
        }

        dataChange = true
        val data = templateData[index]
        templateData.removeAt(index)

        val model = templateList.model as DefaultListModel<String>
        val str = model.elementAt(index)
        model.remove(index)

        val index2 = if (first != null) {
            if (first) 0 else model.size()
        } else {
            if (up == true) index - 1 else index + 1
        }

        templateData.add(index2, data)
        model.add(index2, str)
        templateList.selectedIndex = index2
    }

    private fun moveChild(index: Int, first: Boolean?, up: Boolean?) {
        val children = selectTemplate?.children
        if (children.isNullOrEmpty()) {
            return
        }

        if ((first == true || up == true) && index == 0) {
            return
        }

        if ((first == false || up == false) && index == children.size - 1) {
            return
        }

        dataChange = true
        val data = children[index]
        children.removeAt(index)

        val model = templateChildrenList.model as DefaultListModel<String>
        val str = model.elementAt(index)
        model.remove(index)

        val index2 = if (first != null) {
            if (first) 0 else model.size()
        } else {
            if (up == true) index - 1 else index + 1
        }

        children.add(index2, data)
        model.add(index2, str)
        templateChildrenList.selectedIndex = index2
    }

    // 0: 导入，1：导出，2：导出全部
    private fun chooseFolder(type: Int, templateEntity: TemplateEntity?) {
        val descriptor = if (type == 0) {
            FileChooserDescriptor(
                true, false, false,
                false, false, true
            )
        } else {
            FileChooserDescriptor(
                false, true, false,
                false, false, false
            )
        }
        FileChooser.chooseFiles(descriptor, project, null) { files ->
            if (files.isNullOrEmpty()) {
                return@chooseFiles
            }

            if (type == 0) {
                import(files)
                return@chooseFiles
            }

            if (files.size > 1) {
                return@chooseFiles
            }

            val file = files[0]
            if (type == 1) {
                export(templateEntity!!, file)
            } else {
                exportAll(file)
            }
        }
    }

    private fun import(files: List<VirtualFile>) {
        val gson = Gson()
        val importList = mutableListOf<TemplateEntity>()
        files.forEach {
            if (!it.isDirectory) {
                val psiFile = it.toPsiFile(project)
                if (psiFile != null) {
                    val text = psiFile.text ?: ""
                    try {
                        val template = gson.fromJson(psiFile.text, TemplateEntity::class.java)
                        importList.add(template)
                    } catch (e: Exception) {
                        try {
                            val type = object : TypeToken<List<TemplateEntity>>() {}.type
                            val value: List<TemplateEntity>? = Gson().fromJson(text, type)
                            if (value != null) {
                                importList.addAll(value)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        if (importList.isEmpty()) {
            return
        }

        dataChange = true
        templateData.addAll(importList)
        val model = templateList.model as DefaultListModel<String>
        model.addAll(importList.map { it.name })
    }

    private fun copy() {
        var entity = selectTemplate ?: return
        val dialog = InputNameDialog(project)
        dialog.show()
        if (dialog.exitCode != OK_EXIT_CODE) {
            return
        }

        try {
            dataChange = true
            val gson = Gson()
            entity = gson.fromJson(gson.toJson(entity), TemplateEntity::class.java)
            entity.name = dialog.textField.text.trim()
            templateData.add(entity)
            val model = templateList.model as DefaultListModel<String>
            model.addElement(entity.name)
        } catch (e: Exception) {
            NotificationUtils.showBalloonMsg(project, "复制失败", NotificationType.ERROR)
        }
    }

    private fun export(templateEntity: TemplateEntity, dir: VirtualFile) {
        val psiFileFactory = dir.toPsiDirectory(project) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val fileName = getFileName(dir, 0)
            val content = Gson().toJson(templateEntity)
            try {
                val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                    fileName, JsonFileType.INSTANCE, content, LocalTimeCounter.currentTime(), false
                )
                psiFileFactory.add(psiFile)
                NotificationUtils.showBalloonMsg(
                    project, "已导出到：${dir.path + File.separator + fileName}",
                    NotificationType.INFORMATION
                )
            } catch (e: Exception) {
                NotificationUtils.showBalloonMsg(project, "导出失败", NotificationType.ERROR)
            }
        }
    }

    private tailrec fun getFileName(dir: VirtualFile, index: Int): String {
        val suffix = if (index == 0) "" else "($index)"
        val fileName = "template$suffix.${JsonFileType.INSTANCE.defaultExtension}"
        if (dir.findChild(fileName) == null) {
            return fileName
        }

        if (index > 100) {
            val suffix2 = UUID.randomUUID().toString()
            return "template_$suffix2.${JsonFileType.INSTANCE.defaultExtension}"
        }

        return getFileName(dir, index + 1)
    }

    private fun exportAll(dir: VirtualFile) {
        val psiFileFactory = dir.toPsiDirectory(project) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val fileName = getFileName(dir, 0)
            val content = Gson().toJson(templateData)
            try {
                val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                    fileName, JsonFileType.INSTANCE, content, LocalTimeCounter.currentTime(), false
                )

                psiFileFactory.add(psiFile)
                NotificationUtils.showBalloonMsg(
                    project, "已导出到：${dir.path + File.separator + fileName}",
                    NotificationType.INFORMATION
                )
            } catch (e: Exception) {
                NotificationUtils.showBalloonMsg(project, "导出失败", NotificationType.ERROR)
            }
        }
    }
}

class InputNameDialog(val project: Project, private val defaultValue: String = "") : DialogWrapper(project, false) {
    private val mRootPanel: JComponent
    val textField = JBTextField()

    override fun createCenterPanel(): JComponent = mRootPanel

    override fun getPreferredFocusedComponent(): JComponent = textField

    init {
        mRootPanel = initPanel()
        init()
    }

    private fun initPanel(): JComponent {
        val rootPanel = JPanel()
        rootPanel.layout = BorderLayout()

        val label = JLabel("输入名称：")
        label.border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
        rootPanel.add(label, BorderLayout.NORTH)

        textField.preferredSize = Dimension(180, 30)
        textField.minimumSize = Dimension(180, 30)
        textField.text = defaultValue
        rootPanel.add(textField, BorderLayout.CENTER)

        return rootPanel
    }

    override fun doOKAction() {
        val content = textField.text.toString().trim()
        if (content.isEmpty()) {
            Toast.show(textField, MessageType.WARNING, "内容不能为空")
            return
        }

        super.doOKAction()
    }
}

class InputPlaceHolderDialog(val project: Project, placeHolders: Set<String>) : DialogWrapper(project, false) {

    private val rootPanel: JComponent
    private var focusComponent: JComponent? = null

    val placeholderMap = mutableMapOf<String, String>()

    init {
        placeHolders.forEach {
            placeholderMap[it] = ""
        }

        rootPanel = createRootPanel()
        init()
    }

    override fun createCenterPanel(): JComponent = rootPanel

    override fun getPreferredFocusedComponent(): JComponent? {
        return focusComponent
    }

    private fun createRootPanel(): JComponent {
        val builder = FormBuilder.createFormBuilder()
        builder.addComponent(JLabel("输入占位符内容："))
        for (key in placeholderMap.keys) {
            if (key == "${'$'}DATE${'$'}"
                || key == "${'$'}TIME${'$'}"
                || key == "${'$'}DATETIME${'$'}"
                || key == "${'$'}USER${'$'}"
            ) {
                continue
            }

            val title = JBLabel("${key}:")
            val content = JBTextField()
            content.preferredSize = Dimension(180, 30)
            content.minimumSize = Dimension(180, 30)
            if (focusComponent == null) {
                focusComponent = content
            }

            content.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(event: javax.swing.event.DocumentEvent) {
                    placeholderMap[key] = content.text.toString().trim()
                }

                override fun removeUpdate(event: javax.swing.event.DocumentEvent) {
                    placeholderMap[key] = content.text.toString().trim()
                }

                override fun changedUpdate(event: javax.swing.event.DocumentEvent) {
                    placeholderMap[key] = content.text.toString().trim()
                }
            })

            builder.addLabeledComponent(title, content, 1, false)
        }

        return builder.addComponentFillVertically(JPanel(), 0).panel
    }
}

class SpecialPlaceHolderDescDialog(val project: Project) : DialogWrapper(project, false) {

    private val rootPanel: JTextArea

    init {
        val desc = """
            ${'$'}DATE${'$'}：     当前日期,格式：yyyy-MM-dd
            ${'$'}TIME${'$'}：     当前时间,格式：HH-mm
            ${'$'}DATETIME${'$'}： 当前日期时间,格式：yyyy-MM-dd HH-mm
            ${'$'}USER${'$'}：     当前系统登录的用户
        """.trimIndent()

        rootPanel = JTextArea(desc)
        rootPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        init()
    }

    override fun createCenterPanel(): JComponent = rootPanel
}

