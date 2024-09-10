package com.wanggaowan.tools.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.Gray
import com.intellij.ui.SingleSelectionModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.wanggaowan.tools.entity.Property
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.ui.icon.IconPanel
import com.wanggaowan.tools.utils.PropertiesSerializeUtils
import com.wanggaowan.tools.utils.ex.basePath
import icons.FlutterDevToolsIcons
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.properties.Delegates

/**
 * ImagePreviewPanel的另一种实现，采用JBList实现，JBList同样支持网格，但是列数量无法精准控制，暂时放在这里做一个参考
 *
 * @author Created by wanggaowan on 2024/2/21 17:38
 */
class ImagePreviewPanel(val module: Module) : JPanel(), Disposable {

    // 搜素布局相关View
    private lateinit var mSearchPanel: JPanel
    private lateinit var mSearchBtn: ImageButton
    private lateinit var mClearBtn: ImageButton
    private lateinit var mSearchTextField: JTextField

    private lateinit var mScrollPane: JBScrollPane
    private lateinit var mImagePanel: JBList<Property>
    private val myListModel = MyListModel()

    // 底部布局相关View
    private lateinit var mRootPathJPanel: JPanel
    private lateinit var mRootPathLabel: MyLabel
    private lateinit var mChangeBtn: JButton

    // 网格展示模式时图片布局宽度
    private val mGridImageLayoutWidth = 160

    // 当前布局模式
    private var isGridMode: Boolean by Delegates.observable(false) { _, _, _ ->
        mImagePanel.clearSelection()
        setImageLayout()
    }

    // 需要展示的图片路径
    private var mImages: Set<Property>? = null

    // 默认预览图片文件夹
    private var mRootFilePath: String? = null

    private val defaultCoroutineScope = CoroutineScope(Dispatchers.Default)
    private val mainCoroutineScope = CoroutineScope(Dispatchers.Main)
    private var mGetImageJob: Job? = null
    private var mSingleClickOpenFile: Boolean = false
    private var mSelectedImage: Property? = null

    init {
        Disposer.register(this, UiNotifyConnector.installOn(this, object : Activatable {
            override fun hideNotify() {}

            override fun showNotify() {
                updateNewImage()
            }
        }))

        layout = BorderLayout()
        preferredSize = JBUI.size(320, 100)
        initRootPath()
        initPanel()
    }

    private fun initRootPath() {
        val basePath = module.basePath
        if (basePath.isNullOrEmpty()) {
            mRootFilePath = null
            return
        }

        val rootPath = PropertiesSerializeUtils.getString(module.project, ROOT_PATH, "")
        mRootFilePath = if (rootPath.isNotEmpty()) {
            if (File(rootPath).exists()) {
                rootPath
            } else {
                "${basePath}/${PluginSettings.getImagesFileDir(module.project)}"
            }
        } else {
            "${basePath}/${PluginSettings.getImagesFileDir(module.project)}"
        }
    }

    private fun initPanel() {
        val topPanel = JPanel()
        topPanel.layout = BorderLayout()
        add(topPanel, BorderLayout.NORTH)

        initSearchLayout(topPanel)
        initBottomLayout()

        // 展示Image预览内容的面板
        mImagePanel = JBList(myListModel)
        mImagePanel.background = UIColor.BG_COLOR
        mImagePanel.selectionModel = SingleSelectionModel()
        mImagePanel.visibleRowCount = 0
        setImageLayout()

        mImagePanel.setCellRenderer { _, _, index, selected, focused ->
            return@setCellRenderer getPreviewItemPanel(index, isGridMode, selected || focused)
        }

        mImagePanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3) {
                    // 鼠标中间点击
                    mSingleClickOpenFile = false
                    return
                }

                if (e.clickCount > 1) {
                    if (!mSingleClickOpenFile) {
                        mSelectedImage?.also {
                            openFile(it)
                        }
                    }
                    mSingleClickOpenFile = false
                    return
                }

