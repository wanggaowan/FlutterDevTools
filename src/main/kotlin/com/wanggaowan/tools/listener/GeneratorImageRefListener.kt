package com.wanggaowan.tools.listener

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.PsiDirectoryImpl
import com.wanggaowan.tools.actions.GeneratorImageRefUtils
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.flutterModules
import com.wanggaowan.tools.utils.ex.rootDir
import io.flutter.pub.PubRoot
import kotlinx.coroutines.*

/**
 * 生成图片资源引用，并在pubspec.yaml中生成图片位置声明监听
 *
 * @author Created by wanggaowan on 2023/3/2 22:34
 */
class GeneratorImageRefListener : BulkFileListener {
    override fun before(events: MutableList<out VFileEvent>) {
        super.before(events)
    }

    override fun after(events: MutableList<out VFileEvent>) {
        super.after(events)
        coroutineScope.value.launch {
            val changeDataList = parseFileEvent(events)
            changeDataList.forEach {
                if (it.imageChangeType == CHANGE) {
                    runJob(it.module, false)
                } else if (it.imageChangeType == CHANGE_EXAMPLE) {
                    runJob(it.module, true)
                }
            }
        }
    }

    /**
     * 执行生成图片引用文件和配置Assets节点命令
     */
    private fun runJob(module: Module, isExample: Boolean) {
        module.basePath?.also { basePath ->
            val path = if (isExample) "$basePath/Example" else basePath
            var job = jobMap[path]
            // 仅仅最后一次图片变更时执行,如果多个项目都有图片变化，则只处理最后一个项目
            if (job?.isActive == true) {
                job.cancel()
            }

            job = coroutineScope.value.launch {
                delay(500)
                GeneratorImageRefUtils.generate(module, isExample)
            }

            jobMap[path] = job
        }
    }

    /**
     * 解析文件事件，返回需要处理的文件数据
     */
    private fun parseFileEvent(events: MutableList<out VFileEvent>): List<ChangeData> {
        val changeData = mutableListOf<ChangeData>()
        for (event in events) {
            val file = event.file ?: continue

            if (event is VFileCopyEvent) {
                val module = getModule(event, event.path) ?: continue
                val imageChange = isImageChange(1, module, file, event.path)
                if (imageChange != 0) {
                    changeData.add(ChangeData(module, imageChange))
                }
                continue
            }

            if (event is VFileCreateEvent || event is VFileDeleteEvent) {
                val module = getModule(event, file.path) ?: continue
                val imageChange = isImageChange(0, module, file, "")
                if (imageChange != 0) {
                    changeData.add(ChangeData(module, imageChange))
                }

                continue
            }

            if (event is VFileMoveEvent) {
                var module = getModule(event, event.oldPath)
                if (module != null) {
                    val imageChange = isImageChange(1, module, file, event.oldPath)
                    if (imageChange != 0) {
                        changeData.add(ChangeData(module, imageChange))
                    }
                }

                module = getModule(event, event.newPath)
                if (module != null) {
                    val imageChange = isImageChange(1, module, file, event.newPath)
                    if (imageChange != 0) {
                        changeData.add(ChangeData(module, imageChange))
                    }
                }

                continue
            }

            if (event is VFilePropertyChangeEvent) {
                if (event.propertyName == "name") {
                    val module = getModule(event, file.path) ?: continue
                    val imageChange = isImageChange(0, module, file, "")
                    if (imageChange != 0) {
                        changeData.add(ChangeData(module, imageChange))
                    }
                }
                continue
            }
        }
        return changeData
    }

