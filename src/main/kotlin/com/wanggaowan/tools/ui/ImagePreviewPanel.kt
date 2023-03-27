package com.wanggaowan.tools.ui

import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
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
    private var mImages: Set<String>? = null

    // 当前展示图片数量
    private var mShowImageCount = 1

    // 默认预览图片文件夹
    private var mRootFilePath: String? = null

    private var mDarkTheme = UIConfig.isDarkTheme

    init {
        Disposer.register(this, UiNotifyConnector(this, object : Activatable {
            override fun hideNotify() {}

            override fun showNotify() {
                mImages = getImageData()
                setNewImages()
            }
        }))

        module.project.messageBus.connect()
            .subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
                override fun stateChanged(toolWindowManager: ToolWindowManager) {
                    // 通过监听窗口的变化判断是否修改了主题，当打开设置界面并关闭后，此方法会回调
                    // 目前未找到直接监听主题变更的方法
                    checkTheme()
                }
            })

        module.project.messageBus.connect()
            .subscribe(UISettingsListener.TOPIC, UISettingsListener {
                // 此方法并不是在所有设置改变时都回调，测试发现切换主题，只有黑色主题时才回调
                checkTheme()
            })

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
        mScrollPane.border = LineBorder(UIConfig.getLineColor(), 0, 0, 1, 0)
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
                LineBorder(UIConfig.getLineColor(), 0, 0, 1, 0),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ), LineBorder(UIConfig.getInputUnFocusColor(), 1, true)
        )
        parent.add(mSearchPanel, BorderLayout.NORTH)

        mSearchBtn = ImageButton(SdkIcons.search)
        mSearchBtn.preferredSize = Dimension(30, 30)
        mSearchBtn.minimumSize = mSearchBtn.preferredSize
        mSearchBtn.maximumSize = mSearchBtn.preferredSize
        mSearchBtn.background = UIConfig.TRANSPARENT
        mSearchPanel.add(mSearchBtn)

        mSearchTextField = JTextField()
        mSearchTextField.preferredSize = Dimension(100, 30)
        mSearchTextField.minimumSize = Dimension(100, 30)
        mSearchTextField.background = UIConfig.TRANSPARENT
        mSearchTextField.border = BorderFactory.createEmptyBorder()
        mSearchTextField.isOpaque = true
        mSearchTextField.addFocusListener(object : FocusListener {
            override fun focusGained(p0: FocusEvent?) {
                mSearchPanel.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createCompoundBorder(
                        LineBorder(UIConfig.getLineColor(), 0, 0, 1, 0),
                        BorderFactory.createEmptyBorder(9, 9, 9, 9)
                    ), LineBorder(UIConfig.getInputFocusColor(), 2, true)
                )
            }

            override fun focusLost(p0: FocusEvent?) {
                mSearchPanel.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createCompoundBorder(
                        LineBorder(UIConfig.getLineColor(), 0, 0, 1, 0),
                        BorderFactory.createEmptyBorder(10, 10, 10, 10)
                    ), LineBorder(UIConfig.getInputUnFocusColor(), 1, true)
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
                mClearBtn.background = UIConfig.getMouseEnterColor2()
                mClearBtn.icon = SdkIcons.closeFocus
            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                mClearBtn.background = null
                mClearBtn.icon = SdkIcons.close
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                mClearBtn.background = UIConfig.getMousePressColor2()
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
                mRefreshBtn.background = UIConfig.getMouseEnterColor()
            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                mRefreshBtn.background = null
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                mRefreshBtn.background = UIConfig.getMousePressColor()
            }

            override fun mouseClicked(e: MouseEvent?) {
                mRefreshBtn.background = UIConfig.getMouseEnterColor()
                mImages = getImageData()
                setNewImages()
            }
        })

        // 增加图片预览的根路径显示
        mRootPathJPanel = JPanel(GridBagLayout())
        mRootPathJPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 4, 2, 4),
                LineBorder(UIConfig.getInputUnFocusColor(), 1, true)
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
        mListLayoutBtn.background = UIConfig.getMousePressColor()
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
                mListLayoutBtn.background = UIConfig.getMouseEnterColor()

            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                if (mLayoutMode != 0) {
                    mListLayoutBtn.background = null
                } else {
                    mListLayoutBtn.background = UIConfig.getMousePressColor()
                }
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                mListLayoutBtn.background = UIConfig.getMousePressColor()
            }

            override fun mouseClicked(e: MouseEvent?) {
                if (mLayoutMode == 0) {
                    return
                }

                mListLayoutBtn.background = UIConfig.getMousePressColor()
                mGridLayoutBtn.background = null

                mLayoutMode = 0
                setNewImages()
            }
        })

        mGridLayoutBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                mGridLayoutBtn.background = UIConfig.getMouseEnterColor()
            }

            override fun mouseExited(e: MouseEvent?) {
                super.mouseExited(e)
                if (mLayoutMode != 1) {
                    mGridLayoutBtn.background = null
                } else {
                    mGridLayoutBtn.background = UIConfig.getMousePressColor()
                }
            }

            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                mGridLayoutBtn.background = UIConfig.getMousePressColor()
            }

            override fun mouseClicked(e: MouseEvent?) {
                if (mLayoutMode == 1) {
                    return
                }

                mGridLayoutBtn.background = UIConfig.getMousePressColor()
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

    private fun getImageData(): Set<String>? {
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
            val newData = mutableSetOf<String>()
            for (path: String in data) {
                var name = path
                mRootFilePath?.also {
                    name = path.replace("$it/", "")
                }

                if (name.contains(searchStr)) {
                    newData.add(path)
                }
            }
            data = newData
        }

        if (data.isEmpty()) {
            return
        }

        mShowImageCount = data.size
        setImageLayout()
        data.forEach { imagePath ->
            mImagePanel.add(getPreviewItemPanel(imagePath, mLayoutMode))
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
    private fun getPreviewItemPanel(imagePath: String, layoutType: Int): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                super.mouseClicked(e)
                if (e.clickCount == 2) {
                    val file = VirtualFileManager.getInstance().findFileByUrl("file://$imagePath") ?: return
                    val psiFile = PsiManager.getInstance(module.project).findFile(file) ?: return
                    EditorHelper.openFilesInEditor(arrayOf<PsiFile?>(psiFile))
                }
            }
        })

        if (layoutType == 0) {
            // 列表布局
            panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            panel.preferredSize = Dimension(width, 100)

            val imageView = ImageView(getFile(imagePath), UIConfig.isDarkTheme)
            imageView.preferredSize = Dimension(80, 80)
            panel.add(imageView, BorderLayout.WEST)
            imageView.border = LineBorder(UIConfig.getLineColor(), 1)

            val label = JLabel()
            label.border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 30, 0, 0),
                LineBorder(UIConfig.getLineColor(), 0, 0, 1, 0)
            )

            var path = imagePath
            mRootFilePath?.also {
                path = path.replace("$it/", "")
            }
            label.text = getPropertyValue(path)

            panel.add(label, BorderLayout.CENTER)
        } else {
            // 网格布局
            val labelHeight = 60
            // 20 为padding 10
            panel.preferredSize = Dimension(mGridImageLayoutWidth + 20, mGridImageLayoutWidth + labelHeight + 20)
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
                LineBorder(UIConfig.getLineColor(), 1)
            )

            val imageView = ImageView(getFile(imagePath), UIConfig.isDarkTheme)
            imageView.preferredSize = Dimension(mGridImageLayoutWidth, mGridImageLayoutWidth)
            panel.add(imageView, BorderLayout.CENTER)

            val label = JLabel()
            label.background = UIConfig.getImageTitleBgColor()
            label.isOpaque = true
            label.preferredSize = Dimension(mGridImageLayoutWidth, labelHeight)
            label.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

            var path = imagePath
            mRootFilePath?.also {
                path = path.replace("$it/", "")
            }
            label.text = getPropertyValue(path)

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
    private fun getDeDuplicationList(rootDir: VirtualFile, parentPath: String = ""): LinkedHashSet<String> {
        val childrenSet = linkedSetOf<String>()
        for (child in rootDir.children) {
            if (child.isDirectory) {
                childrenSet.addAll(getDeDuplicationList(child, "$parentPath${child.name}/"))
            } else if (isImage(child.name)) {
                childrenSet.add(getPropertyValue(child.path))
            }
        }

        return childrenSet
    }

    private fun getPropertyValue(value: String): String {
        // 此逻辑是用于处理类似IOS那种通过文件名称带@1x，@2x等后缀处理不同分辨率图片的情况
        // 此情况需要把后缀去除再显示
        // return value.replace("@1x", "")
        //     .replace("@2x", "")
        //     .replace("@3x", "")
        return value
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

    private fun checkTheme() {
        val isDarkTheme = UIConfig.isDarkTheme
        if (mDarkTheme != isDarkTheme) {
            mDarkTheme = isDarkTheme
            updateTheme()
        }
    }

    private fun updateTheme() {
        val inputRectColor =
            if (mSearchTextField.hasFocus()) UIConfig.getInputFocusColor() else UIConfig.getInputUnFocusColor()
        mSearchPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                LineBorder(UIConfig.getLineColor(), 0, 0, 1, 0),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ), LineBorder(inputRectColor, 1, true)
        )
        mSearchBtn.icon = SdkIcons.search
        mClearBtn.icon = SdkIcons.close

        mListLayoutBtn.icon = SdkIcons.list
        if (mLayoutMode == 0) {
            mListLayoutBtn.background = UIConfig.getMousePressColor()
        }

        mGridLayoutBtn.icon = SdkIcons.grid
        if (mLayoutMode == 1) {
            mGridLayoutBtn.background = UIConfig.getMousePressColor()
        }

        mRefreshBtn.icon = SdkIcons.refresh

        mRootPathJPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 2, 2, 2),
                LineBorder(UIConfig.getInputUnFocusColor(), 1, true)
            ),
            BorderFactory.createEmptyBorder(0, 4, 0, 4)
        )

        mScrollPane.border = LineBorder(UIConfig.getLineColor(), 0, 0, 1, 0)
        setNewImages()
    }

    companion object {
        private const val ROOT_PATH = "Image Preview Root Path"
    }
}
