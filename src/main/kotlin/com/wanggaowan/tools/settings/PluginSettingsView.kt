package com.wanggaowan.tools.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.wanggaowan.tools.ui.JLine
import com.wanggaowan.tools.ui.UIColor
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 插件设置界面
 */
class PluginSettingsView() {
    val panel: JPanel
    val imagesDir = JBTextField()
    val imagesRefFilePath = JBTextField()
    val imagesRefClassName = JBTextField()

    val copyAndroidStrUseSimpleMode = JBCheckBox("复制 android <string/> 转为为flutter多语言采用简易模式")
    val extractStr2L10nShowRenameDialog = JBCheckBox("展示重命名弹窗")
    val extractStr2L10nTranslateOther = JBCheckBox("翻译成其它语言")

    val codeCompleteTypeDirectDev = JBCheckBox("支持dev_dependencies依赖的库自动补全")
    val codeCompleteTypeTransitive = JBCheckBox("支持间接依赖的库自动补全")

    val aliAk = JBTextField()
    val aliSk = JBTextField()

    init {
        var builder = FormBuilder.createFormBuilder()
        builder
            .addComponent(createCategoryTitle("生成资源配置"))
            .addLabeledComponent(createItemLabel("图片资源路径: "), imagesDir, 1, false)
            .addLabeledComponent(createItemLabel("图片引用文件路径: "), imagesRefFilePath, 1, false)
            .addLabeledComponent(createItemLabel("图片引用文件类名称: "), imagesRefClassName, 1, false)


        builder = builder.addComponent(createCategoryTitle("提取多语言设置", marginTop = 10), 1)
            .addLabeledComponent(createItemLabel("阿里翻译AK: ", marginLeft = 20), aliAk, 1, false)
            .addLabeledComponent(createItemLabel("阿里翻译SK: ", marginLeft = 20), aliSk, 1, false)
        extractStr2L10nShowRenameDialog.border = JBUI.Borders.empty(4, 10, 0, 0)
        builder = builder.addComponent(extractStr2L10nShowRenameDialog, 1)
        // extractStr2L10nTranslateOther.border = JBUI.Borders.empty(4, 10, 0, 0)
        // builder = builder.addComponent(extractStr2L10nTranslateOther, 1)

        builder = builder.addComponent(createCategoryTitle("代码自动补全", marginTop = 10), 1)
        codeCompleteTypeDirectDev.border = JBUI.Borders.empty(4, 10, 0, 0)
        builder = builder.addComponent(codeCompleteTypeDirectDev, 1)
        codeCompleteTypeTransitive.border = JBUI.Borders.empty(4, 10, 0, 0)
        builder = builder.addComponent(codeCompleteTypeTransitive, 1)

        builder = builder.addComponent(createCategoryTitle("其它设置", marginTop = 10), 1)
        copyAndroidStrUseSimpleMode.border = JBUI.Borders.empty(4, 10, 0, 0)
        builder = builder.addComponent(copyAndroidStrUseSimpleMode, 1)

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
            panel.border = JBUI.Borders.empty(marginTop ?: 0, marginLeft ?: 0, 0, 0)
        }
        return panel
    }

    private fun createItemLabel(title: String, marginLeft: Int? = 10): JLabel {
        val jLabel = JBLabel(title)
        if (marginLeft != null) {
            jLabel.border = JBUI.Borders.emptyLeft(marginLeft)
        }
        return jLabel
    }

    val preferredFocusedComponent: JComponent
        get() = imagesDir
}
