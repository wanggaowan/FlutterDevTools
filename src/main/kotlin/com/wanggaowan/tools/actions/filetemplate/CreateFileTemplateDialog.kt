package com.wanggaowan.tools.actions.filetemplate

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.icons.AllIcons
import com.intellij.json.JsonFileType
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.JBColor
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.LocalTimeCounter
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBDimension
import com.wanggaowan.tools.ui.LineBorder
import com.wanggaowan.tools.ui.UIColor
import com.wanggaowan.tools.ui.language.DartLanguageTextField
import com.wanggaowan.tools.ui.language.JsonLanguageTextField
import com.wanggaowan.tools.ui.language.PlainTextLanguageTextField
import com.wanggaowan.tools.ui.language.YamlLanguageTextField
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.msg.Toast
import icons.DartIcons
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.*
import javax.swing.*
import javax.swing.tree.*

/**
 * 创建文件模版
 *
 * @author Created by wanggaowan on 2023/9/4 13:31
 */
class CreateFileTemplateDialog(val project: Project) : DialogWrapper(project, false) {
    private val mRootPanel: JPanel
    private val templateList = JBList<String>()
    private val templateChildrenTree = Tree(MyMutableTreeNode())
    private var languageTextFieldRoot: JPanel = JPanel(BorderLayout())
    private var languageTextField: LanguageTextField? = null
    private val addTemplateBtn = JButton("+")
    private val importTemplateBtn = JButton("导入")
    private val exportTemplateBtn = JButton("导出")
    private val exportAllTemplateBtn = JButton("导出全部")
    private val applyLanguageTextBtn = JButton("应用")
    private val addTemplateChildBtn = JButton("+")
    private val specialPlaceHolderDescBtn = JButton("特殊占位符说明")

    val templateData = FileTemplateUtils.getTemplateList()

    var selectTemplate: TemplateEntity? = null
        private set

    private var selectTemplateChild: MyMutableTreeNode? = null

    var dataChange: Boolean = false
        private set

    private val placeholders = mutableSetOf<String>()

    var placeholderMap = mutableMapOf<String, String>()
        private set

    var createFileCopy: Boolean = false
        private set

    init {
        mRootPanel = initPanel()
        val doNotAskOption: com.intellij.openapi.ui.DoNotAskOption =
            object : com.intellij.openapi.ui.DoNotAskOption.Adapter() {
                override fun getDoNotShowMessage(): String {
                    return "当创建文件已存在时，是否创建副本"
                }

                override fun shouldSaveOptionsOnCancel(): Boolean {
                    return true
                }

                override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                    if (exitCode == 0 && isSelected) {
                        createFileCopy = true
                    }
                }
            }
        setDoNotAskOption(doNotAskOption)
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