                mSingleClickOpenFile = false
                val index = mImagePanel.selectionModel.anchorSelectionIndex
                val image = myListModel.getData(index) ?: return
                if (image == mSelectedImage) {
                    mSingleClickOpenFile = true
                    openFile(image)
                }
                mSelectedImage = image
            }
        })

        mScrollPane = JBScrollPane(mImagePanel)
        mScrollPane.background = null
        mScrollPane.border = JBUI.Borders.customLine(UIColor.LINE_COLOR, 0, 0, 1, 0)
        add(mScrollPane, BorderLayout.CENTER)
    }

    /**
     * 初始化搜索界面布局
     */
    private fun initSearchLayout(parent: JPanel) {
        // 搜索一栏根布局
        mSearchPanel = JPanel()
        mSearchPanel.background = UIColor.BG_COLOR
        mSearchPanel.layout = BoxLayout(mSearchPanel, BoxLayout.X_AXIS)
        mSearchPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(UIColor.LINE_COLOR, 0, 0, 1, 0),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ), BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true)
        )
        parent.add(mSearchPanel, BorderLayout.NORTH)

        mSearchBtn = ImageButton(FlutterDevToolsIcons.search)
        mSearchBtn.preferredSize = Dimension(30, 30)
        mSearchBtn.minimumSize = mSearchBtn.preferredSize
        mSearchBtn.maximumSize = mSearchBtn.preferredSize
        mSearchBtn.background = UIColor.TRANSPARENT
        mSearchPanel.add(mSearchBtn)

        mSearchTextField = JTextField()
        mSearchTextField.preferredSize = Dimension(100, 30)
        mSearchTextField.minimumSize = Dimension(100, 30)
        mSearchTextField.background = UIColor.TRANSPARENT
        mSearchTextField.border = BorderFactory.createEmptyBorder()
        mSearchTextField.isOpaque = true
        mSearchTextField.addFocusListener(object : FocusListener {
            override fun focusGained(p0: FocusEvent?) {
                mSearchPanel.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createCompoundBorder(
                        JBUI.Borders.customLine(UIColor.LINE_COLOR, 0, 0, 1, 0),
                        BorderFactory.createEmptyBorder(9, 9, 9, 9)
                    ), BorderFactory.createLineBorder(UIColor.INPUT_FOCUS_COLOR, 2, true)
                )
            }

            override fun focusLost(p0: FocusEvent?) {
                mSearchPanel.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createCompoundBorder(
                        JBUI.Borders.customLine(UIColor.LINE_COLOR, 0, 0, 1, 0),
                        BorderFactory.createEmptyBorder(10, 10, 10, 10)
                    ), BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true)
                )
            }
        })

        mSearchPanel.add(mSearchTextField)

        mClearBtn = ImageButton(FlutterDevToolsIcons.close, arcSize = 100)
        mClearBtn.preferredSize = Dimension(30, 30)
        mClearBtn.minimumSize = mSearchBtn.preferredSize
        mClearBtn.maximumSize = mSearchBtn.preferredSize
        mClearBtn.background = null
        mClearBtn.isVisible = false
        mClearBtn.setBorderWidth(7)
        mClearBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                mClearBtn.background = UIColor.MOUSE_ENTER_COLOR2
                mClearBtn.icon = FlutterDevToolsIcons.closeFocus
            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                mClearBtn.background = null
                mClearBtn.icon = FlutterDevToolsIcons.close
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                mClearBtn.background = UIColor.MOUSE_PRESS_COLOR2
                mClearBtn.icon = FlutterDevToolsIcons.closeFocus
            }

            override fun mouseClicked(e: MouseEvent?) {
                mSearchTextField.text = null
                mClearBtn.background = null
                mClearBtn.icon = FlutterDevToolsIcons.close
                mClearBtn.isVisible = false
            }
        })

        mSearchPanel.add(mClearBtn)

        // 文本改变监听
        mSearchTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(p0: DocumentEvent?) {
                val str = mSearchTextField.text.trim()
                mClearBtn.isVisible = str.isNotEmpty()
                setNewImages(mImages, str)
            }

            override fun removeUpdate(p0: DocumentEvent?) {
                val str = mSearchTextField.text.trim()
                mClearBtn.isVisible = str.isNotEmpty()
                setNewImages(mImages, str)
            }

            override fun changedUpdate(p0: DocumentEvent?) {
                val str = mSearchTextField.text.trim()
                mClearBtn.isVisible = str.isNotEmpty()
                setNewImages(mImages, str)
            }
        })
    }

    /**
     * 初始化底部界面布局
     */
    private fun initBottomLayout() {
        // 底部按钮面板
        val bottomPanel = JPanel(GridBagLayout())
        bottomPanel.background = UIColor.BG_COLOR
        bottomPanel.border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
        add(bottomPanel, BorderLayout.SOUTH)

        val c = GridBagConstraints()
        // 底部靠左面板
        val bottomLeftPanel = JPanel(GridBagLayout())
        bottomLeftPanel.background = UIColor.BG_COLOR
        c.fill = GridBagConstraints.VERTICAL
        c.weightx = 0.0
        bottomPanel.add(bottomLeftPanel, c)

        val refreshToolbar =
            ActionManager.getInstance().createActionToolbar("imagePreview", DefaultActionGroup(RefreshButton()), true)
        refreshToolbar.targetComponent = bottomLeftPanel
        var component = refreshToolbar.component
        component.background = null
        c.fill = GridBagConstraints.BOTH
        bottomLeftPanel.add(component, c)

        // 增加图片预览的根路径显示
        mRootPathJPanel = JPanel(GridBagLayout())
        mRootPathJPanel.background = UIColor.BG_COLOR
        mRootPathJPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 4, 2, 4),
                BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true)
            ),
            BorderFactory.createEmptyBorder(0, 4, 0, 4)
        )

        c.weightx = 1.0
        c.fill = GridBagConstraints.BOTH
        bottomPanel.add(mRootPathJPanel, c)

        val label = JLabel("rootPath:")
        @Suppress("UseJBColor")
        label.foreground = Color(76, 80, 82)
        label.font = UIUtil.getFont(UIUtil.FontSize.MINI, label.font)

        c.weightx = 0.0
        c.fill = GridBagConstraints.VERTICAL
        mRootPathJPanel.add(label, c)

        mRootPathLabel = MyLabel(formatRootPath(mRootFilePath ?: ""))
        mRootPathLabel.strictMode = true
        mRootPathLabel.ellipsize = MyLabel.TruncateAt.MIDDLE
        c.fill = GridBagConstraints.BOTH
        c.weightx = 1.0
        mRootPathJPanel.add(mRootPathLabel, c)

        mChangeBtn = JButton("Change")
        mChangeBtn.preferredSize = JBUI.size(60, 26)
        mChangeBtn.maximumSize = JBUI.size(60, 26)
        mChangeBtn.minimumSize = JBUI.size(60, 26)
        mChangeBtn.font = UIUtil.getFont(UIUtil.FontSize.SMALL, mChangeBtn.font)
        mChangeBtn.addActionListener {
            mChangeBtn.isEnabled = false
            val file = VirtualFileManager.getInstance().findFileByUrl("file://$mRootFilePath")
            val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            val selectedFile = FileChooser.chooseFile(descriptor, module.project, file)
            mChangeBtn.isEnabled = true
            selectedFile?.also {
                mRootFilePath = it.path
                PropertiesSerializeUtils.putString(module.project, ROOT_PATH, it.path)
                mRootPathLabel.text = formatRootPath(it.path)
                updateNewImage()
            }
        }

        c.fill = GridBagConstraints.VERTICAL
        c.weightx = 0.0
        mRootPathJPanel.add(mChangeBtn, c)

        // 底部靠右的布局面板
        val bottomRightPanel = JPanel(GridBagLayout())
        bottomRightPanel.background = UIColor.BG_COLOR
        c.fill = GridBagConstraints.VERTICAL
        c.weightx = 0.0
        bottomPanel.add(bottomRightPanel, c)

        val modeSwitchToolbar =
            ActionManager.getInstance()
                .createActionToolbar("imagePreview", DefaultActionGroup(ListModeButton(), GridModeButton()), true)
        modeSwitchToolbar.targetComponent = bottomRightPanel
        component = modeSwitchToolbar.component
        component.background = null
        bottomRightPanel.add(component, c)
    }


    private fun refreshImagePanel() {
        mImagePanel.clearSelection()
        mImagePanel.revalidate()
        mImagePanel.repaint()
    }

    private fun formatRootPath(rootPath: String): String {
        var rootPathFormat = rootPath
        val basePath = module.basePath
        if (!basePath.isNullOrEmpty() && rootPathFormat.startsWith(basePath)) {
            rootPathFormat = rootPathFormat.replace(basePath, "")
            val index = basePath.lastIndexOf("/")
            if (index != -1) {
                rootPathFormat = basePath.substring(index + 1) + rootPathFormat
            }
        }
        return rootPathFormat
    }

    private fun updateNewImage() {
        mGetImageJob?.cancel()

        mGetImageJob = defaultCoroutineScope.launch {
            // 不加delay，cancel则无效，无法检测中断
            delay(200)
            val images = getImageData()
            mainCoroutineScope.launch {
                delay(100)
                mImages = images
                setNewImages(mImages, mSearchTextField.text)
            }
        }
    }

    private fun calculationColumns(): Int {
        val itemWidth = mGridImageLayoutWidth + 20
        val width = width
        return if (width <= itemWidth) 1 else width / itemWidth
    }

    private fun getImageData(): Set<Property>? {
        if (mRootFilePath.isNullOrEmpty()) {
            return null
        }

        val file = VirtualFileManager.getInstance().findFileByUrl("file://$mRootFilePath")
            ?: return null
        if (!file.isDirectory) {
            return null
        }

        val images = getDeDuplicationList(file)
        if (images.size == 0) {
            return null
        }

        return images
    }

    // 设置需要预览的图片数据
    private fun setNewImages(images: Set<Property>?, search: String?) {
        var data = images
        if (data.isNullOrEmpty()) {
            myListModel.data = null
            refreshImagePanel()
            return
        }

        val searchStr = search?.trim()
        if (!searchStr.isNullOrEmpty()) {
            val newData = mutableSetOf<Property>()
            for (property: Property in data) {
                if (property.name.contains(searchStr)) {
                    newData.add(property)
                }
            }
            data = newData
        }

        if (data.isEmpty()) {
            myListModel.data = null
            refreshImagePanel()
            return
        }

        myListModel.data = data.toList()
        refreshImagePanel()
    }

    // 设置图片预览的布局样式
    private fun setImageLayout() {
        if (!isGridMode) {
            mImagePanel.layoutOrientation = JBList.VERTICAL
            mImagePanel.fixedCellHeight = 100
            mImagePanel.fixedCellWidth = width
            mImagePanel.setExpandableItemsEnabled(true)
        } else {
            mImagePanel.layoutOrientation = JBList.HORIZONTAL_WRAP
            mImagePanel.fixedCellHeight = mGridImageLayoutWidth + 60 + 20
            mImagePanel.fixedCellWidth = mGridImageLayoutWidth + 20
            mImagePanel.setExpandableItemsEnabled(false)
        }
    }

    /**
     * 获取图片预览Item样式
     * @param isGridMode false:线性布局，true：网格布局
     */
    private fun getPreviewItemPanel(index: Int, isGridMode: Boolean, focused: Boolean): JPanel {
        val panel = JPanel()
        panel.background = UIColor.BG_COLOR
        val image = myListModel.getData(index) ?: return panel
        panel.layout = BorderLayout()
        if (!isGridMode) {
            if (focused) {
                panel.background = UIColor.MOUSE_ENTER_COLOR
            }

            // 列表布局
            panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            panel.minimumSize = Dimension(width, 100)

            val imageView = ChessBoardPanel()
                .apply {
                    val iconPanel = IconPanel(image.value, true)
                    add(iconPanel)
                }
            imageView.preferredSize = Dimension(80, 80)
            imageView.border = JBUI.Borders.customLine(UIColor.LINE_COLOR, 1)
            panel.add(imageView, BorderLayout.WEST)


            val label = JLabel()
            label.text = image.name

            val label2 = JLabel()
            label2.foreground = Gray._131
            label2.font = UIUtil.getFont(UIUtil.FontSize.MINI, label.font)
            label2.border = BorderFactory.createEmptyBorder(2, 0, 0, 0)
            label2.text = image.key

            val box = Box.createVerticalBox()
            box.border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 30, 0, 0),
                JBUI.Borders.customLine(UIColor.LINE_COLOR, 0, 0, 1, 0)
            )
            box.add(Box.createVerticalGlue())
            box.add(label)
            box.add(label2)
            box.add(Box.createVerticalGlue())

            panel.add(box, BorderLayout.CENTER)

            return panel
        } else {
            JBLabel(null, JBLabel.CENTER)
            // 网格布局
            val labelHeight = 60
            // 20 为padding 10
            panel.preferredSize = Dimension(mGridImageLayoutWidth + 20, mGridImageLayoutWidth + labelHeight + 20)
            panel.minimumSize = panel.preferredSize
            panel.maximumSize = panel.preferredSize
            val emptyBorderWidth = if (focused) 9 else 10
            val lineBorderWidth = if (focused) 2 else 1
            val lineBorderColor = if (focused) UIColor.INPUT_FOCUS_COLOR else UIColor.LINE_COLOR
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(emptyBorderWidth, emptyBorderWidth, emptyBorderWidth, emptyBorderWidth),
                JBUI.Borders.customLine(lineBorderColor, lineBorderWidth)
            )

            val imageView = ChessBoardPanel()
                .apply {
                    alignmentX = LEFT_ALIGNMENT
                    val iconPanel = IconPanel(image.value, true)
                    add(iconPanel)
                }
            imageView.preferredSize = Dimension(mGridImageLayoutWidth, mGridImageLayoutWidth)
            imageView.minimumSize = imageView.preferredSize
            imageView.maximumSize = imageView.preferredSize

            panel.add(imageView, BorderLayout.CENTER)

            val label = JLabel()
            label.text = image.name

            val label2 = JLabel()
            label2.foreground = Gray._131
            label2.border = BorderFactory.createEmptyBorder(2, 0, 0, 0)
            label2.font = UIUtil.getFont(UIUtil.FontSize.MINI, label.font)
            label2.text = image.key

            val box = Box.createVerticalBox()
            box.isOpaque = true
            box.background = UIColor.IMAGE_TITLE_BG_COLOR
            box.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
            box.preferredSize = Dimension(mGridImageLayoutWidth, labelHeight)
            box.minimumSize = box.preferredSize
            box.maximumSize = box.preferredSize
            box.add(Box.createVerticalGlue())
            box.add(label)
            box.add(label2)
            box.add(Box.createVerticalGlue())

            panel.add(box, BorderLayout.SOUTH)
        }

        return panel
    }

    private fun isImage(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith("png")
            || lower.endsWith("jpg")
            || lower.endsWith("jpeg")
            || lower.endsWith("webp")
            || lower.endsWith("gif")
            || lower.endsWith("svg")
    }

    /**
     * 获取去重后的属性列表
     */
    @Suppress("UnsafeVfsRecursion")
    private fun getDeDuplicationList(rootDir: VirtualFile, parentPath: String = ""): LinkedHashSet<Property> {
        val childrenSet = linkedSetOf<Property>()
        val name = rootDir.name
        val children = rootDir.children
        if (name != "4.0x" && name != "3.0x" && name != "2.0x" && name != "1.5x") {
            val child4x = rootDir.findChild("4.0x")
            val child3x = rootDir.findChild("3.0x")
            val child2x = rootDir.findChild("2.0x")
            val child1_5x = rootDir.findChild("1.5x")

            child2x?.also {
                for (child in it.children) {
                    if (!child.isDirectory && isImage(child.name)) {
                        childrenSet.add(Property("${parentPath}${child.name}", child.path, child.name))
                    }
                }
            }

            child3x?.also {
                for (child in it.children) {
                    if (!child.isDirectory && isImage(child.name)) {
                        childrenSet.add(Property("${parentPath}${child.name}", child.path, child.name))
                    }
                }
            }

            child1_5x?.also {
                for (child in it.children) {
                    if (!child.isDirectory && isImage(child.name)) {
                        childrenSet.add(Property("${parentPath}${child.name}", child.path, child.name))
                    }
                }
            }

            for (child in children) {
                if (!child.isDirectory && isImage(child.name)) {
                    childrenSet.add(Property("${parentPath}${child.name}", child.path, child.name))
                }
            }

            child4x?.also {
                for (child in it.children) {
                    if (!child.isDirectory && isImage(child.name)) {
                        childrenSet.add(Property("${parentPath}${child.name}", child.path, child.name))
                    }
                }
            }
        }

        for (child in children) {
            if (!child.isDirectory) {
                continue
            }

            childrenSet.addAll(getDeDuplicationList(child, "$parentPath${child.name}/"))
        }

        return childrenSet
    }

    private fun getFile(relativePath: String): File {
        // 此逻辑是用于处理类似IOS那种通过文件名称带@1x，@2x等后缀处理不同分辨率图片的情况
        // 此情况只需要显示其中一张即可
        // val indexOf = relativePath.lastIndexOf(".")
        // if (indexOf == -1) {
        //     return File(relativePath)
        // }
        //
        // val name = relativePath.substring(0, indexOf)
        // val suffix = relativePath.substring(indexOf)
        // var file = File("$name@3x$suffix")
        // if (file.exists()) {
        //     return file
        // }
        //
        // file = File("$name@2x$suffix")
        // if (file.exists()) {
        //     return file
        // }
        //
        // file = File("$name@1x$suffix")
        // if (file.exists()) {
        //     return file
        // }

        return File(relativePath)
    }

    private fun openFile(image: Property) {
        val file = VirtualFileManager.getInstance().findFileByUrl("file://${image.value}")
            ?: return
        FileEditorManager.getInstance(module.project).openFile(file, false)
    }

    override fun dispose() {

    }

    private inner class ListModeButton
        : ToggleAction("List mode", "Switch to list mode", FlutterDevToolsIcons.list), DumbAware {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun isSelected(e: AnActionEvent) = !isGridMode

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (state) {
                isGridMode = false
            }
        }
    }

    private inner class GridModeButton
        : ToggleAction("Grid mode", "Switch to grid mode", FlutterDevToolsIcons.grid), DumbAware {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun isSelected(e: AnActionEvent) = isGridMode

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (state) {
                isGridMode = true
            }
        }
    }

    private inner class RefreshButton
        : AnAction("Refresh", "Refresh image data", FlutterDevToolsIcons.refresh), DumbAware {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun actionPerformed(event: AnActionEvent) {
            updateNewImage()
        }
    }

    companion object {
        private const val ROOT_PATH = "Image Preview Root Path"
    }
}

