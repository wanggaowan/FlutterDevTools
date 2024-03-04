package com.wanggaowan.tools.utils

import com.intellij.openapi.project.Project
import com.wanggaowan.tools.utils.ex.findChild

/**
 * 提供通用工具方法
 *
 * @author Created by wanggaowan on 2023/2/3 09:48
 */
object XUtils {
    /**
     * 检测项目是否有pubspec.lock文件
     */
    fun havePubspecLockFile(project: Project): Boolean {
        return project.findChild("pubspec.lock") != null
    }

    /**
     * 判断给的名称是否是图片格式
     */
    fun isImage(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith("png")
            || lower.endsWith("jpg")
            || lower.endsWith("jpeg")
            || lower.endsWith("webp")
            || lower.endsWith("gif")
            || lower.endsWith("svg")
    }

    /**
     * 判断是否是图片分辨率变体目录
     */
    fun isImageVariantsFolder(name: String?): Boolean {
        return name == "1.5x" || name == "2.0x" || name == "3.0x" || name == "4.0x"
    }

    /**
     * 将图片路径数据转化为符合Dart规则的Key
     */
    fun imagePathToDartKey(name: String): String {
        return StringUtils.lowerCamelCase(
            name.substring(0, name.lastIndexOf("."))
                .replace("/", "_")
                .replace("-", "_")
                .replace("@", ""), false
        )
    }
}
