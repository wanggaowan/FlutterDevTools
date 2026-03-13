package com.wanggaowan.tools.extensions.toolwindow.resourcePreview

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
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
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.wanggaowan.tools.entity.Property
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.ui.ChessBoardPanel
import com.wanggaowan.tools.ui.ImageButton
import com.wanggaowan.tools.ui.MyLabel
import com.wanggaowan.tools.ui.UIColor
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
 * ImagePreviewPanel的另一种实现，采用JBList实现，JBList同样支持网格，但是列数量无法精准控制
 *
 * @author Created by wanggaowan on 2024/2/21 17:38
 */
class ImagePreviewPanel(val module: Module) : BorderLayoutPanel(), Disposable {

    // 搜素布局相关View
    private lateinit var mSearchPanel: JBBox
    private lateinit var mSearchBtn: ImageButton
    private lateinit var mClearBtn: ImageButton
    private lateinit var mSearchTextField: JBTextField

    private lateinit var mScrollPane: JBScrollPane
    private lateinit var mImagePanel: JBList<Property>
    private val myListModel = MyListModel()

    // 底部布局相关View
    private lateinit var mRootPathJPanel: JPanel
    private lateinit var mRootPathLabel: MyLabel

    // 网格展示模式时图片布局宽度
    private val mGridImageLayoutWidth = 160

    // 网格展示模式时文本布局高度
    private val mGridLabelLayoutHeight = 60

    // 列表布局时列表的高度
    private val mListImageLayoutHeight = 80

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
    private val mainCoroutineScope = CoroutineScope(Dispatchers.EDT)
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
        initSearchLayout()
        initBottomLayout()

