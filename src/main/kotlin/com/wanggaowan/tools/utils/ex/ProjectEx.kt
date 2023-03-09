package com.wanggaowan.tools.utils.ex

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager

/*
 * Project类扩展
 */

/**
 * [Project]根目录
 */
val Project.rootDir: VirtualFile?
    get() = VirtualFileManager.getInstance().findFileByUrl("file://${this.basePath}")

/**
 * 获取项目根目录下的指定[name]文件
 */
fun Project.findChild(name: String) = rootDir?.findChild(name)


internal var isFlutterProjectMap: MutableMap<Project, Boolean> = mutableMapOf()
private fun Project.isFlutterProjectInner(): Boolean {
    val isFlutterProject = isFlutterProjectMap[this]
    if (isFlutterProject != null) {
        return isFlutterProject
    }

    val isF = findChild("pubspec.yaml") != null
    isFlutterProjectMap[this] = isF
    return isF
}

/**
 * 是否是Flutter项目
 */
val Project.isFlutterProject
    get() = isFlutterProjectInner()

// 通过文件获取所属的项目
val VirtualFile.project: Project?
    get() {
        for (project in ProjectManager.getInstance().openProjects) {
            val basePath = project.basePath
            if (basePath != null && path.startsWith(basePath)) {
                return project
            }
        }
        return null
    }
