package com.wanggaowan.tools.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import io.flutter.pub.PubRoot
import java.awt.Font
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

    init {
        var builder = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("图片资源路径: "), imagesDir, 1, false)
            .addLabeledComponent(JBLabel("图片引用文件路径: "), imagesRefFilePath, 1, false)
            .addLabeledComponent(JBLabel("图片引用文件名称: "), imagesRefFileName, 1, false)
            .addLabeledComponent(JBLabel("图片引用文件类名称: "), imagesRefClassName, 1, false)
        if (pubRoot != null && pubRoot.exampleDir != null) {
            val example = JLabel("Example项目:")
            example.border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
            val font = example.font
            val fontSize = if (font == null) 16 else font.size + 2
            example.font = Font(null, Font.BOLD, fontSize)
            builder = builder.addComponent(example)
                .addLabeledComponent(JBLabel("图片资源路径: "), exampleImagesDir, 1, false)
                .addLabeledComponent(JBLabel("图片引用文件路径: "), exampleImagesRefFilePath, 1, false)
                .addLabeledComponent(JBLabel("图片引用文件名称: "), exampleImagesRefFileName, 1, false)
                .addLabeledComponent(JBLabel("图片引用文件类名称: "), exampleImagesRefClassName, 1, false)
        }
        panel = builder.addComponentFillVertically(JPanel(), 0).panel
    }

    val preferredFocusedComponent: JComponent
        get() = imagesDir
}
