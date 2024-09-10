package com.wanggaowan.tools.actions.addRouter

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.wanggaowan.tools.ui.ImageButton
import com.wanggaowan.tools.utils.msg.Toast
import icons.FlutterDevToolsIcons
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 添加路由配置界面
 */
class AddRouterDialog(val project: Project) : DialogWrapper(project, false) {
    private var mContentPane: JPanel
    private val mJPath = JTextField()
    private val mJPage = JTextField()
    private val mJDoc = JTextField()
    private val mJPramsPanel = JPanel(GridBagLayout())
    private val mJReturnPramPanel = JPanel(GridBagLayout())
    private var mUserInputPath: Boolean = false
    private var mSetPath = false

    init {
        mContentPane = initContentPanel()
        init()
    }

    override fun createCenterPanel(): JComponent = mContentPane

    override fun getPreferredFocusedComponent(): JComponent = mJPage

    override fun doOKAction() {
        val pagePath = getPagePath()
        if (pagePath.isEmpty()) {
            Toast.show(mJPath, MessageType.ERROR, "请输入页面路径")
            return
        }

        val pageName = getPageName()
        if (pageName.isEmpty()) {
            Toast.show(mJPage, MessageType.ERROR, "请输入页面名称")
            return
        }

        super.doOKAction()
    }

