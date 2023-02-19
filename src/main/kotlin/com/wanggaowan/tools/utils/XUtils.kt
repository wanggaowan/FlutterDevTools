package com.wanggaowan.tools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * 提供通用工具方法
 *
 * @author Created by wanggaowan on 2023/2/3 09:48
 */
object XUtils {
    fun isFlutterProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val file = VirtualFileManager.getInstance().findFileByUrl("file://$basePath") ?: return false
        var isFlutterProject = false
        for (child in file.children) {
            if (child.name == "pubspec.yaml") {
                // 存在pubspec.yaml文件就认为是Flutter项目
                isFlutterProject = true
                break
            }
        }
        return isFlutterProject
    }

    /**
     * 检测项目是否有pubspec.lock文件
     */
    fun havePubspecLockFile(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val file = VirtualFileManager.getInstance().findFileByUrl("file://$basePath") ?: return false
        var have = false
        for (child in file.children) {
            if (child.name == "pubspec.lock") {
                have = true
                break
            }
        }
        return have
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
