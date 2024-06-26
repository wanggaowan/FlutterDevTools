package com.wanggaowan.tools.actions.log

import com.ibm.icu.text.SimpleDateFormat
import com.wanggaowan.tools.utils.PropertiesSerializeUtils
import java.util.*

/**
 * 日志存储和读取工具
 *
 * @author Created by wanggaowan on 2024/6/26 下午2:37
 */
object LogUtils {
    private const val DATE_KEY = "log_date_time_key"

    fun save(projectName: String, content: String) {
        if (content.isEmpty()) {
            return
        }

        val key = SimpleDateFormat("yyyy-MM-dd").format(Date())
        val valueList = PropertiesSerializeUtils.getList(key)
        val newValue = "$projectName;;;$content"
        if (!valueList.isNullOrEmpty() && valueList.contains(newValue)) {
            return
        }

        val newValues = mutableListOf<String>()
        if (!valueList.isNullOrEmpty()) {
            newValues.addAll(valueList)
        }
        newValues.add(newValue)
        PropertiesSerializeUtils.putList(key, newValues)
        saveDateKey(key)
    }

    fun getLogKeys(): List<String>? {
        return PropertiesSerializeUtils.getList(DATE_KEY)
    }

    fun getLog(key: String): String {
        val content = PropertiesSerializeUtils.getList(key)
        val map = mutableMapOf<String, String>()
        content?.forEach {
            val index = it.indexOf(";;;")
            val title: String
            val value: String
            if (index != -1) {
                title = it.substring(0, index)
                value = it.substring(index + 3)
            } else {
                title = ""
                value = it
            }

            val oldValue = map[title]
            if (oldValue == null) {
                map[title] = value
            } else {
                map[title] = "$oldValue\n$value"
            }
        }

        val builder = StringBuilder()
        map.keys.forEach {
            builder.append(it)
                .append("\n")
                .append(map[it])
                .append("\n\n\n")
        }
        return builder.toString()
    }

    fun delete(key: String) {
        PropertiesSerializeUtils.putList(key, null)
        val value = PropertiesSerializeUtils.getList(DATE_KEY)
        if (value.isNullOrEmpty()) {
            return
        }
        val newValues = mutableListOf<String>()
        newValues.addAll(value)
        newValues.remove(key)
        PropertiesSerializeUtils.putList(DATE_KEY, newValues)
    }

    fun clear() {
        val value = PropertiesSerializeUtils.getList(DATE_KEY)
        value?.forEach {
            PropertiesSerializeUtils.putList(it, null)
        }
        PropertiesSerializeUtils.putList(DATE_KEY, null)
    }

    private fun saveDateKey(date: String) {
        val value = PropertiesSerializeUtils.getList(DATE_KEY)
        if (!value.isNullOrEmpty() && value.contains(date)) {
            return
        }

        val newValues = mutableListOf<String>()
        if (!value.isNullOrEmpty()) {
            newValues.addAll(value)
        }
        newValues.add(date)
        PropertiesSerializeUtils.putList(DATE_KEY, newValues)
    }
}
