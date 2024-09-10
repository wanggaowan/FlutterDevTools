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
import com.intellij.usages.Usage
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.wanggaowan.tools.ui.icon.IconPanel
import com.wanggaowan.tools.utils.msg.Toast
import java.awt.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 导入图片资源后选择导入的目标文件夹弹窗，兼带重命名导入文件名称功能
 *
 * @author Created by wanggaowan on 2022/7/18 08:37
 */
class ImportImageFolderChooser(
    val project: Project,
    title: String,
    initialFile: VirtualFile? = null,
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

    /**
     * 确定按钮点击监听
     */
    private var mCancelActionListener: (() -> Unit)? = null

    private var mDoOk = false

    init {
        mSelectedFolder = initialFile
        setTitle(title)

        val rootPanel = JPanel(BorderLayout())
        contentPane = rootPanel
        rootPanel.preferredSize = JBUI.size(500, 300)
        rootPanel.add(createRenameFilePanel(renameFiles), BorderLayout.CENTER)
        rootPanel.add(createAction(), BorderLayout.SOUTH)
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
        if (!visible && !mDoOk) {
            mCancelActionListener?.invoke()
        }
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
                ""
            }

            var list = mRenameFileMap[parentName]
            if (list == null) {
                list = mutableListOf()
                mRenameFileMap[parentName] = list
            }

            list.add(RenameEntity(it.name, it, it.name))
        }

        mJRenamePanel = JPanel(GridBagLayout())
        initRenamePanel()
        val scrollPane = ScrollPaneFactory.createScrollPane(mJRenamePanel)
        scrollPane.border = JBUI.Borders.customLineBottom(UIColor.LINE_COLOR)
        return scrollPane
    }

    private fun initRenamePanel() {
        mJRenamePanel.removeAll()
        var depth = 0
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.HORIZONTAL
        c.weightx = 1.0

        mRenameFileMap.forEach {
            val type = JLabel((it.key.ifEmpty { "Other" }) + "：")
            type.border = BorderFactory.createEmptyBorder(if (depth > 0) 10 else 0, 5, 5, 5)
            val fontSize = (UIUtil.getFontSize(UIUtil.FontSize.NORMAL) + 2).toInt()
            type.font = Font(type.font.name, Font.BOLD, fontSize)
            c.gridy = depth++
            mJRenamePanel.add(type, c)

            val cc = GridBagConstraints()
            cc.fill = GridBagConstraints.HORIZONTAL
            cc.weightx = 1.0

            it.value.forEach { it2 ->
                val panel = JPanel(GridBagLayout())
                panel.border = BorderFactory.createEmptyBorder(0, 5, 5, 5)
                c.gridy = depth++
                mJRenamePanel.add(panel, c)

                val box = Box.createHorizontalBox()
                cc.gridy = 0
                panel.add(box, cc)

                val imageView = ChessBoardPanel().apply {
                    val iconPanel = IconPanel(it2.oldFile.path, true)
                    add(iconPanel)
                }
                imageView.preferredSize = JBUI.size(34)
                imageView.maximumSize = JBUI.size(34)
                imageView.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
                box.add(imageView)

                val rename = ExtensionTextField(it2.newName, placeHolder = it2.oldName)
                rename.minimumSize = JBUI.size(400, 34)
                box.add(rename)

                val box2 = Box.createHorizontalBox()
                cc.gridy = 1
                box2.border = BorderFactory.createEmptyBorder(2, 0, 2, 0)
                panel.add(box2, cc)

                val (existFile, isInMap) = isImageExist(it2)
                val existFileImageView = ChessBoardPanel().apply {
                    val iconPanel = IconPanel(existFile?.path, true)
                    add(iconPanel)
                }
                existFileImageView.preferredSize = JBUI.size(34, 16)
                existFileImageView.minimumSize = JBUI.size(34, 16)
                existFileImageView.maximumSize = JBUI.size(34, 16)
                existFileImageView.border = BorderFactory.createEmptyBorder(0, 9, 0, 9)
                existFileImageView.isVisible = existFile != null
                box2.add(existFileImageView)

                val hintStr =
                    if (isInMap) "导入的文件存在相同文件，勾选则导入最后一个同名文件，否则导入第一个同名文件" else "已存在同名文件,是否覆盖原文件？不勾选则跳过导入"
                val hint = JCheckBox(hintStr)
                hint.foreground = JBColor.RED
                hint.font = UIUtil.getFont(UIUtil.FontSize.MINI, rename.font)
                it2.existFile = existFile != null
                hint.isVisible = existFile != null
                hint.minimumSize = JBUI.size(400, 22)
                box2.add(hint)
                box2.add(Box.createHorizontalGlue())

                hint.addChangeListener {
                    it2.coverExistFile = hint.isSelected
                }

                // 文本改变监听
                rename.document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str.ifEmpty { it2.oldName }
                        refreshRenamePanel()
                    }

                    override fun removeUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str.ifEmpty { it2.oldName }
                        refreshRenamePanel()
                    }

                    override fun changedUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str.ifEmpty { it2.oldName }
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
                val hintRoot = component.getComponent(1) as Box?
                val imageView = hintRoot?.getComponent(0) as? ChessBoardPanel
                val checkBox = hintRoot?.getComponent(1) as? JCheckBox
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
        entity: RenameEntity, hint: JCheckBox?, imageView: ChessBoardPanel?
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
                (imageView?.getComponent(0) as? IconPanel)?.apply {
                    setIcon(existFile2.path, true)
                }
            } else {
                imageView?.isVisible = false
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
        mDoOk = true
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
     * 设置取消按钮点击监听
     */
    fun setCancelActionListener(listener: (() -> Unit)?) {
        mCancelActionListener = listener
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
     * 导入的原文件
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
    var coverExistFile: Boolean = false,

    val usages: MutableSet<Usage> = mutableSetOf()
)