    /**
     * 图片是否变更，[type]为0表示通过[file]判断文件变更，[type]为1表示通过[path]判断文件变更。
     * 返回[NO_CHANGE]、[CHANGE]或[CHANGE_EXAMPLE]
     */
    private fun isImageChange(type: Int, module: Module, file: VirtualFile?, path: String): Int {
        val pubRoot = PubRoot.forDirectory(module.rootDir) ?: return NO_CHANGE
        val exampleDir = pubRoot.exampleDir
        val exampleImagesDir =
            if (exampleDir == null) null else "${exampleDir.path}/${PluginSettings.getExampleImagesFileDir(module.project)}"
        val imagesDir = "${pubRoot.path}/${PluginSettings.getImagesFileDir(module.project)}"

        if (type == 0) {
            if (file == null) {
                return NO_CHANGE
            }

            if (exampleImagesDir != null && file.path.startsWith(exampleImagesDir)) {
                if (isFileInDir(exampleImagesDir, file)) {
                    return CHANGE_EXAMPLE
                }

                return NO_CHANGE
            }

            if (isFileInDir(imagesDir, file)) {
                return CHANGE
            }

            return NO_CHANGE
        }

        if (exampleImagesDir != null && path.startsWith(exampleImagesDir)) {
            if (file != null && isImage(file)) {
                return CHANGE_EXAMPLE
            }
            return NO_CHANGE
        }

        if (file != null && isImage(file) && path.startsWith(imagesDir)) {
            return CHANGE
        }

        return NO_CHANGE
    }

    /**
     * 获取文件时间所处的模块
     */
    private fun getModule(event: VFileEvent, filePath: String): Module? {
        val requestor = event.requestor
        if (requestor is PsiManager) {
            return getModuleByProject(requestor.project, filePath)
        }

        if (requestor is PsiDirectoryImpl) {
            return getModuleByProject(requestor.project, filePath)
        }

        if (requestor is Project) {
            return getModuleByProject(requestor, filePath)
        }

        if (requestor is Module) {
            return requestor
        }

        if (filePath.isEmpty()) {
            return null
        }

        for (project in ProjectManager.getInstance().openProjects) {
            val module = getModuleByProject(project, filePath)
            if (module != null) {
                return module
            }
        }

        return null
    }

    /**
     * 根据文件路径获取项目中对应的模块
     */
    private fun getModuleByProject(project: Project, filePath: String): Module? {
        if (filePath.isEmpty()) {
            return null
        }

        val modules = project.flutterModules
        if (modules.isNullOrEmpty()) {
            return null
        }

        for (module in modules) {
            val basePath = module.basePath
            if (basePath != null && filePath.startsWith(basePath)) {
                return module
            }
        }
        return null
    }

    /**
     * 指定文件[file]是否是给定目录[imagesDir]下的文件
     */
    private fun isFileInDir(imagesDir: String, file: VirtualFile?): Boolean {
        if (file == null) {
            return false
        }

        if (!file.isValid) {
            // 说明已经被删除
            return file.path.startsWith(imagesDir)
        }

        if (!file.isDirectory) {
            if (XUtils.isImage(file.name) && file.path.startsWith(imagesDir)) {
                return true
            }
            return false
        }

        file.children.forEach {
            if (isFileInDir(imagesDir, it)) {
                return true
            }
        }
        return false
    }

    /**
     * 只要给的文件或文件夹包含图片，都认为是图片
     */
    private fun isImage(virtualFile: VirtualFile): Boolean {
        if (!virtualFile.isDirectory) {
            return XUtils.isImage(virtualFile.name)
        }

        for (child in virtualFile.children) {
            if (isImage(child)) {
                return true
            }
        }
        return false
    }

    companion object {
        private val coroutineScope = lazy { CoroutineScope(Dispatchers.Default) }
        private val jobMap: MutableMap<String, Job> = mutableMapOf()

        /**
         * 无图片发生变更
         */
        const val NO_CHANGE = 0

        /**
         * Flutter项目图片发生变更
         */
        const val CHANGE = 1

        /**
         * Flutter项目内Example项目图片发生变更
         */
        const val CHANGE_EXAMPLE = 2
    }
}

/**
 * 需要处理的数据
 */
data class ChangeData(
    val module: Module,
    val imageChangeType: Int
)

