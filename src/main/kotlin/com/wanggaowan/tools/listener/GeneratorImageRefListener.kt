package com.wanggaowan.tools.listener

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
            val pair = isImageChange(events)
            if (pair != null) {
                if (pair.second == 1) {
                    // 仅仅最后一次图片变更时执行,如果多个项目都有图片变化，则只处理最后一个项目
                    // 这里不区分多项目，一般情况下只会有一个项目文件在变化
                    if (job?.isActive == true) {
                        job?.cancel()
                    }

                    job = coroutineScope.value.launch {
                        delay(1000)
                        GeneratorImageRefUtils.generate(pair.first)
                    }
                }

                if (pair.second == 2) {
                    // 仅仅最后一次图片变更时执行,如果多个项目都有图片变化，则只处理最后一个项目
                    // 这里不区分多项目，一般情况下只会有一个项目文件在变化
                    if (jobForExample?.isActive == true) {
                        jobForExample?.cancel()
                    }

                    jobForExample = coroutineScope.value.launch {
                        delay(1000)
                        GeneratorImageRefUtils.generate(pair.first, true)
                    }
                }
            }
        }
    }

    // 只要文件变动事件有一个图片更改的事件，就认为图片有变动
    private fun isImageChange(events: MutableList<out VFileEvent>): Pair<Project, Int>? {
        for (event in events) {
            val file = event.file ?: continue

            if (event is VFileCopyEvent) {
                val project = getProject(event) ?: continue
                val imageChange = isImageChange(1, project, file, event.path)
                if (imageChange != 0) {
                    return Pair(project, imageChange)
                }
                continue
            }

            if (event is VFileCreateEvent || event is VFileDeleteEvent) {
                val project = getProject(event) ?: continue
                val imageChange = isImageChange(0, project, event.file, "")
                if (imageChange != 0) {
                    return Pair(project, imageChange)
                }

                continue
            }

            if (event is VFileMoveEvent) {
                val project = getProject(event) ?: continue
                val imageChange = isImageChange(1, project, file, event.newPath)
                if (imageChange != 0) {
                    return Pair(project, imageChange)
                }

                continue
            }

            if (event is VFilePropertyChangeEvent) {
                val project = getProject(event) ?: continue
                if (event.propertyName == "name") {
                    val imageChange = isImageChange(0, project, file, "")
                    if (imageChange != 0) {
                        return Pair(project, imageChange)
                    }
                }
                continue
            }
        }

        return null
    }

    /**
     * 图片是否变更，[type]为0表示通过[file]判断文件变更，[type]为1表示通过[path]判断文件变更。
     * 返回0表示没有变更，1表示根项目文件图片变更，2表示example项目文件图片变更
     */
    private fun isImageChange(type: Int, project: Project, file: VirtualFile?, path: String): Int {
        val pubRoot = PubRoot.forDirectory(project.rootDir) ?: return 0
        val exampleDir = pubRoot.exampleDir
        val exampleImagesDir =
            if (exampleDir == null) null else "${exampleDir.path}/${PluginSettings.getExampleImagesFileDir(project)}"
        val imagesDir = "${pubRoot.path}/${PluginSettings.getImagesFileDir(project)}"

        if (type == 0) {
            if (file == null) {
                return 0
            }

            if (exampleImagesDir != null && file.path.startsWith(exampleImagesDir)) {
                if (isImageChange(exampleImagesDir, file)) {
                    return 2
                }

                return 0
            }

            if (isImageChange(imagesDir, file)) {
                return 1
            }

            return 0
        }

        if (exampleImagesDir != null && path.startsWith(exampleImagesDir)) {
            if (file != null && isImage(file)) {
                return 2
            }
            return 0
        }

        if (file != null && isImage(file) && path.startsWith(imagesDir)) {
            return 1
        }

        return 0
    }

    private fun getProject(event: VFileEvent): Project? {
        val requestor = event.requestor
        if (requestor is PsiManager) {
            return requestor.project
        }

        if (requestor is PsiDirectoryImpl) {
            return requestor.project
        }

        val paths = mutableListOf<String>()
        when (event) {
            is VFileMoveEvent -> {
                paths.add(event.oldPath)
                paths.add(event.newPath)
            }

            else -> {
                paths.add(event.path)
            }
        }

        if (paths.isEmpty()) {
            return null
        }

        for (path in paths) {
            for (project in ProjectManager.getInstance().openProjects) {
                val basePath = project.basePath
                if (basePath != null && path.startsWith(basePath)) {
                    return project
                }
            }
        }

        return null
    }

    // 只要给的文件或文件夹有一个文件发生变动就任务图片有变动
    private fun isImageChange(imagesDir: String, file: VirtualFile?): Boolean {
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
            if (isImageChange(imagesDir, it)) {
                return true
            }
        }
        return false
    }

    // 只要给的文件或文件夹包含图片，都认为是图片
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
        private var job: Job? = null
        private var jobForExample: Job? = null
    }
}
