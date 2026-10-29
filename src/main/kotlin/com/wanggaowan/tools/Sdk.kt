package com.wanggaowan.tools

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.wanggaowan.tools.utils.ex.rootDir
import io.flutter.pub.PubRoot

/**
 * 解析Flutter项目
 *
 * @author Created by wanggaowan on 2026/10/28 11:33
 */
@Service(Service.Level.PROJECT)
class Sdk(val project: Project) {
    private val _projectPubRoot: PubRoot? = PubRoot.forFile(project.projectFile)
    private val _pubRoots: MutableSet<PubRoot> = mutableSetOf()

    init {
        addModule(project.modules.toList())
        project.messageBus.connect().subscribe(ModuleListener.TOPIC, object : ModuleListener {
            override fun modulesAdded(project: Project, modules: List<Module>) {
                // 此监听主要处理Android项目嵌套Flutter，非纯Flutter项目情况，
                // 此时可能项目打开时Flutter Module还未初始化
                addModule(modules)
            }
        })
    }

    private fun addModule(modules: List<Module>) {
        for (module in modules) {
            val pubRoot = PubRoot.forDirectory(module.rootDir)
            if (pubRoot != null) {
                this._pubRoots.add(pubRoot)
                for (file in pubRoot.root.children) {
                    if (file.isDirectory) {
                        PubRoot.forDirectory(file)?.let {
                            this._pubRoots.add(it)
                        }
                    }
                }
            }
        }
    }

    val isFlutterProject get() = _projectPubRoot != null

    val pubRoots: Set<PubRoot> get() = this._pubRoots

    fun getPubRoot(file: VirtualFile): PubRoot? {
        return getPubRoot(file.path)
    }

    fun getPubRoot(psiFile: PsiFile): PubRoot? {
        val file = psiFile.virtualFile ?: return null
        return getPubRoot(file.path)
    }

    fun getPubRoot(filePath: String): PubRoot? {
        // _projectPubRoot路径最短，因此先忽略，之后未找到再单独匹配，要匹配最长路径。除了_projectPubRoot外，
        // 其余pubRoot都是在_projectPubRoot路径之下，不处理除_projectPubRoot之外的PubRoot路径之下嵌套PubRoot
        // 的情况
        val find = this.pubRoots.find { it.root != _projectPubRoot?.root && filePath.startsWith(it.root.path) }
        if (find != null) {
            return find
        }

        if (_projectPubRoot != null && filePath.startsWith(_projectPubRoot.path)) {
            return _projectPubRoot
        }

        return null
    }

    companion object {
        fun getInstance(project: Project): Sdk {
            return project.getService(Sdk::class.java)
        }
    }
}