                    showMenu(e, index, selectTemplate!!)
                }
            }
        })

        templateList.addListSelectionListener {
            val index = templateList.selectedIndex
            if (index < 0 || index >= templateData.size) {
                selectTemplate = null
                selectTemplateChild = null
                (templateChildrenTree.model as DefaultTreeModel).setRoot(null)
                return@addListSelectionListener
            }

            selectTemplate = templateData[index]
            selectTemplateChild = null
            val treeModel = (templateChildrenTree.model as DefaultTreeModel)
            val rootNode = MyMutableTreeNode(selectTemplate)
            val firstNode: TreeNode? = if (rootNode.childCount == 0) null else rootNode.firstChild
            treeModel.setRoot(rootNode)
            templateChildrenTree.isRootVisible = selectTemplate?.createFolder == true
            if (firstNode != null) {
                templateChildrenTree.selectionModel.selectionPath = TreePath(treeModel.getPathToRoot(firstNode))
            }
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

        templateChildrenTree.cellRenderer = MyTreeCellRenderer()

        templateChildrenTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3) {
                    if (selectTemplateChild != null) {
                        showChildMenu(e, selectTemplateChild!!)
                    }
                }
            }
        })

        templateChildrenTree.addTreeSelectionListener {
            val node = it.path.lastPathComponent
            if (node !is MyMutableTreeNode) {
                selectTemplateChild = null
                languageTextField?.isVisible = false
                return@addTreeSelectionListener
            }

            selectTemplateChild = node
            val obj = node.userObject
            if (obj == null || obj !is TemplateChildEntity) {
                languageTextField?.isVisible = false
                return@addTreeSelectionListener
            }

            if (!obj.isFolder) {
                if (languageTextField == null) {
                    languageTextField = createLanguageTextField(obj.name)
                    languageTextFieldRoot.add(languageTextField!!, BorderLayout.CENTER)
                } else if (needCreateLanguageTextField(languageTextField!!, obj.name)) {
                    languageTextField = createLanguageTextField(obj.name)
                    languageTextFieldRoot.removeAll()
                    languageTextFieldRoot.add(languageTextField!!, BorderLayout.CENTER)
                } else {
                    languageTextField?.isVisible = true
                }
                languageTextField?.text = obj.content ?: ""
                languageTextField?.editor?.scrollingModel?.disableAnimation()
                languageTextField?.editor?.scrollingModel?.scroll(0, 0)
            } else {
                languageTextField?.isVisible = false
            }
        }

        val scrollPane = JBScrollPane(templateChildrenTree)
        scrollPane.preferredSize = JBDimension(200, 400)
        scrollPane.border = BorderFactory.createEmptyBorder()
        rootPanel.add(scrollPane, BorderLayout.CENTER)

        val box = Box.createHorizontalBox()
        box.border = BorderFactory.createCompoundBorder(
            LineBorder(UIColor.LINE_COLOR, 1, 0, 0, 0),
            BorderFactory.createEmptyBorder(0, 5, 0, 5)
        )
        rootPanel.add(box, BorderLayout.SOUTH)

        box.add(Box.createHorizontalGlue())

        addTemplateChildBtn.preferredSize = JBDimension(40, 40)
        addTemplateChildBtn.addActionListener {
            val treeModel = (templateChildrenTree.model as DefaultTreeModel)
            val root = treeModel.root
            if (root !is MyMutableTreeNode) {
                return@addActionListener
            }
            addTemplateChild(root)
        }
        box.add(addTemplateChildBtn)

        return rootPanel
    }

    private fun initLanguageTextField(): JComponent {
        val rootPanel = JPanel()
        rootPanel.layout = BorderLayout()
        rootPanel.border = LineBorder(UIColor.LINE_COLOR, 1, 0, 1, 1)

        languageTextFieldRoot.preferredSize = JBDimension(500, 400)
        rootPanel.add(languageTextFieldRoot, BorderLayout.CENTER)

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

    private fun createLanguageTextField(fileName: String?): LanguageTextField {
        val suffix = if (fileName.isNullOrEmpty()) "" else {
            val index = fileName.lastIndexOf(".")
            if (index == -1) "" else fileName.substring(index + 1)
        }.lowercase()

        val languageTextField =
            when (suffix) {
                "dart" -> DartLanguageTextField(project)
                "json" -> JsonLanguageTextField(project)
                "yaml" -> YamlLanguageTextField(project)
                else -> PlainTextLanguageTextField(project)
            }

        languageTextField.autoscrolls = false
        languageTextField.border = BorderFactory.createEmptyBorder()
        languageTextField.editor?.setBorder(BorderFactory.createEmptyBorder())
        languageTextField.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                super.documentChanged(event)
                val tempContent = languageTextField.text
                val obj = selectTemplateChild?.userObject
                if (obj == null || obj !is TemplateChildEntity || obj.isFolder) {
                    return
                }

                obj.tempContent = tempContent
                val content = obj.content ?: ""
                applyLanguageTextBtn.isEnabled = tempContent != content
            }
        })
        return languageTextField
    }

    private fun needCreateLanguageTextField(languageTextField: LanguageTextField, fileName: String?): Boolean {
        val suffix = if (fileName.isNullOrEmpty()) "" else {
            val index = fileName.lastIndexOf(".")
            if (index == -1) "" else fileName.substring(index + 1)
        }.lowercase()

        return when (suffix) {
            "dart" -> languageTextField !is DartLanguageTextField
            "json" -> languageTextField !is JsonLanguageTextField
            "yaml" -> languageTextField !is YamlLanguageTextField
            else -> languageTextField !is PlainTextLanguageTextField
        }
    }

    private fun addTemplate() {
        val dialog = InputNameDialog(project, hint = "同时作为目录")
        dialog.show()
        if (dialog.exitCode != OK_EXIT_CODE) {
            return
        }

        dataChange = true
        val name = dialog.textField.text.toString().trim()
        val entity = TemplateEntity()
        entity.name = name
        entity.createFolder = dialog.createFolder.isSelected
        templateData.add(entity)
        val model = templateList.model as DefaultListModel<String>
        model.addElement(name)
        templateList.selectedIndex = model.size() - 1
    }

    private fun deleteTemplate(index: Int, templateEntity: TemplateEntity) {
        dataChange = true
        templateData.remove(templateEntity)
        val model = templateList.model as DefaultListModel<String>
        model.removeElementAt(index)
    }

    private fun addTemplateChild(parentNode: MyMutableTreeNode) {
        val obj = parentNode.userObject ?: return

        val dialog = InputNameDialog(project, hint = "创建目录")
        dialog.show()
        if (dialog.exitCode != OK_EXIT_CODE) {
            return
        }

        dataChange = true
        val name = dialog.textField.text.toString().trim()
        val entity = TemplateChildEntity()
        entity.name = name
        entity.isFolder = dialog.createFolder.isSelected

        var children = obj.children
        if (children == null) {
            children = mutableListOf()
            obj.children = children
        }
        children.add(entity)

        if (parentNode.childCount < children.size) {
            // MyMutableTreeNode的childCount第一次执行会执行插入操作，因此只有数量小于children时才执行插入操作
            // 否则由于children.add(entity)及parentNode.childCount执行的插入操作，会导致重复插入
            val node = MyMutableTreeNode(entity)
            parentNode.add(node)
        }

        val treeModel = (templateChildrenTree.model as DefaultTreeModel)
        val selectionPath = templateChildrenTree.selectionModel.selectionPath
        treeModel.reload(parentNode)
        val path = TreePath(treeModel.getPathToRoot(parentNode))
        if (!templateChildrenTree.isExpanded(path)) {
            templateChildrenTree.expandPath(path)
        }

        if (selectionPath != null) {
            templateChildrenTree.selectionModel.selectionPath = selectionPath
        }
    }

    private fun deleteTemplateChild(node: MyMutableTreeNode) {
        val parent = node.parent ?: return
        if (parent !is MyMutableTreeNode) {
            return
        }

        val totalCount = parent.childCount
        if (totalCount == 0) {
            return
        }

        val obj = parent.userObject ?: return

        val children = obj.children
        if (children.isNullOrEmpty()) {
            return
        }

        dataChange = true
        val index = parent.getIndex(node)
        children.removeAt(index)
        parent.remove(index)
        val treeModel = templateChildrenTree.model as DefaultTreeModel
        treeModel.reload(parent)
    }

    private fun applyTextChange() {
        applyLanguageTextBtn.isEnabled = false
        val obj = selectTemplateChild?.userObject
        if (obj == null || obj !is TemplateChildEntity || obj.isFolder) {
            return
        }

        dataChange = true
        val tempContent = obj.tempContent ?: return
        obj.content = tempContent
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

        parsePlaceHolders(children)

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

    private fun parsePlaceHolders(children: List<TemplateChildEntity>?) {
        if (children.isNullOrEmpty()) {
            return
        }

        children.forEach {
            if (!it.isFolder) {
                val content = it.tempContent ?: it.content ?: ""
                val placeholders = FileTemplateUtils.getPlaceHolders(content)
                this.placeholders.addAll(placeholders)
            } else {
                parsePlaceHolders(it.children)
            }
        }
    }

    private fun showMenu(e: MouseEvent, index: Int, templateEntity: TemplateEntity) {
        val pop = JBPopupMenu()
        var menu = JBMenuItem("重命名")
        menu.addActionListener {
            rename(index, templateEntity)
        }
        pop.add(menu)

        menu = JBMenuItem("删除")
        menu.addActionListener {
            deleteTemplate(index, templateEntity)
        }
        pop.add(menu)

        menu = JBMenuItem("复制模版")
        menu.addActionListener {
            copy(templateEntity)
        }
        pop.add(menu)

        menu = JBMenuItem("移动到第一个")
        menu.addActionListener {
            move(index, true, null)
        }
        pop.add(menu)

        menu = JBMenuItem("移动到最后一个")
        menu.addActionListener {
            move(index, false, null)
        }
        pop.add(menu)

        menu = JBMenuItem("往上移动")
        menu.addActionListener {
            move(index, null, true)
        }
        pop.add(menu)

        menu = JBMenuItem("往下移动")
        menu.addActionListener {
            move(index, null, false)
        }
        pop.add(menu)

        menu = JBMenuItem(if (templateEntity.createFolder) "仅作为分类" else "同时作为目录")
        menu.addActionListener {
            dataChange = true
            templateEntity.createFolder = !templateEntity.createFolder
            templateChildrenTree.isRootVisible = templateEntity.createFolder
        }
        pop.add(menu)

        pop.show(e.component, e.x, e.y)
    }

    private fun showChildMenu(e: MouseEvent, node: MyMutableTreeNode) {
        val pop = JBPopupMenu()

        val obj = node.userObject
        if (obj != null && (obj is TemplateEntity || (obj is TemplateChildEntity && obj.isFolder))) {
            val menu = JBMenuItem("新增")
            menu.addActionListener {
                addTemplateChild(node)
            }
            pop.add(menu)
        }

        var menu = JBMenuItem("重命名")
        menu.addActionListener {
            renameChild(node)
        }
        pop.add(menu)

        menu = JBMenuItem("删除")
        menu.addActionListener {
            deleteTemplateChild(node)
        }
        pop.add(menu)

        menu = JBMenuItem("移动到第一个")
        menu.addActionListener {
            moveChild(node, true, null)
        }
        pop.add(menu)

        menu = JBMenuItem("移动到最后一个")
        menu.addActionListener {
            moveChild(node, false, null)
        }
        pop.add(menu)

        menu = JBMenuItem("往上移动")
        menu.addActionListener {
            moveChild(node, null, true)
        }
        pop.add(menu)

        menu = JBMenuItem("往下移动")
        menu.addActionListener {
            moveChild(node, null, false)
        }
        pop.add(menu)
        pop.show(e.component, e.x, e.y)
    }

    private fun rename(index: Int, templateEntity: TemplateEntity) {
        val dialog = InputNameDialog(project, templateEntity.name ?: "")
        dialog.show()
        if (dialog.exitCode != OK_EXIT_CODE) {
            return
        }

        dataChange = true
        val name = dialog.textField.text.trim()
        templateEntity.name = name
        val model = templateList.model as DefaultListModel<String>
        model.set(index, name)
    }

    private fun renameChild(node: MyMutableTreeNode) {
        val obj = node.userObject
        if (obj !is TemplateChildEntity) {
            return
        }
        val dialog = InputNameDialog(project, obj.name ?: "")
        dialog.show()
        if (dialog.exitCode != OK_EXIT_CODE) {
            return
        }

        dataChange = true
        val name = dialog.textField.text.trim()
        obj.name = name

        val treeModel = (templateChildrenTree.model as DefaultTreeModel)
        treeModel.reload(node)

        if (obj.isFolder) {
            return
        }

        if (selectTemplateChild?.userObject != obj) {
            return
        }

        if (languageTextField == null) {
            return
        }

        if (!needCreateLanguageTextField(languageTextField!!, obj.name)) {
            return
        }

        val scrollingModel = languageTextField?.editor?.scrollingModel
        val verticalScrollOffset = scrollingModel?.verticalScrollOffset
        val horizontalScrollOffset = scrollingModel?.horizontalScrollOffset

        languageTextField = createLanguageTextField(obj.name)
        languageTextFieldRoot.removeAll()
        languageTextFieldRoot.add(languageTextField!!, BorderLayout.CENTER)
        languageTextField?.text = obj.tempContent ?: obj.content ?: ""
        if (verticalScrollOffset != null) {
            languageTextField?.editor?.scrollingModel?.disableAnimation()
            languageTextField?.editor?.scrollingModel?.scroll(horizontalScrollOffset ?: 0, verticalScrollOffset)
        }
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

    private fun moveChild(node: MyMutableTreeNode, first: Boolean?, up: Boolean?) {
        val parent = node.parent ?: return
        if (parent !is MyMutableTreeNode) {
            return
        }

        val totalCount = parent.childCount
        if (totalCount == 0) {
            return
        }

        val obj = parent.userObject ?: return

        val children = obj.children
        if (children.isNullOrEmpty()) {
            return
        }

        val index = parent.getIndex(node)
        if ((first == true || up == true) && index == 0) {
            return
        }

        if ((first == false || up == false) && index == children.size - 1) {
            return
        }

        dataChange = true
        val data = children[index]
        children.removeAt(index)
        parent.remove(index)

        val index2 = if (first != null) {
            if (first) 0 else totalCount - 1
        } else {
            if (up == true) index - 1 else index + 1
        }

        children.add(index2, data)
        parent.insert(node, index2)
        val treeModel = templateChildrenTree.model as DefaultTreeModel
        val path = templateChildrenTree.selectionModel.selectionPath
        treeModel.reload(parent)
        templateChildrenTree.selectionModel.selectionPath = path
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

    private fun copy(templateEntity: TemplateEntity) {
        var entity = templateEntity
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

class InputNameDialog(val project: Project, private val defaultValue: String = "", private val hint: String? = null) :
    DialogWrapper(project, false) {
    private val mRootPanel: JComponent
    val textField = JBTextField()
    val createFolder = JCheckBox()

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

        createFolder.text = hint
        if (!hint.isNullOrEmpty()) {
            rootPanel.add(createFolder, BorderLayout.SOUTH)
        }
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
            if (key == "#DATE#" || key == "#TIME#" || key == "#DATETIME#" || key == "#USER#") {
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
            #DATE#：     当前日期,格式：yyyy-MM-dd
            #TIME#：     当前时间,格式：HH-mm
            #DATETIME#： 当前日期时间,格式：yyyy-MM-dd HH-mm
            #USER#：     当前系统登录的用户
        """.trimIndent()

        rootPanel = JTextArea(desc)
        rootPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        init()
    }

    override fun createCenterPanel(): JComponent = rootPanel
}

class MyMutableTreeNode(template: BaseTemplate? = null) : DefaultMutableTreeNode(template) {

    override fun getChildCount(): Int {
        return if (userObject == null) 0 else if (children != null) {
            children.size
        } else {
            getUserObject()?.children?.forEach {
                val index = if (children == null) 0 else children.size
                insert(MyMutableTreeNode(it), index)
            }

            if (children == null) {
                children = Vector()
                0
            } else {
                children.size
            }
        }
    }

    override fun children(): Enumeration<TreeNode> {
        return if (userObject == null) EMPTY_ENUMERATION else if (children != null) {
            children.elements()
        } else {
            getUserObject()?.children?.forEach {
                val index = if (children == null) 0 else children.size
                insert(MyMutableTreeNode(it), index)
            }

            if (children == null) {
                children = Vector()
                EMPTY_ENUMERATION
            } else {
                children.elements()
            }
        }
    }

    override fun isLeaf(): Boolean {
        return getUserObject()?.children.isNullOrEmpty()
    }

    override fun toString(): String {
        return getUserObject()?.name ?: ""
    }

    override fun getUserObject(): BaseTemplate? {
        return super.getUserObject() as BaseTemplate?
    }
}

class MyTreeCellRenderer : DefaultTreeCellRenderer() {
    init {
        borderSelectionColor = null
    }

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        var obj: BaseTemplate? = null
        if (value is MyMutableTreeNode) {
            obj = value.userObject
        }

        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, false)
        if (leaf) {
            val isFolder = obj is TemplateEntity || (obj is TemplateChildEntity && obj.isFolder)
            if (isFolder) {
                icon = defaultClosedIcon
            } else {
                val fileName = obj?.name
                val suffix = if (fileName.isNullOrEmpty()) "" else {
                    val index = fileName.lastIndexOf(".")
                    if (index == -1) "" else fileName.substring(index + 1)
                }.lowercase()

                when (suffix) {
                    "dart" -> {
                        icon = DartIcons.Dart_file
                    }

                    "json" -> {
                        icon = AllIcons.FileTypes.Json
                    }

                    "yaml" -> {
                        icon = AllIcons.FileTypes.Yaml
                    }

                    else -> {
                        icon = AllIcons.FileTypes.Text
                    }
                }
            }
        }

        return this
    }

    override fun getBackgroundSelectionColor(): Color {
        return JBColor("bgSelect", Color(0x00ffffff, true))
    }

    override fun getBackgroundNonSelectionColor(): Color {
        return JBColor("bgUnSelect", Color(0x00ffffff, true))
    }
}
