package com.wanggaowan.tools.ui

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.usages.Usage
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.lang.dart.DartBundle
import com.wanggaowan.tools.extensions.findusage.FindProgress
import com.wanggaowan.tools.extensions.findusage.FindUsageManager
import com.wanggaowan.tools.utils.XUtils
import org.jetbrains.kotlin.idea.core.util.toPsiFile
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
    renameFiles: Array<VirtualFile>? = null,
    private val renameSameNameOtherFiles: Boolean = false
) : JDialog() {
    private lateinit var mBtnPreview: JButton
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
    private fun createRenameFilePanel(files: Array<VirtualFile>?): JComponent {
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
            existFileImageView.preferredSize = JBUI.size(34, 16)
            existFileImageView.minimumSize = JBUI.size(34, 16)
            existFileImageView.maximumSize = JBUI.size(34, 16)
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
                    checkBtnEnable()
                    refreshRenamePanel()
                }

                override fun removeUpdate(p0: DocumentEvent?) {
                    val str = rename.text.trim()
                    it.newName = str.ifEmpty { it.oldName }
                    checkBtnEnable()
                    refreshRenamePanel()
                }

                override fun changedUpdate(p0: DocumentEvent?) {
                    val str = rename.text.trim()
                    it.newName = str.ifEmpty { it.oldName }
                    checkBtnEnable()
                    refreshRenamePanel()
                }
            })
        }

        val placeHolder = JLabel()
        cc.weighty = 1.0
        cc.gridy = depth++
        mJRenamePanel.add(placeHolder, cc)
    }

    private fun checkBtnEnable() {
        var enable = false
        mRenameFileList.forEach {
            if (it.oldName != it.newName) {
                if (!it.existFile || it.coverExistFile) {
                    enable = true
                    return@forEach
                }
            }
        }
        mBtnPreview.isEnabled = enable
        mBtnOk.isEnabled = enable
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

        mBtnPreview = JButton("Preview")
        mBtnPreview.isEnabled = false
        bottomPane.add(mBtnPreview)

        mBtnOk = JButton("Refactor")
        mBtnOk.isEnabled = false
        bottomPane.add(mBtnOk)

        cancelBtn.addActionListener {
            isVisible = false
        }

        mBtnPreview.addActionListener {
            doOKAction(true)
        }

        mBtnOk.addActionListener {
            doOKAction(false)
        }

        return bottomPane
    }

    private fun doOKAction(isPreview: Boolean) {
        isVisible = false
        findUsages(isPreview)
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
        rootDir = if (XUtils.isImageVariantsFolder(rootDir)) {
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

    private fun findUsages(isPreview: Boolean) {
        FindUsageManager(project).findUsages(
            mRenameFileList.mapNotNull {
                if (it.existFile && !it.coverExistFile) {
                    return@mapNotNull null
                } else if (!renameSameNameOtherFiles) {
                    if (XUtils.isImageVariantsFolder(it.oldFile.parent?.name)) {
                        return@mapNotNull null
                    }
                }
                it.oldFile.toPsiFile(project)
            }.toTypedArray(),
            progressTitle = {
                when (it) {
                    null -> {
                        "Find rename files usages"
                    }

                    is PsiNamedElement -> {
                        "Find rename file:${it.name} usages"
                    }

                    else -> {
                        "Find usages"
                    }
                }
            },
            findProgress = object : FindProgress() {
                override fun find(target: PsiElement, usage: Usage) {
                    mRenameFileList.forEach {
                        if (target is PsiFile && target.virtualFile == it.oldFile) {
                            it.usages.add(usage)
                            return@forEach
                        }
                    }
                }

                override fun end(indicator: ProgressIndicator) {
                    if (isPreview) {
                        ApplicationManager.getApplication().invokeLater {
                            previewRefactoring()
                        }
                    } else {
                        mOkActionListener?.invoke()
                    }
                }
            })
    }

    private fun previewRefactoring() {
        val presentation = UsageViewPresentation()
        presentation.tabText = RefactoringBundle.message("usageView.tabText")
        presentation.isShowCancelButton = true
        if (mRenameFileList.size == 1) {
            val entity = mRenameFileList[0]
            presentation.targetsNodeText =
                RefactoringBundle.message("0.to.be.renamed.to.1.2", entity.oldName, "", entity.newName)
        } else {
            presentation.targetsNodeText =
                RefactoringBundle.message("renaming.command.name", "${mRenameFileList.size} files")
        }
        presentation.nonCodeUsagesString = DartBundle.message("usages.in.comments.to.rename")
        presentation.codeUsagesString = DartBundle.message("usages.in.code.to.rename")
        presentation.setDynamicUsagesString(DartBundle.message("dynamic.usages.to.rename"))
        presentation.isUsageTypeFilteringAvailable = false

        val targets = mRenameFileList.mapNotNull {
            val psiFile = it.oldFile.toPsiFile(project)
            if (psiFile == null) {
                null
            } else {
                PsiElement2UsageTargetAdapter(psiFile, true)
            }
        }.toTypedArray()

        val usageArray = mRenameFileList.fold(mutableSetOf<Usage>()) { acc, entity ->
            acc.addAll(entity.usages)
            return@fold acc
        }.toTypedArray()
        val usageView = UsageViewManager.getInstance(project).showUsages(targets, usageArray, presentation)
        val message = if (mRenameFileList.size == 1) {
            val entity = mRenameFileList[0]
            "Rename File '${entity.oldName} to '${entity.newName}'"
        } else {
            "Rename ${mRenameFileList.size} Files"
        }
        usageView.addPerformOperationAction(
            { mOkActionListener?.invoke() },
            message,
            DartBundle.message("rename.need.reRun"),
            RefactoringBundle.message("usageView.doAction"), false
        )
    }
}

