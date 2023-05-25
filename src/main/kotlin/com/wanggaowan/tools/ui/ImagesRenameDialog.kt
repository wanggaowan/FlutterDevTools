package com.wanggaowan.tools.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 图片重命名
 *
 * @author Created by wanggaowan on 2023/5/25 10:54
 */
class ImagesRenameDialog(
    val project: Project,
    /**
     * 需要重命名的文件
     */
    renameFiles: List<VirtualFile>? = null,
) : JDialog() {
    private lateinit var mBtnOk: JButton
    private lateinit var mJRenamePanel: JPanel

    private var mRenameFileList = mutableListOf<RenameEntity>()

    /**
     * 确定按钮点击监听
     */
    private var mOkActionListener: (() -> Unit)? = null

    init {
        title = "重命名"

        val rootPanel = JPanel(BorderLayout())
        contentPane = rootPanel
        rootPanel.preferredSize = JBUI.size(580, 300)
        rootPanel.add(createRenameFilePanel(renameFiles), BorderLayout.CENTER)
        rootPanel.add(createAction(), BorderLayout.SOUTH)

        pack()
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
        mRenameFileList.clear()
        files?.forEach {
            mRenameFileList.add(RenameEntity(it.name, it, it.name))
        }

        mJRenamePanel = JPanel(GridBagLayout())
        initRenamePanel()
        val scrollPane = ScrollPaneFactory.createScrollPane(mJRenamePanel)
        scrollPane.border = BorderFactory.createCompoundBorder(
            LineBorder(UIColor.LINE_COLOR, 0, 0, 1, 0),
            BorderFactory.createEmptyBorder(5, 0, 0, 0)
        )
        return scrollPane
    }

    private fun initRenamePanel() {
        mJRenamePanel.removeAll()
        var depth = 0
        val cc = GridBagConstraints()
        cc.fill = GridBagConstraints.HORIZONTAL

        mRenameFileList.forEach {
            cc.gridy = depth

            // 左边部分
            val titleBox = Box.createHorizontalBox()
            titleBox.preferredSize = JBUI.size(250, 35)
            titleBox.minimumSize = JBUI.size(250, 35)
            cc.weightx = 0.0
            cc.gridx = 0
            mJRenamePanel.add(titleBox, cc)

            val imageView = ImageView(File(it.oldFile.path))
            imageView.preferredSize = JBUI.size(35)
            imageView.maximumSize = JBUI.size(35)
            imageView.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
            titleBox.add(imageView)

            val title = JLabel(it.oldName + "：")
            titleBox.add(title)

            // 右边部分
            val box = Box.createVerticalBox()
            cc.weightx = 1.0
            cc.gridx = 1
            mJRenamePanel.add(box, cc)

            val rename = JTextField(it.newName)
            rename.minimumSize = JBUI.size(0, 35)
            rename.maximumSize = JBUI.size(Int.MAX_VALUE, 35)
            box.add(rename)

            val box2 = Box.createHorizontalBox()
            val existFile = isImageExist(it)
            val existFileImageView = ImageView(if (existFile != null) File(existFile.path) else null)
            existFileImageView.preferredSize = JBUI.size(25)
            existFileImageView.border = BorderFactory.createEmptyBorder(2, 2, 2, 5)
            existFileImageView.isVisible = existFile != null
            existFileImageView.maximumSize = JBUI.size(25)
            box2.add(existFileImageView)

            val hintStr = "已存在同名文件,是否继续重命名？不勾选则跳过重命名"
            val hint = JCheckBox(hintStr)
            hint.foreground = JBColor.RED
            hint.font = UIUtil.getFont(UIUtil.FontSize.MINI, rename.font)
            it.existFile = existFile != null
            hint.isVisible = existFile != null
            box2.add(hint)

            box2.add(Box.createHorizontalGlue())
            box.add(box2)

            hint.addChangeListener { _ ->
                it.coverExistFile = hint.isSelected
            }

            // 文本改变监听
            rename.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(p0: DocumentEvent?) {
                    val str = rename.text.trim()
                    it.newName = str
                    refreshRenamePanel()
                }

                override fun removeUpdate(p0: DocumentEvent?) {
                    val str = rename.text.trim()
                    it.newName = str
                    refreshRenamePanel()
                }

                override fun changedUpdate(p0: DocumentEvent?) {
                    val str = rename.text.trim()
                    it.newName = str
                    refreshRenamePanel()
                }
            })
            depth++
        }

        val placeHolder = JLabel()
        cc.weighty = 1.0
        cc.gridy = depth++
        mJRenamePanel.add(placeHolder, cc)
    }

    private fun refreshRenamePanel() {
        val components = mJRenamePanel.components
        for (index in 0 until ((components.size - 1) / 2)) {
            val rightRoot = components[index * 2 + 1] as Box?
            val rightHintRoot = rightRoot?.getComponent(1) as Box?
            val imageView = rightHintRoot?.getComponent(0) as? ImageView
            val checkBox = rightHintRoot?.getComponent(1) as? JCheckBox
            refreshHintVisible(mRenameFileList[index], checkBox, imageView)
        }
    }

    private fun refreshHintVisible(
        entity: RenameEntity, hint: JCheckBox?, imageView: ImageView?
    ) {
        val existFile2 = isImageExist(entity)
        entity.existFile = existFile2 != null
        val hintStr = "已存在同名文件,是否继续重命名？不勾选则跳过重命名"
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
        bottomPane.add(Box.createHorizontalGlue())
        val cancelBtn = JButton("cancel")
        bottomPane.add(cancelBtn)
        mBtnOk = JButton("ok")
        bottomPane.add(mBtnOk)

        cancelBtn.addActionListener {
            isVisible = false
        }

        mBtnOk.addActionListener {
            doOKAction()
        }

        return bottomPane
    }

    private fun doOKAction() {
        isVisible = false
        mOkActionListener?.invoke()
    }

    /**
     * 获取重命名文件Map，key为原始文件名称，value为重命名的值
     */
    fun getRenameFileList(): List<RenameEntity> {
        return mRenameFileList
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
    private fun isImageExist(entity: RenameEntity): VirtualFile? {
        if (entity.oldName == entity.newName) {
            return null
        }

        var rootDir = entity.oldFile.parent.path
        rootDir = if (rootDir == "1.5x"
            || rootDir == "2.0x"
            || rootDir == "3.0x"
            || rootDir == "4.0x"
        ) {
            entity.oldFile.parent.parent.path
        } else {
            rootDir
        }

        var selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/${entity.newName}")
        if (selectFile != null) {
            return selectFile
        }

        selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/1.5x/${entity.newName}")
        if (selectFile != null) {
            return selectFile
        }

        selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/2.0x/${entity.newName}")
        if (selectFile != null) {
            return selectFile
        }

        selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/3.0x/${entity.newName}")
        if (selectFile != null) {
            return selectFile
        }

        selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/4.0x/${entity.newName}")
        if (selectFile != null) {
            return selectFile
        }

        return null
    }
}

