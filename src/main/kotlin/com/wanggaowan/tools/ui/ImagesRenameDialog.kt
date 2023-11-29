package com.wanggaowan.tools.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
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
        rootPanel.preferredSize = JBUI.size(500, 300)
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
        mJRenamePanel.border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
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
        cc.weightx = 1.0

        mRenameFileList.forEach {
            val box = Box.createHorizontalBox()
            cc.gridy = depth++
            mJRenamePanel.add(box, cc)

            val imageView = ImageView(File(it.oldFile.path))
            imageView.preferredSize = JBUI.size(34)
            imageView.maximumSize = JBUI.size(34)
            imageView.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
            box.add(imageView)

            val rename = ExtensionTextField(it.newName, placeHolder = it.oldName)
            rename.minimumSize = JBUI.size(400, 34)
            box.add(rename)

            val box2 = Box.createHorizontalBox()
            cc.gridy = depth++
            box2.border = BorderFactory.createEmptyBorder(2, 0, 2, 0)
            mJRenamePanel.add(box2, cc)

            val existFile = isImageExist(it)
            val existFileImageView = ImageView(if (existFile != null) File(existFile.path) else null)
            existFileImageView.preferredSize = JBUI.size(34,16)
            existFileImageView.minimumSize = JBUI.size(34,16)
            existFileImageView.maximumSize = JBUI.size(34,16)
            existFileImageView.border = BorderFactory.createEmptyBorder(0, 9, 0, 9)
            existFileImageView.isVisible = existFile != null
            box2.add(existFileImageView)

            val hintStr = "已存在同名文件,是否继续重命名？不勾选则跳过重命名"
            val hint = JCheckBox(hintStr)
            hint.foreground = JBColor.RED
            hint.font = UIUtil.getFont(UIUtil.FontSize.MINI, rename.font)
            it.existFile = existFile != null
            hint.isVisible = existFile != null
            box2.add(hint)

            box2.add(Box.createHorizontalGlue())

            hint.addChangeListener { _ ->
                it.coverExistFile = hint.isSelected
            }

            // 文本改变监听
            rename.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(p0: DocumentEvent?) {
                    val str = rename.text.trim()
                    it.newName = str.ifEmpty { it.oldName }
                    refreshRenamePanel()
                }

                override fun removeUpdate(p0: DocumentEvent?) {
                    val str = rename.text.trim()
                    it.newName = str.ifEmpty { it.oldName }
                    refreshRenamePanel()
                }

                override fun changedUpdate(p0: DocumentEvent?) {
                    val str = rename.text.trim()
                    it.newName = str.ifEmpty { it.oldName }
                    refreshRenamePanel()
                }
            })
        }

        val placeHolder = JLabel()
        cc.weighty = 1.0
        cc.gridy = depth++
        mJRenamePanel.add(placeHolder, cc)
    }

    private fun refreshRenamePanel() {
        val components = mJRenamePanel.components
        for (index in 0 until ((components.size - 1) / 2)) {
            val hintRoot = components[index * 2 + 1] as Box?
            val imageView = hintRoot?.getComponent(0) as? ImageView
            val checkBox = hintRoot?.getComponent(1) as? JCheckBox
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

