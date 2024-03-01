package com.wanggaowan.tools.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.Gray
import com.intellij.ui.SingleSelectionModel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.MultiColumnList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.wanggaowan.tools.entity.Property
import com.wanggaowan.tools.listener.SimpleComponentListener
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.PropertiesSerializeUtils
import com.wanggaowan.tools.utils.ex.basePath
import icons.FlutterDevToolsIcons
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.*
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 图片资源预览面板
 *
 * @author Created by wanggaowan on 2022/6/17 13:13
 */
class ImagePreviewPanel(val module: Module) : JPanel(), Disposable {

    // 搜素布局相关View
    private lateinit var mSearchPanel: JPanel
    private lateinit var mSearchBtn: ImageButton
    private lateinit var mClearBtn: ImageButton
    private lateinit var mSearchTextField: JTextField

    private lateinit var mScrollPane: JBScrollPane
    private lateinit var mImagePanel: MyMultiColumnList
    private val myListModel = MyListModel()

    // 底部布局相关View
    private lateinit var mListLayoutBtn: ImageButton
    private lateinit var mGridLayoutBtn: ImageButton
    private lateinit var mRefreshBtn: ImageButton
    private lateinit var mRootPathJPanel: JPanel
    private lateinit var mRootPathLabel: MyLabel
    private lateinit var mChangeBtn: JButton

    // 网格展示模式时图片布局宽度
    private val mGridImageLayoutWidth = 160

    // 当前布局模式
    private var mLayoutMode = 0

    // 需要展示的图片路径
    private var mImages: Set<Property>? = null

    // 默认预览图片文件夹
    private var mRootFilePath: String? = null

    private val defaultCoroutineScope = CoroutineScope(Dispatchers.Default)
    private val mainCoroutineScope = CoroutineScope(Dispatchers.Main)
    private var mGetImageJob: Job? = null
    private var mSelectedImage: Property? = null

    init {
        Disposer.register(this, UiNotifyConnector(this, object : Activatable {
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
        mImagePanel = MyMultiColumnList(myListModel)
        mImagePanel.background = UIColor.BG_COLOR
        mImagePanel.selectionModel = SingleSelectionModel()
        mImagePanel.setFixedColumnsMode(1)
        mImagePanel.tableHeader = null
        setImageLayout()

        mImagePanel.setCellRenderer { _, _, index, selected, focused ->
            return@setCellRenderer getPreviewItemPanel(index, mLayoutMode, selected || focused)
        }

        mImagePanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3) {
                    // 双击
                    return
                }

                if (e.clickCount > 1) {
                    mSelectedImage?.also {
                        openFile(it)
                    }
                    return
                }

                val selectedRow = mImagePanel.selectedRow
                if (mLayoutMode == 0 || mImagePanel.maxColumns == 1) {
                    mSelectedImage = myListModel.getData(selectedRow) ?: return
                    return
                }

                val selectedColumn = mImagePanel.selectedColumn
                if (selectedRow == -1 || selectedColumn == -1) {
                    mSelectedImage = null
                    return
                }

                val index = mImagePanel.model.toListIndex(selectedRow, selectedColumn)
                mSelectedImage = myListModel.getData(index) ?: return
            }
        })

        mScrollPane = JBScrollPane(mImagePanel)
        mScrollPane.background = null
        mScrollPane.border = LineBorder(UIColor.LINE_COLOR, 0, 0, 1, 0)
        mScrollPane.horizontalScrollBar = null
        add(mScrollPane, BorderLayout.CENTER)

