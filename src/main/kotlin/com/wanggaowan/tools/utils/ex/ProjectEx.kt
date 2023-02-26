package com.wanggaowan.tools.utils.ex

import com.intellij.openapi.project.Project
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


private var mFlutterProject: Boolean = false
private fun Project.isFlutterProjectInner():Boolean {
    if (mFlutterProject) {
        return true
    }
    mFlutterProject = findChild("pubspec.yaml") != null
    return mFlutterProject
}

/**
 * 是否是Flutter项目
 */
val Project.isFlutterProject
    get() = isFlutterProjectInner()
