package com.wanggaowan.tools.utils.ex

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.wanggaowan.tools.utils.NotificationUtils
import io.flutter.pub.PubRoot
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/*
 * Project扩展
 */

// <editor-fold desc="project">

fun Project?.getModules(): Array<Module>? {
    if (this == null) {
        return null
    }
    return ModuleManager.getInstance(this).modules
}


/**
 * 是否是Flutter项目
 */
val Project?.isFlutterProject: Boolean
    get() {
        if (this == null) {
            return false
        }

        val isFP = isFlutterProjectInner(this.basePath)
        if (isFP) {
            return true
        }

        val modules = getModules()
        if (modules.isNullOrEmpty()) {
            NotificationUtils.showBalloonMsg(this, "无Flutter模块", NotificationType.ERROR)
            return false
        }

        for (module in modules) {
            if (module.isFlutterProject) {
                return true
            }
        }
        return false
    }

fun isFlutterProjectInner(basePath: String?): Boolean {
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
/**
 * 是否是Flutter项目
 */
val Module?.isFlutterProject: Boolean
    get() {
        if (this == null) {
            return false
        }

        val files = ModuleRootManager.getInstance(this).contentRoots
        if (files.isEmpty()) {
            return false
        }
        return isFlutterProjectInner(files[0].path)
    }

val Module?.rootDir: VirtualFile?
    get() {
        if (this == null) {
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


// <editor-fold desc="VirtualFile">

/**
 * 是否输入Flutter项目文件
 */
val VirtualFile?.isFlutterProject: Boolean
    get() = this != null && isFlutterProjectInner(path)

/**
 * 通过文件获取所属的模块
 */
val VirtualFile.module: Module?
    get() {
        var inModule: Module? = null
        var inModuleBasePath: String? = null
        for (project in ProjectManager.getInstance().openProjects) {
            val modules = project.getModules()
            if (modules.isNullOrEmpty()) {
                continue
            }

            for (module in modules) {
                val basePath = module.basePath
                if (basePath != null && path.startsWith(basePath)) {
                    // 此处不能找到第一个就返回，因为可能path路径上存在多个module，
                    // 需要匹配最长路径的module
                    if (inModuleBasePath == null) {
                        inModule = module
                        inModuleBasePath = basePath
                    } else if (inModuleBasePath.length < basePath.length) {
                        inModule = module
                        inModuleBasePath = basePath
                    }
                }
            }
        }
        return inModule
    }

// </editor-fold>

// <editor-fold desc="AnActionEvent">

val AnActionEvent?.isFlutterProject: Boolean
    get() {
        if (this == null) {
            return false
        }

        val module = getData(LangDataKeys.MODULE)
        if (module.isFlutterProject) {
            return true
        }

        return project.isFlutterProject
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

fun PsiElement?.findModule(): Module? {
    if (this == null) {
        return null
    }

    val parent = getParentOfType<PsiFile>(strict = true) ?: return null
    return parent.virtualFile?.module
}

