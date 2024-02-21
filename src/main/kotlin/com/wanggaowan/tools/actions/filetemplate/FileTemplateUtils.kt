package com.wanggaowan.tools.actions.filetemplate

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jetbrains.lang.dart.DartFileType
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
    private const val TEMPLATE_VERSION = 1

    // 是否执行老数据的转化逻辑
    private const val MAP_OLD_DATA_NAME = "isMapOldDataName"

    fun initDefaultTemplate() {
        mapOldData()

        val version = PropertiesSerializeUtils.getInt(TEMPLATE_VERSION_KEY, 0)
        val templateList = getTemplateList()
        var haveInsert = false
        if (version <= 0) {
            // 版本1新增的内容
            haveInsert = true
            templateList.add(Page.template)
            templateList.add(SimplePage.template)
            templateList.add(FormPage.template)
            templateList.add(SimpleFormPage.template)
            templateList.add(ViewPagerPage.template)
            templateList.add(SimpleViewPagerPage.template)
        }

        if (version <= 1) {
            // 版本2新增的内容
            // TODO: 预留，用于以后理解插件升级后数据更新逻辑
            haveInsert = true
        }

        PropertiesSerializeUtils.putInt(TEMPLATE_VERSION_KEY, TEMPLATE_VERSION)
        if (haveInsert) {
            val defaultTemplate = Gson().toJson(templateList)
            PropertiesSerializeUtils.putString(TEMPLATE_DATA_KEY, defaultTemplate)
        }
    }

    private fun mapOldData() {
        val isMapOldData = PropertiesSerializeUtils.getBoolean(MAP_OLD_DATA_NAME, false)
        if (isMapOldData) {
            return
        }
        PropertiesSerializeUtils.putBoolean(MAP_OLD_DATA_NAME, true)
        val list = getTemplateList()
        if (list.isEmpty()) {
            return
        }

        var change = false
        list.forEach {
            change = mapOldDataName(it.children) || change
        }

        if (change) {
            saveTemplateList(list)
        }
    }

    private fun mapOldDataName(list: List<TemplateChildEntity>?): Boolean {
        if (list.isNullOrEmpty()) {
            return false
        }

        var change = false
        list.forEach {
            if (it.isFolder) {
                change = mapOldDataName(it.children) || change
            } else {
                var name = it.name
                if (!name.isNullOrEmpty()) {
                    val index = name.lastIndexOf(".")
                    if (index == -1) {
                        name += ".${DartFileType.INSTANCE.defaultExtension}"
                        it.name = name
                        change = true
                    }
                }
            }
        }
        return change
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