        registerSizeChange()
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
                LineBorder(UIColor.LINE_COLOR, 0, 0, 1, 0),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ), LineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true)
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
                        LineBorder(UIColor.LINE_COLOR, 0, 0, 1, 0),
                        BorderFactory.createEmptyBorder(9, 9, 9, 9)
                    ), LineBorder(UIColor.INPUT_FOCUS_COLOR, 2, true)
                )
            }

            override fun focusLost(p0: FocusEvent?) {
                mSearchPanel.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createCompoundBorder(
                        LineBorder(UIColor.LINE_COLOR, 0, 0, 1, 0),
                        BorderFactory.createEmptyBorder(10, 10, 10, 10)
                    ), LineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true)
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

        mRefreshBtn = ImageButton(FlutterDevToolsIcons.refresh)
        mRefreshBtn.preferredSize = JBUI.size(30)
        mRefreshBtn.maximumSize = JBUI.size(30)
        mRefreshBtn.minimumSize = JBUI.size(30)
        mRefreshBtn.background = null
        mRefreshBtn.setBorderWidth(3)
        c.fill = GridBagConstraints.BOTH
        bottomLeftPanel.add(mRefreshBtn, c)

        mRefreshBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                mRefreshBtn.background = UIColor.MOUSE_ENTER_COLOR
            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                mRefreshBtn.background = null
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                mRefreshBtn.background = UIColor.MOUSE_PRESS_COLOR
            }

            override fun mouseClicked(e: MouseEvent?) {
                mRefreshBtn.background = UIColor.MOUSE_ENTER_COLOR
                updateNewImage()
            }
        })

        // 增加图片预览的根路径显示
        mRootPathJPanel = JPanel(GridBagLayout())
        mRootPathJPanel.background = UIColor.BG_COLOR
        mRootPathJPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 4, 2, 4),
                LineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true)
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

        mListLayoutBtn = ImageButton(FlutterDevToolsIcons.list)
        mListLayoutBtn.preferredSize = JBUI.size(30)
        mListLayoutBtn.maximumSize = JBUI.size(30)
        mListLayoutBtn.minimumSize = JBUI.size(30)
        mListLayoutBtn.background = UIColor.MOUSE_PRESS_COLOR
        mListLayoutBtn.setBorderWidth(3)
        bottomRightPanel.add(mListLayoutBtn, c)

        mGridLayoutBtn = ImageButton(FlutterDevToolsIcons.grid)
        mGridLayoutBtn.preferredSize = JBUI.size(30)
        mGridLayoutBtn.maximumSize = JBUI.size(30)
        mGridLayoutBtn.minimumSize = JBUI.size(30)
        mGridLayoutBtn.background = null
        mGridLayoutBtn.setBorderWidth(3)
        bottomRightPanel.add(mGridLayoutBtn, c)

        mListLayoutBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                mListLayoutBtn.background = UIColor.MOUSE_ENTER_COLOR

            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                if (mLayoutMode != 0) {
                    mListLayoutBtn.background = null
                } else {
                    mListLayoutBtn.background = UIColor.MOUSE_PRESS_COLOR
                }
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                mListLayoutBtn.background = UIColor.MOUSE_PRESS_COLOR
            }

            override fun mouseClicked(e: MouseEvent?) {
                if (mLayoutMode == 0) {
                    return
                }

                mListLayoutBtn.background = UIColor.MOUSE_PRESS_COLOR
                mGridLayoutBtn.background = null

                mLayoutMode = 0
                mImagePanel.clearSelection()
                setImageLayout()
                mImagePanel.setFixedColumnsMode(1)
            }
        })

        mGridLayoutBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                mGridLayoutBtn.background = UIColor.MOUSE_ENTER_COLOR
            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                if (mLayoutMode != 1) {
                    mGridLayoutBtn.background = null
                } else {
                    mGridLayoutBtn.background = UIColor.MOUSE_PRESS_COLOR
                }
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                mGridLayoutBtn.background = UIColor.MOUSE_PRESS_COLOR
            }

            override fun mouseClicked(e: MouseEvent?) {
                if (mLayoutMode == 1) {
                    return
                }

                mGridLayoutBtn.background = UIColor.MOUSE_PRESS_COLOR
                mListLayoutBtn.background = null

                mLayoutMode = 1
                mImagePanel.clearSelection()
                setImageLayout()
                val columns = calculationColumns()
                mImagePanel.setFixedColumnsMode(columns)
            }
        })
    }

    /**
     * 注册窗口尺寸改变监听
     */
    private fun registerSizeChange() {
        addComponentListener(object : SimpleComponentListener() {
            override fun componentResized(p0: ComponentEvent?) {
                if (mLayoutMode == 1) {
                    val columns = calculationColumns()
                    if (columns != mImagePanel.maxColumns) {
                        mImagePanel.clearSelection()
                        mImagePanel.setFixedColumnsMode(columns)
                    }
                }
            }
        })
    }

    private fun refreshImagePanel() {
        mImagePanel.clearSelection()
        mImagePanel.model.fireTableStructureChanged()
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
        if (mLayoutMode == 0) {
            mImagePanel.rowHeight = 100
        } else {
            mImagePanel.rowHeight = mGridImageLayoutWidth + 60 + 20
        }
    }

    /**
     * 获取图片预览Item样式
     * @param layoutType 0:线性布局，1：网格布局
     */
    private fun getPreviewItemPanel(index: Int, layoutType: Int, focused: Boolean): JPanel {
        val panel = JPanel()
        panel.background = UIColor.BG_COLOR
        val image = myListModel.getData(index) ?: return panel
        panel.layout = BorderLayout()
        if (layoutType == 0) {
            if (focused) {
                panel.background = UIColor.MOUSE_ENTER_COLOR
            }

            // 列表布局
            panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            panel.minimumSize = Dimension(width, 100)

            val imageView = ImageView(getFile(image.value))
            imageView.preferredSize = Dimension(80, 80)
            panel.add(imageView, BorderLayout.WEST)
            imageView.border = LineBorder(UIColor.LINE_COLOR, 1)

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
                LineBorder(UIColor.LINE_COLOR, 0, 0, 1, 0)
            )
            box.add(Box.createVerticalGlue())
            box.add(label)
            box.add(label2)
            box.add(Box.createVerticalGlue())

            panel.add(box, BorderLayout.CENTER)

            return panel
        } else {
            // 网格布局
            val labelHeight = 60
            // 20 为padding 10
            panel.preferredSize = Dimension(mGridImageLayoutWidth + 20, mGridImageLayoutWidth + labelHeight + 20)
            panel.minimumSize = panel.preferredSize
            panel.maximumSize = panel.preferredSize
            val emptyBorderWidth= if (focused) 9 else 10
            val lineBorderWidth= if (focused) 2 else 1
            val lineBorderColor= if (focused) UIColor.INPUT_FOCUS_COLOR else UIColor.LINE_COLOR
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(emptyBorderWidth, emptyBorderWidth, emptyBorderWidth, emptyBorderWidth),
                LineBorder(lineBorderColor, lineBorderWidth)
            )

            val imageView = ImageView(getFile(image.value))
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

    companion object {
        private const val ROOT_PATH = "Image Preview Root Path"
    }

    inner class MyMultiColumnList(model: ListModel<Property>) : MultiColumnList(model) {

        var maxColumns: Int = 25
            private set

        override fun setFixedColumnsMode(maxColumns: Int) {
            this.maxColumns = maxColumns
            super.setFixedColumnsMode(maxColumns)
        }

        override fun getPreferredSize(): Dimension {
            val totalHeight = this.model.rowCount * rowHeight + 100
            return Dimension(width, totalHeight)
        }
    }
}

class MyListModel : AbstractListModel<Property>() {
    var data: List<Property>? = null

    override fun getSize(): Int {
        return data?.size ?: 0
    }

    override fun getElementAt(index: Int): Property {
        return data!![index]
    }

    fun getData(index: Int): Property? {
        val data = this.data
        if (data.isNullOrEmpty()) {
            return null
        }
        if (index < 0 || index >= data.size) {
            return null
        }
        return data[index]
    }
}


