package com.wanggaowan.tools.utils.ex

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.wanggaowan.tools.Sdk
import io.flutter.pub.PubRoot

/*
 * Project扩展
 */

// <editor-fold desc="project">

fun Project?.getModules(): Array<Module>? {
    if (this == null || this.isDisposed) {
        return null
    }
    return ModuleManager.getInstance(this).modules
}

private val flutterProjectMap: MutableMap<Project, Boolean> = mutableMapOf()

/**
 * 是否是Flutter项目
 */
val Project?.isFlutterProject: Boolean
    get() {
        if (this == null || this.isDisposed) {
            return false
        }

        val isFlutter = flutterProjectMap[this]
        if (isFlutter != null) {
            return isFlutter
        }

        val isFP = isFlutterProjectInner(this.basePath)
        if (isFP) {
            flutterProjectMap[this] = true
            return true
        }

        val modules = getModules()
        if (modules.isNullOrEmpty()) {
            flutterProjectMap[this] = false
            return false
        }

        for (module in modules) {
            if (module.isFlutterProject) {
                flutterProjectMap[this] = true
                return true
            }
        }

        flutterProjectMap[this] = false
        return false
    }

private fun isFlutterProjectInner(basePath: String?): Boolean {
    if (basePath.isNullOrEmpty()) {
        return false
    }

    val file = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/pubspec.yaml")
    return file != null
}

/**
 * [Project]根目录
 */
val Project.rootDir: VirtualFile?
    get() = VirtualFileManager.getInstance().findFileByUrl("file://${this.basePath}")

/**
 * 获取项目根目录下的指定[name]文件
 */
fun Project.findChild(name: String) = rootDir?.findChild(name)

val Project.flutterModules: List<Module>?
    get() {
        val modules = getModules()
        if (modules.isNullOrEmpty()) {
            return null
        }
        val list = mutableListOf<Module>()
        for (module in modules) {
            if (module.isFlutterProject) {
                list.add(module)
            }
        }
        return list
    }

// </editor-fold>


// <editor-fold desc="Module">

private val flutterModuleMap: MutableMap<Module, Boolean> = mutableMapOf()

/**
 * 是否是Flutter项目
 */
val Module?.isFlutterProject: Boolean
    get() {
        if (this == null || this.isDisposed) {
            return false
        }

        var isFlutter = flutterModuleMap[this]
        if (isFlutter != null) {
            return isFlutter
        }

        val files = ModuleRootManager.getInstance(this).contentRoots
        if (files.isEmpty()) {
            flutterModuleMap[this] = false
            return false
        }

        isFlutter = isFlutterProjectInner(files[0].path)
        flutterModuleMap[this] = isFlutter
        return isFlutter
    }

val Module?.rootDir: VirtualFile?
    get() {
        if (this == null || this.isDisposed) {
            return null
        }

        val files = ModuleRootManager.getInstance(this).contentRoots
        return if (files.isEmpty()) null else files[0]
    }

val Module?.basePath: String?
    get() = rootDir?.path

/**
 * 获取模块根目录下的指定[name]文件
 */
fun Module.findChild(name: String) = rootDir?.findChild(name)
// </editor-fold>


// <editor-fold desc="AnActionEvent">

val AnActionEvent?.isFlutterProject: Boolean
    get() {
        if (this == null) {
            return false
        }

        val module = getData(LangDataKeys.MODULE)
        return module.isFlutterProject
    }

// </editor-fold>


// <editor-fold desc="PubRoot">

/**
 * 指定名称的依赖是否存在
 */
fun PubRoot.haveDependencies(name: String): Boolean {
    val packagesMap = this.packagesMap ?: return false
    return packagesMap[name] != null
}

// </editor-fold>

fun PsiElement?.findPubRoot(): PubRoot? {
    if (this == null) {
        return null
    }

    val file = containingFile ?: return null
    return Sdk.getInstance(this.project).getPubRoot(file)
}

