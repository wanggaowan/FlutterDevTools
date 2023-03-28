package com.wanggaowan.tools.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.wanggaowan.tools.listener.SimpleComponentListener
import com.wanggaowan.tools.utils.msg.Toast
import java.awt.*
import java.awt.event.ComponentEvent
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
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
) : JDialog() {
    private lateinit var mBtnOk: JButton
    private lateinit var mJChosenFolder: MyLabel
    private lateinit var mJRenamePanel: JPanel

    private var mChosenFolderWidth = -1

    /**
     * 选中的文件夹
     */
    private var mSelectedFolder: VirtualFile? = null

    private var mRenameFileMap = mutableMapOf<String, MutableList<RenameEntity>>()

    /**
     * 确定按钮点击监听
     */
    private var mOkActionListener: (() -> Unit)? = null

    init {
        mSelectedFolder = initialFile
        setTitle(title)

        val rootPanel = JPanel(BorderLayout())
        contentPane = rootPanel
        rootPanel.preferredSize = JBUI.size(680, 300)
        rootPanel.add(createRenameFilePanel(renameFiles), BorderLayout.CENTER)
        rootPanel.add(createAction(), BorderLayout.SOUTH)
        rootPanel.addComponentListener(object : SimpleComponentListener() {
            override fun componentResized(p0: ComponentEvent?) { // 窗体大小改变时，动态计算显示导入路径文本组件宽度
                var maxWidth = rootPanel.width - JBUI.scale(300)
                if (maxWidth < 0) {
                    maxWidth = 0
                }
                setChosenFolderWrapWidth(maxWidth)
            }
        })

        pack()

        if (initialFile != null) {
            mBtnOk.isEnabled = true
            mJChosenFolder.text = initialFile.path
            setChosenFolderWrapWidth(JBUI.scale(400))
        } else {
            mBtnOk.isEnabled = false
        }
    }

    private fun setChosenFolderWrapWidth(maxWidth: Int = Int.MAX_VALUE) {
        val insets = mJChosenFolder.insets
        val insetsHorizontal = insets.left + insets.right
        val borderInsets = mJChosenFolder.border?.getBorderInsets(mJChosenFolder)
        val borderHorizontal = if (borderInsets == null) 0 else borderInsets.left + borderInsets.right
        val viewAclWith = mJChosenFolder.originalTextWidth + insetsHorizontal + borderHorizontal
        if (mChosenFolderWidth != viewAclWith) {
            if (maxWidth > viewAclWith) {
                mChosenFolderWidth = viewAclWith
                mJChosenFolder.preferredSize = Dimension(viewAclWith, JBUI.scale(30))
            } else {
                mChosenFolderWidth = maxWidth
                mJChosenFolder.preferredSize = Dimension(maxWidth, JBUI.scale(30))
            }
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
        scrollPane.border = LineBorder(UIColor.LINE_COLOR, 0, 0, 1, 0)
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
            val fontSize = (UIUtil.getFontSize(UIUtil.FontSize.NORMAL) + 2).toInt()
            type.font = Font(type.font.name, Font.BOLD, fontSize)
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

                val imageView = ImageView(File(it2.oldFile.path))
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
                val existFileImageView = ImageView(if (existFile != null) File(existFile.path) else null)
                existFileImageView.preferredSize = JBUI.size(25)
                existFileImageView.border = BorderFactory.createEmptyBorder(2, 2, 2, 5)
                existFileImageView.isVisible = existFile != null
                existFileImageView.maximumSize = JBUI.size(25)
                box2.add(existFileImageView)

                val hintStr =
                    if (isInMap) "导入的文件存在相同文件，勾选则导入最后一个同名文件，否则导入第一个同名文件" else "已存在同名文件,是否覆盖原文件？不勾选则跳过导入"
                val hint = JCheckBox(hintStr)
                hint.foreground = JBColor.RED
                hint.font = UIUtil.getFont(UIUtil.FontSize.MINI, rename.font)
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
            if (component is JLabel) { // 分类标题
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
        entity: RenameEntity, hint: JCheckBox?, imageView: ImageView?
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
        mJChosenFolder = MyLabel()
        mJChosenFolder.strictMode = true
        mJChosenFolder.ellipsize = MyLabel.TruncateAt.MIDDLE
        mJChosenFolder.border = BorderFactory.createEmptyBorder(0, 0, 0, 10)
        bottomPane.add(mJChosenFolder)
        val chooseFolderBtn = JButton("change")
        bottomPane.add(chooseFolderBtn)
        bottomPane.add(Box.createHorizontalGlue())
        val cancelBtn = JButton("cancel")
        bottomPane.add(cancelBtn)
        mBtnOk = JButton("import")
        bottomPane.add(mBtnOk)

        chooseFolderBtn.addActionListener {
            isVisible = false
            val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            val selectedFolder = FileChooser.chooseFile(descriptor, project, mSelectedFolder)
            isVisible = true
            mBtnOk.isEnabled = mSelectedFolder != null
            if (selectedFolder != null) {
                val oldSelectedFolder = mSelectedFolder
                mSelectedFolder = selectedFolder
                mJChosenFolder.text = mSelectedFolder?.path
                setChosenFolderWrapWidth()
                if (oldSelectedFolder?.path != mSelectedFolder?.path) {
                    refreshRenamePanel()
                }
            }
        }

        cancelBtn.addActionListener {
            isVisible = false
        }

        mBtnOk.addActionListener {
            doOKAction()
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
