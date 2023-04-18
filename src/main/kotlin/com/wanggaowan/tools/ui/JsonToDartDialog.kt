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
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * json文件转JAVA对象
 */
class JsonToDartDialog(
    val project: Project,
    private val className: String?,
) : DialogWrapper(project, false) {

    private lateinit var mRootPanel: JPanel
    private lateinit var mCreateObjectName: JTextField
    private lateinit var mJPEtRoot: JPanel
    private val mEtJsonContent: EditorTextField = JsonLanguageTextField(project)
    private lateinit var mCbCreateDoc: JCheckBox
    private lateinit var mCbGeneratorJsonSerializable: JCheckBox
    private lateinit var mCbGeneratorGFile: JCheckBox
    private lateinit var mCbNullSafe: JCheckBox
    private lateinit var mObjSuffix: JTextField
    private lateinit var mCbCreateFromList: JCheckBox
    private lateinit var mCbSetConverters: JCheckBox
    private lateinit var mConvertersValue: JTextField

    private var mJsonValue: JsonObject? = null

    init {
        mEtJsonContent.isEnabled = true
        mJPEtRoot.add(mEtJsonContent, BorderLayout.CENTER)
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
        mConvertersValue.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) {
                val text = mConvertersValue.text
                if (text == CONVERTERS_HINT) {
                    mConvertersValue.text = ""
                }
            }

            override fun focusLost(e: FocusEvent?) {
                val text = mConvertersValue.text.trim()
                if (text.isEmpty()) {
                    mConvertersValue.text = CONVERTERS_HINT
                }
            }
        })
    }

    private fun initData() {
        mConvertersValue.font = UIUtil.getFont(UIUtil.FontSize.SMALL, mConvertersValue.font)
        val value = PropertiesSerializeUtils.getString(project, CONVERTERS_VALUE)
        if (value.isNotEmpty()) {
            mConvertersValue.text = PropertiesSerializeUtils.getString(project, CONVERTERS_VALUE)
        }

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
        return mCbCreateFromList.isSelected
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
        val value = mConvertersValue.text.trim()
        if (value == CONVERTERS_HINT) {
            return ""
        }
        return value
    }

    companion object {
        const val CONVERTERS_HINT = "请输入converters值"
        const val CONVERTERS_VALUE = "converters_value"
        const val SET_CONVERTERS = "set_converters"
    }
}
