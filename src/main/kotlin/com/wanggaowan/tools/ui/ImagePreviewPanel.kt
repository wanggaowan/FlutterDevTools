package com.wanggaowan.tools.ui

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.wanggaowan.tools.entity.Property
import com.wanggaowan.tools.listener.SimpleComponentListener
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.PropertiesSerializeUtils
import com.wanggaowan.tools.utils.ex.basePath
import icons.SdkIcons
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
    private lateinit var mImagePanel: JPanel

    // 底部布局相关View
    private lateinit var mListLayoutBtn: ImageButton
    private lateinit var mGridLayoutBtn: ImageButton
    private lateinit var mRefreshBtn: ImageButton
    private lateinit var mRootPathJPanel: JPanel
    private lateinit var mRootPathLabel: MyLabel

    // 网格展示模式时图片布局宽度
    private val mGridImageLayoutWidth = 160

    // 当前布局模式
    private var mLayoutMode = 0

    // 需要展示的图片路径
    private var mImages: Set<Property>? = null

    // 当前展示图片数量
    private var mShowImageCount = 1

    // 默认预览图片文件夹
    private var mRootFilePath: String? = null

    init {
        Disposer.register(this, UiNotifyConnector(this, object : Activatable {
            override fun hideNotify() {}

            override fun showNotify() {
                mImages = getImageData()
                setNewImages()
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
        mImagePanel = JPanel()
        mImagePanel.layout = FlowLayout(FlowLayout.LEFT, 0, 0)
        mImagePanel.background = null
        mImagePanel.border = null
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
        mSearchPanel.layout = BoxLayout(mSearchPanel, BoxLayout.X_AXIS)
        mSearchPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                LineBorder(UIColor.LINE_COLOR, 0, 0, 1, 0),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ), LineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true)
        )
        parent.add(mSearchPanel, BorderLayout.NORTH)

        mSearchBtn = ImageButton(SdkIcons.search)
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

        mClearBtn = ImageButton(SdkIcons.close, arcSize = 100)
        mClearBtn.preferredSize = Dimension(30, 30)
        mClearBtn.minimumSize = mSearchBtn.preferredSize
        mClearBtn.maximumSize = mSearchBtn.preferredSize
        mClearBtn.background = null
        mClearBtn.isVisible = false
        mClearBtn.setBorderWidth(7)
        mClearBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                mClearBtn.background = UIColor.MOUSE_ENTER_COLOR2
                mClearBtn.icon = SdkIcons.closeFocus
            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                mClearBtn.background = null
                mClearBtn.icon = SdkIcons.close
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                mClearBtn.background = UIColor.MOUSE_PRESS_COLOR2
                mClearBtn.icon = SdkIcons.closeFocus
            }

            override fun mouseClicked(e: MouseEvent?) {
                mSearchTextField.text = null
                mClearBtn.background = null
                mClearBtn.icon = SdkIcons.close
                mClearBtn.isVisible = false
            }
        })

        mSearchPanel.add(mClearBtn)

        // 文本改变监听
        mSearchTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(p0: DocumentEvent?) {
                val str = mSearchTextField.text.trim()
                mClearBtn.isVisible = str.isNotEmpty()
                setNewImages()
            }

            override fun removeUpdate(p0: DocumentEvent?) {
                val str = mSearchTextField.text.trim()
                mClearBtn.isVisible = str.isNotEmpty()
                setNewImages()
            }

            override fun changedUpdate(p0: DocumentEvent?) {
                val str = mSearchTextField.text.trim()
                mClearBtn.isVisible = str.isNotEmpty()
                setNewImages()
            }

        })
    }

    /**
     * 初始化底部界面布局
     */
    private fun initBottomLayout() {
        // 底部按钮面板
        val bottomPanel = JPanel(GridBagLayout())
        bottomPanel.border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
        add(bottomPanel, BorderLayout.SOUTH)

        val c = GridBagConstraints()
        // 底部靠左面板
        val bottomLeftPanel = JPanel(GridBagLayout())
        c.fill = GridBagConstraints.VERTICAL
        c.weightx = 0.0
        bottomPanel.add(bottomLeftPanel, c)

        mRefreshBtn = ImageButton(SdkIcons.refresh)
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
                mImages = getImageData()
                setNewImages()
            }
        })

        // 增加图片预览的根路径显示
        mRootPathJPanel = JPanel(GridBagLayout())
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

        mRootPathLabel = MyLabel(mRootFilePath ?: "")
        mRootPathLabel.strictMode = true
        mRootPathLabel.ellipsize = MyLabel.TruncateAt.MIDDLE
        c.fill = GridBagConstraints.BOTH
        c.weightx = 1.0
        mRootPathJPanel.add(mRootPathLabel, c)

        val jButton = JButton("Change")
        jButton.preferredSize = JBUI.size(60, 26)
        jButton.maximumSize = JBUI.size(60, 26)
        jButton.minimumSize = JBUI.size(60, 26)
        jButton.font = UIUtil.getFont(UIUtil.FontSize.SMALL, jButton.font)
        jButton.addActionListener {
            jButton.isEnabled = false
            val file = VirtualFileManager.getInstance().findFileByUrl("file://$mRootFilePath")
            val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            val selectedFile = FileChooser.chooseFile(descriptor, module.project, file)
            jButton.isEnabled = true
            selectedFile?.also {
                mRootFilePath = it.path
                val path = mRootFilePath ?: ""
                PropertiesSerializeUtils.putString(module.project, ROOT_PATH, path)
                mRootPathLabel.text = path
                mImages = getImageData()
                setNewImages()
            }
        }

        c.fill = GridBagConstraints.VERTICAL
        c.weightx = 0.0
        mRootPathJPanel.add(jButton, c)

        // 底部靠右的布局面板
        val bottomRightPanel = JPanel(GridBagLayout())
        c.fill = GridBagConstraints.VERTICAL
        c.weightx = 0.0
        bottomPanel.add(bottomRightPanel, c)

        mListLayoutBtn = ImageButton(SdkIcons.list)
        mListLayoutBtn.preferredSize = JBUI.size(30)
        mListLayoutBtn.maximumSize = JBUI.size(30)
        mListLayoutBtn.minimumSize = JBUI.size(30)
        mListLayoutBtn.background = UIColor.MOUSE_PRESS_COLOR
        mListLayoutBtn.setBorderWidth(3)
        bottomRightPanel.add(mListLayoutBtn, c)

        mGridLayoutBtn = ImageButton(SdkIcons.grid)
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
                setNewImages()
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
                setNewImages()
            }
        })
    }

    /**
     * 注册窗口尺寸改变监听
     */
    private fun registerSizeChange() {
        addComponentListener(object : SimpleComponentListener() {
            override fun componentResized(p0: ComponentEvent?) {
                if (mLayoutMode == 0) {
                    for (component in mImagePanel.components) {
                        component.preferredSize = Dimension(width, 100)
                    }
                    val totalHeight = mShowImageCount * 100 + 100
                    mImagePanel.preferredSize = Dimension(width, totalHeight)
                    mImagePanel.updateUI()
                    return
                }

                val itemHeight: Int = mGridImageLayoutWidth + 60 + 20
                val itemWidth = mGridImageLayoutWidth + 20
                val columns: Int = if (width <= itemWidth) 1 else width / itemWidth
                var rows: Int = mShowImageCount / columns
                if (mShowImageCount % columns != 0) {
                    rows += 1
                }
                val totalHeight = rows * itemHeight + 100
                mImagePanel.preferredSize = Dimension(width, totalHeight)
            }
        })
    }

    private fun getImageData(): Set<Property>? {
        if (mRootFilePath.isNullOrEmpty()) {
            return null
        }

        val file = VirtualFileManager.getInstance().findFileByUrl("file://$mRootFilePath") ?: return null
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
    private fun setNewImages() {
        mImagePanel.removeAll()
        mImagePanel.updateUI()
        mShowImageCount = 1

        var data = mImages
        if (data.isNullOrEmpty()) {
            return
        }

        val searchStr = mSearchTextField.text.trim()
        if (searchStr.isNotEmpty()) {
            val newData = mutableSetOf<Property>()
            for (property: Property in data) {
                if (property.key.contains(searchStr)) {
                    newData.add(property)
                }
            }
            data = newData
        }

        if (data.isEmpty()) {
            return
        }

        mShowImageCount = data.size
        setImageLayout()
        data.forEach { image ->
            mImagePanel.add(getPreviewItemPanel(image, mLayoutMode))
        }
        mImagePanel.updateUI()
    }

    // 设置图片预览的布局样式
    private fun setImageLayout() {
        (mImagePanel.layout as FlowLayout).let {
            if (mLayoutMode == 0) {
                val totalHeight = mShowImageCount * 100 + 100
                mImagePanel.preferredSize = Dimension(width, totalHeight)
            } else {
                val itemHeight: Int = mGridImageLayoutWidth + 60 + 20
                val itemWidth = mGridImageLayoutWidth + 20
                val columns: Int = if (width <= itemWidth) 1 else width / itemWidth
                var rows: Int = mShowImageCount / columns
                if (mShowImageCount % columns != 0) {
                    rows += 1
                }
                val totalHeight = rows * itemHeight + 100
                mImagePanel.preferredSize = Dimension(width, totalHeight)
            }
        }
    }

    /**
     * 获取图片预览Item样式
     * @param layoutType 0:线性布局，1：网格布局
     */
    private fun getPreviewItemPanel(image: Property, layoutType: Int): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                panel.background = UIColor.MOUSE_ENTER_COLOR
            }

            override fun mouseExited(e: MouseEvent?) {
                panel.background = null
            }

            override fun mousePressed(e: MouseEvent?) {
                panel.background = UIColor.MOUSE_PRESS_COLOR
            }

            override fun mouseClicked(e: MouseEvent) {
                panel.background = null
                val file = VirtualFileManager.getInstance().findFileByUrl("file://${image.value}") ?: return
                val psiFile = PsiManager.getInstance(module.project).findFile(file) ?: return
                EditorHelper.openFilesInEditor(arrayOf<PsiFile?>(psiFile))
            }
        })

        if (layoutType == 0) {
            // 列表布局
            panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            panel.preferredSize = Dimension(width, 100)

            val imageView = ImageView(getFile(image.value))
            imageView.preferredSize = Dimension(80, 80)
            panel.add(imageView, BorderLayout.WEST)
            imageView.border = LineBorder(UIColor.LINE_COLOR, 1)

            val label = JLabel()
            label.border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 30, 0, 0),
                LineBorder(UIColor.LINE_COLOR, 0, 0, 1, 0)
            )
            label.text = image.key

            panel.add(label, BorderLayout.CENTER)
        } else {
            // 网格布局
            val labelHeight = 60
            // 20 为padding 10
            panel.preferredSize = Dimension(mGridImageLayoutWidth + 20, mGridImageLayoutWidth + labelHeight + 20)
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
                LineBorder(UIColor.LINE_COLOR, 1)
            )

            val imageView = ImageView(getFile(image.value))
            imageView.preferredSize = Dimension(mGridImageLayoutWidth, mGridImageLayoutWidth)
            panel.add(imageView, BorderLayout.CENTER)

            val label = JLabel()
            label.background = UIColor.IMAGE_TITLE_BG_COLOR
            label.isOpaque = true
            label.preferredSize = Dimension(mGridImageLayoutWidth, labelHeight)
            label.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
            label.text = image.key

            panel.add(label, BorderLayout.SOUTH)
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
        for (child in rootDir.children) {
            if (child.isDirectory) {
                val name = child.name
                val child4x = child.findChild("4.0x")
                val child3x = child.findChild("3.0x")
                val child2x = child.findChild("2.0x")
                val child1_5x = child.findChild("1.5x")
                if (child4x != null) {
                    childrenSet.addAll(getDeDuplicationList(child4x, "$parentPath$name/${child4x.name}/"))
                }

                if (child3x != null) {
                    childrenSet.addAll(getDeDuplicationList(child3x, "$parentPath$name/${child3x.name}/"))
                }

                if (child2x != null) {
                    childrenSet.addAll(getDeDuplicationList(child2x, "$parentPath$name/${child2x.name}/"))
                }

                if (child1_5x != null) {
                    childrenSet.addAll(getDeDuplicationList(child1_5x, "$parentPath$name/${child1_5x.name}/"))
                }

                if (name == "4.0x" || name == "3.0x" || name == "2.0x" || name == "1.5x") {
                    continue
                }
                childrenSet.addAll(getDeDuplicationList(child, "$parentPath${child.name}/"))
            } else if (isImage(child.name)) {
                childrenSet.add(Property(child.name, child.path))
            }
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

    override fun dispose() {

    }

    companion object {
        private const val ROOT_PATH = "Image Preview Root Path"
    }
}
