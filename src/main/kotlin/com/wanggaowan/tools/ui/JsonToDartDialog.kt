package com.wanggaowan.tools.ui

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import com.wanggaowan.tools.utils.PropertiesSerializeUtils
import com.wanggaowan.tools.utils.msg.Toast
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import javax.swing.*

/**
 * json文件转JAVA对象
 */
class JsonToDartDialog(
    val project: Project,
    private val className: String?,
) : DialogWrapper(project, false) {

    private lateinit var mRootPanel: JPanel
    private lateinit var mHeadRootPanel: JPanel
    private val mCreateObjectName: ExtensionTextField = ExtensionTextField("", placeHolder = "请输入类名称")
    private val mObjSuffix: ExtensionTextField = ExtensionTextField("", placeHolder = "类名后缀")
    private lateinit var mJPEtRoot: JPanel
    private val mEtJsonContent: EditorTextField = JsonLanguageTextField(project)
    private lateinit var mCbCreateDoc: JCheckBox
    private lateinit var mCbGeneratorJsonSerializable: JCheckBox
    private lateinit var mCbGeneratorGFile: JCheckBox
    private lateinit var mCbNullSafe: JCheckBox
    private lateinit var mCbSetConverters: JCheckBox
    private lateinit var mBottomRootPanel2: JPanel
    private val mConvertersValue: ExtensionTextField = ExtensionTextField()

    private var mJsonValue: JsonObject? = null

    init {
        val cc = GridBagConstraints()
        cc.fill = GridBagConstraints.HORIZONTAL
        cc.weightx = 0.0
        cc.gridx = 0

        val label = JLabel("类名称")
        label.border = BorderFactory.createEmptyBorder(0, 0, 0, 6)
        mHeadRootPanel.add(label, cc)

        cc.weightx = 10.0
        cc.gridx = 1
        mCreateObjectName.minimumSize = Dimension(100, 30)
        mHeadRootPanel.add(mCreateObjectName, cc)

        cc.weightx = 1.0
        cc.gridx = 2
        mObjSuffix.text = "Entity"
        mObjSuffix.border = BorderFactory.createEmptyBorder(0, 4, 0, 0)
        mObjSuffix.minimumSize = Dimension(100, 30)
        mHeadRootPanel.add(mObjSuffix, cc)

        mEtJsonContent.isEnabled = true
        mJPEtRoot.add(mEtJsonContent, BorderLayout.CENTER)

        mConvertersValue.preferredSize = Dimension(220, 38)
        mBottomRootPanel2.add(mConvertersValue)
        initEvent()
        initData()
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return if (className == null) {
            mCreateObjectName
        } else {
            mEtJsonContent
        }
    }

    override fun createCenterPanel(): JComponent = mRootPanel

    private fun initEvent() {

    }

    private fun initData() {
        mConvertersValue.placeHolder = CONVERTERS_HINT
        mConvertersValue.font = UIUtil.getFont(UIUtil.FontSize.SMALL, mConvertersValue.font)
        val value = PropertiesSerializeUtils.getString(project, CONVERTERS_VALUE)
        mConvertersValue.text = value

        mCbSetConverters.isSelected = PropertiesSerializeUtils.getBoolean(project, SET_CONVERTERS, true)

        if (className == null) {
            mCreateObjectName.isEnabled = true
            return
        }

        mCreateObjectName.isEnabled = false
        mCreateObjectName.text = className
    }

    override fun doOKAction() {
        val objName = getClassName()
        if (objName.isEmpty()) {
            Toast.show(mCreateObjectName, MessageType.ERROR, "请输入要创建的对象名称")
            return
        }

        val jsonStr = mEtJsonContent.text.trim()
        if (jsonStr.isEmpty()) {
            Toast.show(mEtJsonContent, MessageType.ERROR, "请输入JSON内容")
            return
        }

        if (mCbSetConverters.isSelected) {
            val text = getConvertersValue()
            if (text.isEmpty()) {
                Toast.show(mConvertersValue, MessageType.ERROR, CONVERTERS_HINT)
                return
            }
        }

        try {
            mJsonValue = Gson().fromJson(jsonStr, JsonObject::class.java)
        } catch (e: Exception) {
            Toast.show(mEtJsonContent, MessageType.ERROR, "JSON数据格式不正确")
            return
        }

        super.doOKAction()
    }

    /**
     * 是否生成类序列化方法
     */
    fun isGeneratorJsonSerializable(): Boolean {
        return mCbGeneratorJsonSerializable.isSelected
    }

    /**
     * 是否生成类对应的.g.dart文件
     */
    fun isGeneratorGFile(): Boolean {
        return mCbGeneratorGFile.isSelected
    }

    /**
     * 是否生成注释
     */
    fun isGeneratorDoc(): Boolean {
        return mCbCreateDoc.isSelected
    }

    /**
     * 是否生成空安全类型
     */
    fun isNullSafe(): Boolean {
        return mCbNullSafe.isSelected
    }

    /**
     * 是否配置序列号的converters字段
     */
    fun isSetConverters(): Boolean {
        return mCbSetConverters.isSelected
    }

    /**
     * 是否生成反序列号列表方法
     */
    fun isCreateFromList(): Boolean {
        // return mCbCreateFromList.isSelected

        // 取消单独勾选创建序列化列表方法，只要序列化，则生成序列化单个方法和列表方法
        return mCbGeneratorJsonSerializable.isSelected
    }

    /**
     * 仅在点击OK成功后获取，否则将抛出异常
     */
    fun getJsonValue(): JsonObject {
        return mJsonValue!!
    }

    fun getClassName(): String {
        return mCreateObjectName.text.trim()
    }

    fun getSuffix(): String {
        return mObjSuffix.text.trim()
    }

    fun getConvertersValue(): String {
        return mConvertersValue.text.trim()
    }

    companion object {
        const val CONVERTERS_HINT = "请输入converters值"
        const val CONVERTERS_VALUE = "converters_value"
        const val SET_CONVERTERS = "set_converters"
    }
}
