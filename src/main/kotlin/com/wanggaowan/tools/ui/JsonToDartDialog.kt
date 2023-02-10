package com.wanggaowan.tools.ui

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.ui.EditorTextField
import com.wanggaowan.tools.utils.msg.Toast
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * json文件转JAVA对象
 */
class JsonToDartDialog(
    project: Project,
    private val className: String?,
) : DialogWrapper(project, false) {

    private lateinit var mRootPanel: JPanel
    private lateinit var mCreateObjectName: JTextField
    private lateinit var mJPEtRoot: JPanel
    private val mEtJsonContent: EditorTextField = JsonLanguageTextField(project)
    private lateinit var mCbCreateDoc: JCheckBox
    private lateinit var mCbGeneratorJsonSerializable: JCheckBox
    private lateinit var mCbNullSafe: JCheckBox
    private lateinit var mObjSuffix: JTextField

    private var mJsonValue: JsonObject? = null

    init {
        mEtJsonContent.isEnabled = true
        mJPEtRoot.add(mEtJsonContent, BorderLayout.CENTER)
        initData()
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return if (className == null) {
            mCreateObjectName
        } else {
            mEtJsonContent
        }
    }

    override fun createCenterPanel(): JComponent = mRootPanel

    private fun initData() {
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

        try {
            mJsonValue = Gson().fromJson(jsonStr, JsonObject::class.java)
        } catch (e: Exception) {
            Toast.show(mEtJsonContent, MessageType.ERROR, "JSON数据格式不正确")
            return
        }

        super.doOKAction()
    }

    fun isGeneratorJsonSerializable(): Boolean {
        return mCbGeneratorJsonSerializable.isSelected
    }

    fun isGeneratorDoc(): Boolean {
        return mCbCreateDoc.isSelected
    }

    fun isNullSafe(): Boolean {
        return mCbNullSafe.isSelected
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
}