    private fun initContentPanel(): JPanel {
        val jParamsTitlePanel = JPanel(BorderLayout())
        jParamsTitlePanel.border = BorderFactory.createEmptyBorder(10, 0, 0, 0)

        val label = JBLabel("参数")
        label.alignmentX = 0f
        jParamsTitlePanel.add(label, BorderLayout.CENTER)

        val paramsAdd = ImageButton(icon = FlutterDevToolsIcons.add)
        paramsAdd.preferredSize = JBUI.size(16)
        jParamsTitlePanel.add(paramsAdd, BorderLayout.EAST)

        val verticalSpace = JBLabel()
        val c = GridBagConstraints()
        c.gridwidth = 4
        c.gridx = 0
        c.gridy = 0
        c.weightx = 1.0
        c.weighty = 1.0
        c.fill = GridBagConstraints.BOTH
        mJPramsPanel.add(verticalSpace, c)

        paramsAdd.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                createParamItem(verticalSpace)
            }
        })

        mJPath.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(p0: DocumentEvent?) {
                val str = mJPath.text.trim()
                if (!mUserInputPath) {
                    if (str != "/${mJPage.text.trim()}") {
                        mUserInputPath = true
                    }
                } else if (str.isEmpty()) {
                    mUserInputPath = false
                }
            }

            override fun removeUpdate(p0: DocumentEvent?) {
                if (mSetPath) {
                    mSetPath = false
                    return
                }

                val str = mJPath.text.trim()
                if (!mUserInputPath) {
                    if (str != "/${mJPage.text.trim()}") {
                        mUserInputPath = true
                    }
                } else if (str.isEmpty()) {
                    mUserInputPath = false
                }
            }

            override fun changedUpdate(p0: DocumentEvent?) {
                val str = mJPath.text.trim()
                if (!mUserInputPath) {
                    if (str != "/${mJPage.text.trim()}") {
                        mUserInputPath = true
                    }
                } else if (str.isEmpty()) {
                    mUserInputPath = false
                }
            }
        })

        mJPage.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(p0: DocumentEvent?) {
                if (!mUserInputPath) {
                    val str = mJPage.text.trim()
                    mSetPath = true
                    // 通过这种方式，会先触发removeUpdate再触发insertUpdate
                    mJPath.text = if (str.isEmpty()) "" else "/$str"
                }
            }

            override fun removeUpdate(p0: DocumentEvent?) {
                if (!mUserInputPath) {
                    val str = mJPage.text.trim()
                    mSetPath = true
                    mJPath.text = if (str.isEmpty()) "" else "/$str"
                }
            }

            override fun changedUpdate(p0: DocumentEvent?) {
                if (!mUserInputPath) {
                    val str = mJPage.text.trim()
                    mSetPath = true
                    mJPath.text = if (str.isEmpty()) "" else "/$str"
                }
            }
        })

        initReturnParam()
        val builder = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("页面名称: "), mJPage, 1, false)
            .addLabeledComponent(JBLabel("页面路径: "), mJPath, 1, false)
            .addLabeledComponent(JBLabel("路由注释: "), mJDoc, 1, false)
            .addLabeledComponent(JBLabel("返回数据: "), mJReturnPramPanel, 1, false)
            .addComponent(jParamsTitlePanel, 1)
            .addComponentFillVertically(JBScrollPane(mJPramsPanel), 1)
        val panel = builder.panel
        panel.preferredSize = JBUI.size(400, 300)
        return panel
    }

    private fun initReturnParam() {
        val c = GridBagConstraints()
        c.gridwidth = 1
        c.gridx = 0
        c.gridy = 0
        c.weightx = 1.0
        c.weighty = 0.0
        c.fill = GridBagConstraints.HORIZONTAL
        val typeName = JTextField("泛型名称")
        typeName.isVisible = false
        mJReturnPramPanel.add(typeName, c)

        c.gridx = 1
        c.weightx = 0.0
        c.fill = GridBagConstraints.NONE
        val typeBox = ComboBox<String>()
        typeBox.addItem("无返回参数")
        typeBox.addItem("String")
        typeBox.addItem("int")
        typeBox.addItem("double")
        typeBox.addItem("bool")
        typeBox.addItem("List")
        typeBox.addItem("Set")
        typeBox.addItem("Map")
        typeBox.addItem("dynamic")
        typeBox.addItem("自定义")
        mJReturnPramPanel.add(typeBox, c)

        val space = JBLabel()
        c.gridx = 2
        c.weightx = 1.0
        c.fill = GridBagConstraints.HORIZONTAL
        mJReturnPramPanel.add(space, c)

        typeName.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) {
                val text = typeName.text
                if (text == "泛型名称" || text == "数据类型") {
                    typeName.text = ""
                }
            }

            override fun focusLost(e: FocusEvent?) {
                val text = typeName.text.trim()
                if (text.isEmpty()) {
                    val type = typeBox.selectedItem as String
                    if (type == "自定义") {
                        typeName.text = "数据类型"
                    } else {
                        typeName.text = "泛型名称"
                    }
                }
            }
        })

        typeBox.addItemListener {
            val type: String = it.item as String
            if (type == "List" || type == "Set" || type == "自定义") {
                typeName.text = if (type == "自定义") "数据类型" else "泛型名称"
                if (!typeName.isVisible) {
                    typeName.isVisible = true
                    space.isVisible = false
                    mJReturnPramPanel.updateUI()
                }
            } else if (typeName.isVisible) {
                typeName.isVisible = false
                space.isVisible = true
                mJReturnPramPanel.updateUI()
            }
        }
    }

    private fun createParamItem(verticalSpace: JComponent) {
        val panel = JPanel(GridBagLayout())

        val c = GridBagConstraints()
        c.gridwidth = 1
        c.gridx = 0
        c.gridy = 0
        c.weightx = 1.0
        c.weighty = 0.0
        c.fill = GridBagConstraints.HORIZONTAL
        val paramName = JTextField("参数名")
        panel.add(paramName, c)

        val typeName = JTextField("泛型名称")
        typeName.isVisible = false
        c.gridx = 1
        panel.add(typeName, c)

        mJPramsPanel.remove(verticalSpace)
        val gridy = mJPramsPanel.components.size / 4
        val cc = GridBagConstraints()
        cc.gridwidth = 1
        cc.weightx = 1.0
        cc.weighty = 0.0
        cc.gridx = 0
        cc.gridy = gridy
        cc.fill = GridBagConstraints.HORIZONTAL
        mJPramsPanel.add(panel, cc)

        cc.gridx = 1
        cc.weightx = 0.0
        cc.fill = GridBagConstraints.NONE
        val typeBox = ComboBox<String>()
        typeBox.addItem("String")
        typeBox.addItem("int")
        typeBox.addItem("double")
        typeBox.addItem("bool")
        typeBox.addItem("List")
        typeBox.addItem("Set")
        typeBox.addItem("Map")
        typeBox.addItem("dynamic")
        typeBox.addItem("自定义")
        mJPramsPanel.add(typeBox, cc)

        val checkBox = JBCheckBox("null", true)
        cc.gridx = 2
        mJPramsPanel.add(checkBox, cc)

        cc.gridx = 3
        val delete = ImageButton(icon = FlutterDevToolsIcons.delete)
        delete.preferredSize = JBUI.size(16)
        mJPramsPanel.add(delete, cc)

        cc.gridx = 0
        cc.gridy = gridy + 1
        cc.weighty = 1.0
        cc.weightx = 1.0
        cc.fill = GridBagConstraints.BOTH
        mJPramsPanel.add(verticalSpace, cc)

        mJPramsPanel.updateUI()

        paramName.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) {
                val text = paramName.text
                if (text == "参数名") {
                    paramName.text = ""
                }
            }

            override fun focusLost(e: FocusEvent?) {
                val text = paramName.text.trim()
                if (text.isEmpty()) {
                    paramName.text = "参数名"
                }
            }
        })

        typeName.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) {
                val text = typeName.text
                if (text == "泛型名称" || text == "数据类型") {
                    typeName.text = ""
                }
            }

            override fun focusLost(e: FocusEvent?) {
                val text = typeName.text.trim()
                if (text.isEmpty()) {
                    val type = typeBox.selectedItem as String
                    if (type == "自定义") {
                        typeName.text = "数据类型"
                    } else {
                        typeName.text = "泛型名称"
                    }
                }
            }
        })

        delete.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                mJPramsPanel.remove(panel)
                mJPramsPanel.remove(typeBox)
                mJPramsPanel.remove(checkBox)
                mJPramsPanel.remove(delete)
                mJPramsPanel.updateUI()
            }
        })

        typeBox.addItemListener {
            val type: String = it.item as String
            if (type == "List" || type == "Set" || type == "自定义") {
                typeName.text = if (type == "自定义") "数据类型" else "泛型名称"
                if (!typeName.isVisible) {
                    typeName.isVisible = true
                    panel.updateUI()
                }
            } else if (typeName.isVisible) {
                typeName.isVisible = false
                panel.updateUI()
            }
        }
    }

    fun getPagePath(): String {
        return mJPath.text.trim()
    }

    fun getPageName(): String {
        return mJPage.text.trim()
    }

    fun getDoc(): String {
        return mJDoc.text.trim()
    }

    fun getReturnType(): String {
        return (mJReturnPramPanel.components[1] as ComboBox<*>).selectedItem as String
    }

    fun getReturnGenerics(): String {
        val value = (mJReturnPramPanel.components[0] as JTextField).text.trim()
        if (value == "泛型名称" || value == "数据类型") {
            return ""
        }
        return value
    }

    fun getParams(): List<Param> {
        val list = mutableListOf<Param>()
        val components = mJPramsPanel.components

        for (index in 0 until (components.size - 1) / 4) {
            val childComponents = (components[index * 4] as JPanel).components

            val name = (childComponents[0] as JTextField).text.trim()
            if (name.isEmpty() || name == "参数名") {
                continue
            }

            var generics = (childComponents[1] as JTextField).text.trim()
            if (generics == "泛型名称" || generics == "数据类型") {
                generics = ""
            }

            val type = (components[index * 4 + 1] as ComboBox<*>).selectedItem as String
            if (type == "自定义" && generics.isEmpty()) {
                continue
            }

            val couldNull = (components[index * 4 + 2] as JBCheckBox).isSelected
            list.add(Param(name, type, generics, couldNull))
        }

        return list
    }
}

data class Param(val name: String, val type: String, val generics: String, val couldNull: Boolean)
