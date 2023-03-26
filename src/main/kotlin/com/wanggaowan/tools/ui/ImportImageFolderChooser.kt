package com.wanggaowan.tools.ui

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.TreeFileChooser
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor
import com.intellij.openapi.fileChooser.ex.RootFileElement
import com.intellij.openapi.fileChooser.impl.FileTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.tree.TreeVisitor.ByComponent
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ArrayUtil
import com.intellij.util.Function
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import com.wanggaowan.tools.utils.msg.Toast
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel
import kotlin.collections.set

/**
 * 导入图片资源后选择导入的目标文件夹弹窗，兼带重命名导入文件名称功能
 *
 * @author Created by wanggaowan on 2022/7/18 08:37
 */
class ImportImageFolderChooser(
    val project: Project,
    title: String,
    private val initialFile: VirtualFile? = null,
    /**
     * 需要重命名的文件
     */
    renameFiles: List<VirtualFile>? = null,
    /**
     * 文件夹过滤器
     */
    private val filter: TreeFileChooser.PsiFileFilter? = null
) : JDialog() {
    private lateinit var myTree: Tree
    private lateinit var mBtnOk: JButton
    private lateinit var mJChosenFolder: JLabel
    private lateinit var mJRenamePanel: JPanel

    /**
     * 切换文件选择/重命名面板的父面板
     */
    private var mCardPane: JPanel

    /**
     * 选中的文件夹
     */
    private var mSelectedFolder: VirtualFile? = null
    private var mOldSelectedFolder: VirtualFile? = null

    private var mBuilder: StructureTreeModel<FileTreeStructure>? = null
    private val mDisableStructureProviders = false
    private val mShowLibraryContents = false
    private var mCardShow = CARD_RENAME
    private var mRenameFileMap = mutableMapOf<String, MutableList<RenameEntity>>()

    /**
     * 确定按钮点击监听
     */
    private var mOkActionListener: (() -> Unit)? = null

    private val dispose = Disposable {
        dispose()
    }

    init {
        mSelectedFolder = initialFile
        setTitle(title)

        val rootPanel = JPanel(BorderLayout())
        contentPane = rootPanel
        val layout = CardLayout()
        mCardPane = JPanel(layout)
        mCardPane.add(createRenameFilePanel(renameFiles), CARD_RENAME)
        mCardPane.add(createFileChoosePanel(), CARD_FILE)
        rootPanel.add(mCardPane, BorderLayout.CENTER)
        rootPanel.add(createAction(), BorderLayout.SOUTH)
        pack()

        if (initialFile != null) {
            mBtnOk.isEnabled = true
            mJChosenFolder.text = initialFile.path.replace(project.basePath ?: "", "")
        } else {
            mBtnOk.isEnabled = false
        }
    }

    override fun setVisible(visible: Boolean) {
        if (visible) {
            val window = WindowManager.getInstance().suggestParentWindow(project)
            window?.let {
                location = Point(it.x + (it.width - this.width) / 2, it.y + (it.height - this.height) / 2)
            }
        }
        super.setVisible(visible)
        if (!visible) {
            dispose.dispose()
        }
    }

    /**
     * 构建文件选择面板
     */
    private fun createFileChoosePanel(): JComponent {
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
        val treeStructure: FileTreeStructure = object : FileTreeStructure(project, descriptor) {
            override fun getChildElements(element: Any): Array<Any> {
                return filterFiles(super.getChildElements(element))
            }
        }

        val model = StructureTreeModel(treeStructure, dispose)
        model.setComparator(AlphaComparator.INSTANCE)
        mBuilder = model
        myTree = Tree(AsyncTreeModel(model, dispose))
        myTree.isRootVisible = false
        myTree.expandRow(0)
        myTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        myTree.cellRenderer = NodeRenderer()
        val scrollPane = ScrollPaneFactory.createScrollPane(myTree)
        scrollPane.preferredSize = JBUI.size(730, 300)
        myTree.addTreeSelectionListener { handleSelectionChanged() }
        return scrollPane
    }

    /**
     * 构建重命名文件面板
     */
    private fun createRenameFilePanel(files: List<VirtualFile>?): JComponent {
        mRenameFileMap.clear()
        files?.forEach {
            val parentName = if (it.path.contains("drawable")) "Drawable"
            else if (it.path.contains("mipmap")) {
                "Mipmap"
            } else {
                null
            }

            if (parentName != null) {
                var list = mRenameFileMap[parentName]
                if (list == null) {
                    list = mutableListOf()
                    mRenameFileMap[parentName] = list
                }

                list.add(RenameEntity(it.name, it, it.name))
            }
        }

        mJRenamePanel = JPanel(GridBagLayout())
        initRenamePanel()
        val scrollPane = ScrollPaneFactory.createScrollPane(mJRenamePanel)
        scrollPane.border = LineBorder(UIConfig.getLineColor(), 0, 0, 1, 0)
        scrollPane.preferredSize = JBUI.size(mJRenamePanel.width, 300)
        return scrollPane
    }

    private fun initRenamePanel() {
        mJRenamePanel.removeAll()
        var depth = 0
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.HORIZONTAL
        c.weightx = 1.0

        mRenameFileMap.forEach {
            val type = JLabel(it.key + "：")
            type.border = BorderFactory.createEmptyBorder(if (depth > 0) 10 else 0, 5, 5, 5)
            val font = type.font
            val fontSize = if (font == null) 16 else font.size + 2
            type.font = Font(null, Font.BOLD, fontSize)
            c.gridy = depth++
            mJRenamePanel.add(type, c)

            val cc = GridBagConstraints()
            cc.fill = GridBagConstraints.HORIZONTAL
            it.value.forEach { it2 ->
                val panel = JPanel(GridBagLayout())
                panel.border = BorderFactory.createEmptyBorder(0, 5, 5, 5)
                c.gridy = depth++
                mJRenamePanel.add(panel, c)

                // 左边部分
                val titleBox = Box.createHorizontalBox()
                titleBox.preferredSize = JBUI.size(250, 35)
                titleBox.minimumSize = JBUI.size(250, 35)
                cc.weightx = 0.0
                panel.add(titleBox, cc)

                val imageView = ImageView(File(it2.oldFile.path), UIConfig.isDarkTheme)
                imageView.preferredSize = JBUI.size(35)
                imageView.maximumSize = JBUI.size(35)
                imageView.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
                titleBox.add(imageView)

                val title = JLabel(it2.oldName + "：")
                titleBox.add(title)

                // 右边部分
                val box = Box.createVerticalBox()
                cc.weightx = 1.0
                panel.add(box, cc)

                val rename = JTextField(it2.newName)
                rename.minimumSize = JBUI.size(0, 35)
                rename.maximumSize = JBUI.size(Int.MAX_VALUE, 35)
                box.add(rename)

                val box2 = Box.createHorizontalBox()
                val (existFile, isInMap) = isImageExist(it2)
                val existFileImageView =
                    ImageView(if (existFile != null) File(existFile.path) else null, UIConfig.isDarkTheme)
                existFileImageView.preferredSize = JBUI.size(25)
                existFileImageView.border = BorderFactory.createEmptyBorder(2, 2, 2, 5)
                existFileImageView.isVisible = existFile != null
                existFileImageView.maximumSize = JBUI.size(25)
                box2.add(existFileImageView)

                val hintStr =
                    if (isInMap) "导入的文件存在相同文件，勾选则导入最后一个同名文件，否则导入第一个同名文件" else "已存在同名文件,是否覆盖原文件？不勾选则跳过导入"
                val hint = JCheckBox(hintStr)
                hint.foreground = JBColor.RED
                val renameFont = rename.font
                hint.font = Font(
                    null,
                    renameFont.style,
                    if (renameFont == null) JBUI.scaleFontSize(12f) else renameFont.size - 2
                )
                it2.existFile = existFile != null
                hint.isVisible = existFile != null
                box2.add(hint)

                box2.add(Box.createHorizontalGlue())
                box.add(box2)

                hint.addChangeListener {
                    it2.coverExistFile = hint.isSelected
                }

                // 文本改变监听
                rename.document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str
                        refreshRenamePanel()
                    }

                    override fun removeUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str
                        refreshRenamePanel()
                    }

                    override fun changedUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str
                        refreshRenamePanel()
                    }
                })
            }
        }

        val placeHolder = JLabel()
        c.weighty = 1.0
        c.gridy = depth++
        mJRenamePanel.add(placeHolder, c)
    }

    private fun refreshRenamePanel() {
        var isDrawable = false
        var index = 0
        for (component in mJRenamePanel.components) {
            if (component is JLabel) {
                // 分类标题
                val value = component.text.trim() == "Drawable："
                if (value != isDrawable) {
                    index = 0
                }
                isDrawable = value
            } else if (component is JPanel) {
                val rightRoot = component.getComponent(1) as Box?
                val rightHintRoot = rightRoot?.getComponent(1) as Box?
                val imageView = rightHintRoot?.getComponent(0) as? ImageView
                val checkBox = rightHintRoot?.getComponent(1) as? JCheckBox
                val key = if (isDrawable) "Drawable" else "Mipmap"
                val values = mRenameFileMap[key]
                if (values != null && index < values.size) {
                    values[index].also { entity ->
                        refreshHintVisible(entity, checkBox, imageView)
                    }
                }
                index++
            }
        }
    }

    private fun refreshHintVisible(
        entity: RenameEntity,
        hint: JCheckBox?,
        imageView: ImageView?
    ) {
        val (existFile2, isInMap) = isImageExist(entity)
        entity.existFile = existFile2 != null
        val hintStr =
            if (isInMap) "导入的文件存在相同文件，勾选则导入最后一个同名文件，否则导入第一个同名文件" else "已存在同名文件,是否覆盖原文件？不勾选则跳过导入"
        hint?.text = hintStr
        val preVisible = hint?.isVisible
        val visible = existFile2 != null
        if (preVisible != visible) {
            hint?.isVisible = visible
            if (existFile2 != null) {
                imageView?.isVisible = true
                imageView?.setImage(File(existFile2.path))
            } else {
                imageView?.isVisible = false
            }

            if (preVisible != null) {
                val resize = JBUI.scale(if (visible) 25 else -25)
                mJRenamePanel.preferredSize = Dimension(mJRenamePanel.width, mJRenamePanel.height + resize)
            }
        }
    }

    /**
     * 构建底部按钮面板
     */
    private fun createAction(): JComponent {
        val bottomPane = Box.createHorizontalBox()
        bottomPane.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        mJChosenFolder = JLabel()
        mJChosenFolder.border = BorderFactory.createEmptyBorder(0, 0, 0, 10)
        bottomPane.add(mJChosenFolder)
        val chooseFolderBtn = JButton("change folder")
        bottomPane.add(chooseFolderBtn)
        bottomPane.add(Box.createHorizontalGlue())
        val cancelBtn = JButton("cancel")
        bottomPane.add(cancelBtn)
        mBtnOk = JButton("import")
        bottomPane.add(mBtnOk)

        chooseFolderBtn.addActionListener {
            if (mCardShow == CARD_RENAME) {
                chooseFolderBtn.isVisible = false
                cancelBtn.isVisible = false
                mBtnOk.text = "ok"
                mCardShow = CARD_FILE
                val layout = mCardPane.layout as CardLayout
                layout.show(mCardPane, CARD_FILE)
                myTree.requestFocus()
                if (mOldSelectedFolder == null && initialFile != null) {
                    selectFolder(initialFile)
                }
                mOldSelectedFolder = mSelectedFolder
            }
        }

        cancelBtn.addActionListener {
            isVisible = false
        }

        mBtnOk.addActionListener {
            if (mCardShow == CARD_FILE) {
                if (mOldSelectedFolder?.path != mSelectedFolder?.path) {
                    // initRenamePanel()
                    refreshRenamePanel()
                }

                chooseFolderBtn.isVisible = true
                cancelBtn.isVisible = true
                mBtnOk.text = "import"
                mCardShow = CARD_RENAME
                val layout = mCardPane.layout as CardLayout
                layout.show(mCardPane, CARD_RENAME)
            } else {
                doOKAction()
            }
        }

        return bottomPane
    }

    private fun doOKAction() {
        if (mSelectedFolder == null || !mSelectedFolder!!.isDirectory) {
            Toast.show(rootPane, MessageType.ERROR, "请选择文件夹")
            return
        }
        isVisible = false
        mOkActionListener?.invoke()
    }

    /**
     * 获取选中的文件夹
     */
    fun getSelectedFolder(): VirtualFile? {
        return mSelectedFolder
    }

    /**
     * 选择文件夹
     */
    private fun selectFolder(file: VirtualFile) {
        TreeUtil.promiseSelect(myTree, object : ByComponent<VirtualFile, DefaultMutableTreeNode>(file, Function {
            if (it !is DefaultMutableTreeNode) {
                return@Function null
            }

            return@Function it
        }) {

            override fun visit(node: DefaultMutableTreeNode?): TreeVisitor.Action {
                if (node == null) {
                    return TreeVisitor.Action.SKIP_CHILDREN
                }

                val userObject = node.userObject ?: return TreeVisitor.Action.SKIP_CHILDREN
                if (userObject !is FileNodeDescriptor) {
                    return TreeVisitor.Action.SKIP_CHILDREN
                }

                val element = userObject.element
                if (element is RootFileElement) {
                    return TreeVisitor.Action.CONTINUE
                }

                val file2 = element.file ?: return TreeVisitor.Action.SKIP_CHILDREN
                val path = file.path
                val path2 = file2.path
                if (path2 == path) {
                    return TreeVisitor.Action.INTERRUPT
                }

                if (path.startsWith(path2)) {
                    return TreeVisitor.Action.CONTINUE
                }

                return TreeVisitor.Action.SKIP_CHILDREN
            }

            override fun contains(pathComponent: DefaultMutableTreeNode, thisComponent: VirtualFile): Boolean {
                return false
            }
        }).onProcessed {

        }
    }

    /**
     * 获取重命名文件Map，key为原始文件名称，value为重命名的值
     */
    fun getRenameFileMap(): Map<String, List<RenameEntity>> {
        return mRenameFileMap
    }

    /**
     * 设置确定按钮点击监听
     */
    fun setOkActionListener(listener: (() -> Unit)?) {
        mOkActionListener = listener
    }

    private fun handleSelectionChanged() {
        mBtnOk.isEnabled = isChosenFolder()
        if (mSelectedFolder == null) {
            mJChosenFolder.text = null
        } else {
            mJChosenFolder.text = mSelectedFolder?.path?.replace(project.basePath ?: "", "")
        }
    }

    private fun isChosenFolder(): Boolean {
        val path = myTree.selectionPath ?: return false
        val node = path.lastPathComponent as DefaultMutableTreeNode
        val userObject = node.userObject as? FileNodeDescriptor ?: return false
        val vFile = userObject.element.file
        return (vFile != null).apply {
            mSelectedFolder = vFile
        }
    }

    private fun filterFiles(list: Array<*>): Array<Any> {
        val condition = Condition { psiFile: PsiFile ->
            if (!psiFile.isDirectory) {
                return@Condition false
            } else if (filter != null && !filter.accept(psiFile)) {
                return@Condition false
            }

            true
        }

        val result: MutableList<Any?> = ArrayList(list.size)
        for (obj in list) {
            val psiFile: PsiFile? = when (obj) {
                is PsiFile -> {
                    obj
                }

                is PsiFileNode -> {
                    obj.value
                }

                else -> {
                    null
                }
            }
            if (psiFile != null && !condition.value(psiFile)) {
                continue
            } else if (obj is ProjectViewNode<*>) {
                if (obj.value !is PsiDirectory) {
                    // 只接收APP模块
                    continue
                }
            }
            result.add(obj)
        }
        return ArrayUtil.toObjectArray(result)
    }

    /**
     * 判断指定图片是否已存在,存在则返回同名文件
     */
    private fun isImageExist(entity: RenameEntity): Pair<VirtualFile?, Boolean> {
        val rootDir = mSelectedFolder?.path ?: return Pair(null, false)
        var selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/${entity.newName}")
        if (selectFile != null) {
            return Pair(selectFile, false)
        }

        selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/1.5x/${entity.newName}")
        if (selectFile != null) {
            return Pair(selectFile, false)
        }

        selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/2.0x/${entity.newName}")
        if (selectFile != null) {
            return Pair(selectFile, false)
        }

        selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/3.0x/${entity.newName}")
        if (selectFile != null) {
            return Pair(selectFile, false)
        }

        selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/4.0x/${entity.newName}")
        if (selectFile != null) {
            return Pair(selectFile, false)
        }

        for (value in mRenameFileMap.values) {
            for (e in value) {
                if (e != entity && entity.newName == e.newName) {
                    return Pair(e.oldFile, true)
                }
            }
        }

        return Pair(null, false)
    }

    companion object {
        /**
         * 文件选择面板
         */
        private const val CARD_FILE = "file"

        /**
         * 文件重命名面板
         */
        private const val CARD_RENAME = "rename"
    }
}

/**
 * 重命名实体
 */
data class RenameEntity(
    /**
     * 导入的原文件名称
     */
    var oldName: String,
    /**
     * 导入的原文件名称
     */
    var oldFile: VirtualFile,
    /**
     * 重命名的名称
     */
    var newName: String,
    /**
     * 存在同名文件
     */
    var existFile: Boolean = false,
    /**
     * 如果存在同名文件，是否覆盖同名文件
     */
    var coverExistFile: Boolean = false
)
