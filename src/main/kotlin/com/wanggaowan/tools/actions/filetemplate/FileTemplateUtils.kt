package com.wanggaowan.tools.actions.filetemplate

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wanggaowan.tools.actions.filetemplate.template.Page
import com.wanggaowan.tools.actions.filetemplate.template.SimplePage
import com.wanggaowan.tools.utils.PropertiesSerializeUtils

/**
 * 模版文件工具类
 *
 * @author Created by wanggaowan on 2023/9/5 16:44
 */
object FileTemplateUtils {
    private val initKey = "fileTemplateInit"
    private val templateKey = "template"

    fun initDefaultTemplate() {
        // if (PropertiesSerializeUtils.getBoolean(initKey, false)) {
        //     return
        // }
        //
        // PropertiesSerializeUtils.putBoolean(initKey, true)
        val templateList = arrayListOf<Template>()
        templateList.add(Page.template)
        templateList.add(SimplePage.template)
        val defaultTemplate = Gson().toJson(templateList)
        PropertiesSerializeUtils.putString(templateKey, defaultTemplate)
    }

    fun getTemplateList(): List<Template> {
        val content = PropertiesSerializeUtils.getString(templateKey)
        val list = arrayListOf<Template>()
        if (content.isEmpty()) {
            return list
        }

        try {
            val type = object : TypeToken<List<Template>>() {}.type
            val value: List<Template>? = Gson().fromJson(content, type)
            if (value != null) {
                list.addAll(value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveTemplateList(list: List<Template>) {
        if (list.isEmpty()) {
            PropertiesSerializeUtils.putString(templateKey, "")
            return
        }
        val defaultTemplate = Gson().toJson(list)
        PropertiesSerializeUtils.putString(templateKey, defaultTemplate)
    }
}