        // 展示 Image 预览内容的面板
        mImagePanel = JBList(myListModel)
        mImagePanel.selectionModel = SingleSelectionModel()
        mImagePanel.visibleRowCount = 0

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
        mScrollPane.border = JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0)
        addToCenter(mScrollPane)

        setImageLayout()
    }

    /**
     * 初始化搜索界面布局
     */
    private fun initSearchLayout() {
        // 搜索一栏根布局
        mSearchPanel = JBBox(BoxLayout.X_AXIS)
        mSearchPanel.border = JBUI.Borders.compound(
            JBUI.Borders.compound(
                JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0),
                JBUI.Borders.empty(10)
            ), JBUI.Borders.customLine(UIUtil.getListSelectionBackground(false), 1)
        )

        mSearchBtn = ImageButton(FlutterDevToolsIcons.search)
        mSearchBtn.preferredSize = JBDimension(30, 30)
        mSearchBtn.minimumSize = mSearchBtn.preferredSize
        mSearchBtn.maximumSize = mSearchBtn.preferredSize
        mSearchBtn.background = UIColor.TRANSPARENT
        mSearchPanel.add(mSearchBtn)

        mSearchTextField = JBTextField()
        mSearchTextField.preferredSize = JBDimension(100, 30)
        mSearchTextField.minimumSize = JBDimension(100, 30)
        mSearchTextField.background = UIColor.TRANSPARENT
        mSearchTextField.border = JBUI.Borders.empty()
        mSearchTextField.isOpaque = true
        mSearchTextField.addFocusListener(object : FocusListener {
            override fun focusGained(p0: FocusEvent?) {
                mSearchPanel.border = JBUI.Borders.compound(
                    JBUI.Borders.compound(
                        JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0),
                        JBUI.Borders.empty(9)
                    ), JBUI.Borders.customLine(UIUtil.getListSelectionBackground(true), 2)
                )
            }

            override fun focusLost(p0: FocusEvent?) {
                mSearchPanel.border = JBUI.Borders.compound(
                    JBUI.Borders.compound(
                        JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0),
                        JBUI.Borders.empty(10)
                    ), JBUI.Borders.customLine(UIUtil.getListSelectionBackground(false), 1)
                )
            }
        })

        mSearchPanel.add(mSearchTextField)

        mClearBtn = ImageButton(FlutterDevToolsIcons.close, arcSize = 100)
        mClearBtn.preferredSize = JBDimension(30, 30)
        mClearBtn.minimumSize = mSearchBtn.preferredSize
        mClearBtn.maximumSize = mSearchBtn.preferredSize
        mClearBtn.background = null
        mClearBtn.isVisible = false
        mClearBtn.setBorderWidth(7)
        mClearBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                mClearBtn.background = JBUI.CurrentTheme.ActionButton.hoverBackground()
                mClearBtn.icon = FlutterDevToolsIcons.closeFocus
            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                mClearBtn.background = null
                mClearBtn.icon = FlutterDevToolsIcons.close
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                mClearBtn.background = JBUI.CurrentTheme.ActionButton.pressedBackground()
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

        addToTop(mSearchPanel)
    }

    /**
     * 初始化底部界面布局
     */
    private fun initBottomLayout() {
        // 底部按钮面板
        val bottomPanel = BorderLayoutPanel()
        bottomPanel.border = JBUI.Borders.empty(5, 0)
        addToBottom(bottomPanel)

        // 底部靠左面板
        val refreshToolbar =
            ActionManager.getInstance().createActionToolbar("imagePreview", DefaultActionGroup(RefreshButton()), true)
        refreshToolbar.targetComponent = bottomPanel
        var component = refreshToolbar.component
        bottomPanel.addToLeft(component)

        // 增加图片预览的根路径显示
        mRootPathJPanel = JPanel(GridBagLayout())
        mRootPathJPanel.border = JBUI.Borders.compound(
            JBUI.Borders.compound(
                JBUI.Borders.empty(2, 4),
                JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1)
            ),
            JBUI.Borders.empty(0, 4)
        )
        bottomPanel.addToCenter(mRootPathJPanel)

        val label = JBLabel("RootPath：")
        label.foreground = UIUtil.getLabelDisabledForeground()
        label.font = UIUtil.getFont(UIUtil.FontSize.MINI, label.font)

        val c = GridBagConstraints()
        c.weightx = 0.0
        c.fill = GridBagConstraints.VERTICAL
        mRootPathJPanel.add(label, c)

        mRootPathLabel = MyLabel(formatRootPath(mRootFilePath ?: ""))
        mRootPathLabel.strictMode = true
        mRootPathLabel.ellipsize = MyLabel.TruncateAt.MIDDLE
        c.fill = GridBagConstraints.BOTH
        c.weightx = 1.0
        mRootPathJPanel.add(mRootPathLabel, c)

        val toolbar =
            ActionManager.getInstance()
                .createActionToolbar("imagePreview", DefaultActionGroup(SelectPathButton()), true)
        toolbar.targetComponent = bottomPanel
        component = toolbar.component

        c.fill = GridBagConstraints.VERTICAL
        c.weightx = 0.0
        mRootPathJPanel.add(component, c)

        // 底部靠右的布局面板
        val modeSwitchToolbar =
            ActionManager.getInstance()
                .createActionToolbar("imagePreview", DefaultActionGroup(ListModeButton(), GridModeButton()), true)
        modeSwitchToolbar.targetComponent = bottomPanel
        component = modeSwitchToolbar.component
        bottomPanel.addToRight(component)
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
        if (images.isEmpty()) {
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
            mImagePanel.fixedCellHeight = mListImageLayoutHeight
            // 设置为0说明宽度自适应
            mImagePanel.fixedCellWidth = 0
            mImagePanel.setExpandableItemsEnabled(true)
        } else {
            mImagePanel.layoutOrientation = JBList.HORIZONTAL_WRAP
            mImagePanel.fixedCellHeight = mGridImageLayoutWidth + mGridLabelLayoutHeight + 20
            mImagePanel.fixedCellWidth = mGridImageLayoutWidth + 20
            mImagePanel.setExpandableItemsEnabled(false)
        }
    }

    /**
     * 获取图片预览Item样式
     * @param isGridMode false:线性布局，true：网格布局
     */
    private fun getPreviewItemPanel(index: Int, isGridMode: Boolean, focused: Boolean): JPanel {
        val panel = BorderLayoutPanel()
        val image = myListModel.getData(index) ?: return panel

        if (!isGridMode) {
            // 列表布局
            if (focused) {
                panel.border = JBUI.Borders.compound(
                    JBUI.Borders.compound(
                        JBUI.Borders.empty(7),
                        JBUI.Borders.customLine(UIUtil.getListSelectionBackground(true), 2)
                    ),
                    JBUI.Borders.empty(1),
                )
            } else {
                panel.border = JBUI.Borders.empty(10)
            }
            panel.minimumSize = JBDimension(120, mListImageLayoutHeight)
            val imageView = ChessBoardPanel()
                .apply {
                    val iconPanel = IconPanel(image.value, true)
                    add(iconPanel)
                }

            // 20为上下左右padding
            val size = mListImageLayoutHeight - 20
            imageView.preferredSize = JBDimension(size, size)
            imageView.minimumSize = imageView.preferredSize
            imageView.maximumSize = imageView.preferredSize
            imageView.border = JBUI.Borders.customLine(UIUtil.getListSelectionBackground(false), 1)
            panel.addToLeft(imageView)

            val label = JBLabel()
            label.text = image.name

            val label2 = JBLabel()
            label2.foreground = Gray._131
            label2.font = UIUtil.getFont(UIUtil.FontSize.MINI, label.font)
            label2.border = JBUI.Borders.emptyTop(2)
            label2.text = image.key

            val box = JBBox.createVerticalBox()
            if (focused) {
                box.border = JBUI.Borders.emptyLeft(20)
            } else {
                box.border = JBUI.Borders.compound(
                    JBUI.Borders.emptyLeft(20),
                    JBUI.Borders.customLine(UIUtil.getListSelectionBackground(false), 0, 0, 1, 0)
                )
            }

            box.add(JBBox.createVerticalGlue())
            box.add(label)
            box.add(JBBox.createVerticalStrut(4))
            box.add(label2)
            box.add(JBBox.createVerticalGlue())

            panel.addToCenter(box)

            return panel
        } else {
            // 网格布局
            panel.preferredSize =
                JBDimension(mGridImageLayoutWidth + 20, mGridImageLayoutWidth + mGridLabelLayoutHeight + 20)
            panel.minimumSize = panel.preferredSize
            panel.maximumSize = panel.preferredSize
            val emptyBorderWidth = if (focused) 9 else 10
            val lineBorderWidth = if (focused) 2 else 1
            val lineBorderColor = UIUtil.getListSelectionBackground(focused)
            panel.border = JBUI.Borders.compound(
                JBUI.Borders.empty(emptyBorderWidth),
                JBUI.Borders.customLine(lineBorderColor, lineBorderWidth)
            )

            val imageView = ChessBoardPanel()
                .apply {
                    alignmentX = LEFT_ALIGNMENT
                    val iconPanel = IconPanel(image.value, true)
                    add(iconPanel)
                }
            imageView.preferredSize = JBDimension(mGridImageLayoutWidth, mGridImageLayoutWidth)
            imageView.minimumSize = imageView.preferredSize
            imageView.maximumSize = imageView.preferredSize

            panel.addToCenter(imageView)

            val label = JBLabel()
            label.text = image.name

            val label2 = JBLabel()
            label2.foreground = Gray._131
            label2.border = JBUI.Borders.emptyTop(2)
            label2.font = UIUtil.getFont(UIUtil.FontSize.MINI, label.font)
            label2.text = image.key

            val box = JBBox.createVerticalBox()
            box.isOpaque = true
            box.background = JBUI.CurrentTheme.ActionButton.pressedBackground()
            box.border = JBUI.Borders.empty(5)
            box.preferredSize = JBDimension(mGridImageLayoutWidth, mGridLabelLayoutHeight)
            box.minimumSize = box.preferredSize
            box.maximumSize = box.preferredSize
            box.add(JBBox.createVerticalGlue())
            box.add(label)
            box.add(JBBox.createVerticalStrut(4))
            box.add(label2)
            box.add(JBBox.createVerticalGlue())

            panel.addToBottom(box)
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

    private inner class SelectPathButton
        : AnAction("Select", "Select image root path", AllIcons.Actions.Edit), DumbAware {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun actionPerformed(event: AnActionEvent) {
            val file = VirtualFileManager.getInstance().findFileByUrl("file://$mRootFilePath")
            val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            val selectedFile = FileChooser.chooseFile(descriptor, module.project, file)
            selectedFile?.also {
                mRootFilePath = it.path
                PropertiesSerializeUtils.putString(module.project, ROOT_PATH, it.path)
                mRootPathLabel.text = formatRootPath(it.path)
                updateNewImage()
            }
        }
    }

    companion object {
        private const val ROOT_PATH = "Image Preview Root Path"
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

