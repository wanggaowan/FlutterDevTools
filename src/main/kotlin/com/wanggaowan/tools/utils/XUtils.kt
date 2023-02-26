package com.wanggaowan.tools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.wanggaowan.tools.utils.ex.findChild
import com.wanggaowan.tools.utils.ex.rootDir
import kotlinx.html.InputType

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
}
