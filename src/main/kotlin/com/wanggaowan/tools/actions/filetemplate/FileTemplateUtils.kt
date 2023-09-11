package com.wanggaowan.tools.actions.filetemplate

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wanggaowan.tools.actions.filetemplate.template.*
import com.wanggaowan.tools.utils.PropertiesSerializeUtils

/**
 * 模版文件工具类
 *
 * @author Created by wanggaowan on 2023/9/5 16:44
 */
object FileTemplateUtils {
    private const val TEMPLATE_DATA_KEY = "template"
    private const val TEMPLATE_VERSION_KEY = "templateVersion"
    private const val TEMPLATE_VERSION = 2

    fun initDefaultTemplate() {
        if (PropertiesSerializeUtils.getInt(TEMPLATE_VERSION_KEY) == TEMPLATE_VERSION) {
            return
        }

        PropertiesSerializeUtils.putInt(TEMPLATE_VERSION_KEY, TEMPLATE_VERSION)
        val templateList = arrayListOf<TemplateEntity>()
        templateList.add(Page.template)
        templateList.add(SimplePage.template)
        templateList.add(FormPage.template)
        templateList.add(SimpleFormPage.template)
        templateList.add(ViewPagerPage.template)
        templateList.add(SimpleViewPagerPage.template)
        val defaultTemplate = Gson().toJson(templateList)
        PropertiesSerializeUtils.putString(TEMPLATE_DATA_KEY, defaultTemplate)
    }

    fun getTemplateList(): MutableList<TemplateEntity> {
        val content = PropertiesSerializeUtils.getString(TEMPLATE_DATA_KEY)
        val list = mutableListOf<TemplateEntity>()
        if (content.isEmpty()) {
            return list
        }

        try {
            val type = object : TypeToken<List<TemplateEntity>>() {}.type
            val value: List<TemplateEntity>? = Gson().fromJson(content, type)
            if (value != null) {
                list.addAll(value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveTemplateList(list: List<TemplateEntity>) {
        if (list.isEmpty()) {
            PropertiesSerializeUtils.putString(TEMPLATE_DATA_KEY, "")
            return
        }
        val defaultTemplate = Gson().toJson(list)
        PropertiesSerializeUtils.putString(TEMPLATE_DATA_KEY, defaultTemplate)
    }

    /**
     * 匹配文本中的所有占位符
     */
    fun getPlaceHolders(content: String): Set<String> {
        val set = mutableSetOf<String>()
        if (content.isEmpty()) {
            return set
        }

        /// 匹配已$开始，中间任意[A-Za-z0-9_]组合字符，$结束的任意字符
        val regex = Regex("\\\$[A-Za-z0-9_]*\\\$")
        val result = regex.findAll(content)
        result.forEach { set.add(it.value) }
        return set
    }
}
