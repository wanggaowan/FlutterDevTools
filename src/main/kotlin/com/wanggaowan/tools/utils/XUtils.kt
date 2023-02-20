package com.wanggaowan.tools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * 提供通用工具方法
 *
 * @author Created by wanggaowan on 2023/2/3 09:48
 */
object XUtils {
    fun isFlutterProject(project: Project): Boolean {
        // 存在pubspec.yaml文件就认为是Flutter项目
        return findFileInRootDir(project, "pubspec.yaml") != null
    }

    /**
     * 检测项目是否有pubspec.lock文件
     */
    fun havePubspecLockFile(project: Project): Boolean {
        return findFileInRootDir(project, "pubspec.lock") != null
    }

    /**
     * 检测项目根目录下是否有[fileName]指定文件
     */
    fun findFileInRootDir(project: Project, fileName: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val file = VirtualFileManager.getInstance().findFileByUrl("file://$basePath") ?: return null
        return file.findChild(fileName)
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
