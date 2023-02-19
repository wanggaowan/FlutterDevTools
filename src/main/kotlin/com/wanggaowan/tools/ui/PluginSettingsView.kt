package com.wanggaowan.tools.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import kotlinx.html.InputType
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 插件设置界面
 */
class PluginSettingsView {
    val panel: JPanel
    private val imagesDir = JBTextField()
    private val imagesDartFileGeneratePath = JBTextField()

    init {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("图片资源路径: "), imagesDir, 1, false)
            .addLabeledComponent(JBLabel("生成images.dart放置路径: "), imagesDartFileGeneratePath, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    val preferredFocusedComponent: JComponent
        get() = imagesDir

    /**
     * 图片资源路径，用于生成images.dart文件
     */
    fun getImagesDir(): String {
        return imagesDir.text
    }

    /**
     * 设置图片资源路径，用于生成images.dart文件
     */
    fun setImagesDir(text: String?) {
        imagesDir.text = text
    }

    /**
     * 图片资源引用文件存放的路径
     */
    fun getImagesDartFileGeneratePath(): String {
        return imagesDartFileGeneratePath.text
    }

    /**
     * 设置图片资源引用文件存放的路径
     */
    fun setImagesDartFileGeneratePath(text: String?) {
        imagesDartFileGeneratePath.text = text
    }
}
