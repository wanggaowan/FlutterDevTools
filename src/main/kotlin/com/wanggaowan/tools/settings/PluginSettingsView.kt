package com.wanggaowan.tools.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBInsets
import com.wanggaowan.tools.ui.JLine
import com.wanggaowan.tools.ui.UIColor
import io.flutter.pub.PubRoot
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 插件设置界面
 */
class PluginSettingsView(pubRoot: PubRoot?) {
    val panel: JPanel
    val imagesDir = JBTextField()
    val imagesRefFilePath = JBTextField()
    val imagesRefFileName = JBTextField()
    val imagesRefClassName = JBTextField()

    // example项目，一般是写插件的项目会存在example目录
    val exampleImagesDir = JBTextField()
    val exampleImagesRefFilePath = JBTextField()
    val exampleImagesRefFileName = JBTextField()
    val exampleImagesRefClassName = JBTextField()

    val copyAndroidStrUseSimpleMode = JBCheckBox("复制 android <string/> 转为为flutter多语言采用简易模式")
    val extractStr2L10nShowRenameDialog = JBCheckBox("提取文本为多语言时展示重命名弹窗")

    init {
        var builder = FormBuilder.createFormBuilder()
        builder
            .addComponent(createCategoryTitle("生成资源配置"))
            .addLabeledComponent(createItemLabel("图片资源路径: "), imagesDir, 1, false)
            .addLabeledComponent(createItemLabel("图片引用文件路径: "), imagesRefFilePath, 1, false)
            .addLabeledComponent(createItemLabel("图片引用文件名称: "), imagesRefFileName, 1, false)
            .addLabeledComponent(createItemLabel("图片引用文件类名称: "), imagesRefClassName, 1, false)
        if (pubRoot != null && pubRoot.exampleDir != null) {
            val example = createCategoryTitle("Example项目", 10, marginLeft = 10)
            builder = builder.addComponent(example)
                .addLabeledComponent(createItemLabel("图片资源路径: ", marginLeft = 20), exampleImagesDir, 1, false)
                .addLabeledComponent(createItemLabel("图片引用文件路径: ", marginLeft = 20), exampleImagesRefFilePath, 1, false)
                .addLabeledComponent(createItemLabel("图片引用文件名称: ", marginLeft = 20), exampleImagesRefFileName, 1, false)
                .addLabeledComponent(createItemLabel("图片引用文件类名称: ", marginLeft = 20), exampleImagesRefClassName, 1, false)
        }

        builder = builder.addComponent(createCategoryTitle("其它设置", marginTop = 10), 1)

        copyAndroidStrUseSimpleMode.border = BorderFactory.createEmptyBorder(4, 10, 0, 0)
        builder = builder.addComponent(copyAndroidStrUseSimpleMode, 1)
        extractStr2L10nShowRenameDialog.border = BorderFactory.createEmptyBorder(4, 10, 0, 0)
        builder = builder.addComponent(extractStr2L10nShowRenameDialog, 1)

        panel = builder.addComponentFillVertically(JPanel(), 0).panel
    }

    private fun createCategoryTitle(title: String, marginTop: Int? = null, marginLeft: Int? = null): JComponent {
        val panel = JPanel()
        panel.layout = BorderLayout()

        val jLabel = JLabel(title)
        panel.add(jLabel, BorderLayout.WEST)

        val divider = JLine(UIColor.LINE_COLOR, JBInsets(0, 10, 0, 0))
        panel.add(divider, BorderLayout.CENTER)

        if (marginTop != null || marginLeft != null) {
            panel.border = BorderFactory.createEmptyBorder(marginTop ?: 0, marginLeft ?: 0, 0, 0)
        }
        return panel
    }

    private fun createItemLabel(title: String, marginLeft: Int? = 10): JLabel {
        val jLabel = JBLabel(title)
        if (marginLeft != null) {
            jLabel.border = BorderFactory.createEmptyBorder(0, marginLeft, 0, 0)
        }
        return jLabel
    }

    val preferredFocusedComponent: JComponent
        get() = imagesDir
}
