package com.wanggaowan.tools.listener

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.wanggaowan.tools.actions.GeneratorImageRefUtils
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
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
        val project: Project = ProjectManagerListenerImpl.project ?: return
        if (!project.isFlutterProject) {
            return
        }

        if (isImageChange(project, events)) {
            // 仅仅最后一次图片变更时执行
            if (job?.isActive == true) {
                job?.cancel()
            }

            job = coroutineScope.value.launch {
                delay(1000)
                GeneratorImageRefUtils.generate(project)
            }
        }
    }

    // 只要文件变动事件有一个图片更改的事件，就认为图片有变动
    private fun isImageChange(project: Project, events: MutableList<out VFileEvent>): Boolean {
        val imagesDir = "${project.basePath}/${PluginSettings.imagesFileDir}"
        for (event in events) {
            val file = event.file ?: continue
            if (event is VFileCreateEvent
                || event is VFileDeleteEvent) {
                if (isImageChange(imagesDir, file)) {
                    return true
                }
                continue
            }

            if (event is VFileMoveEvent) {
                if (isImage(file) && event.newPath.contains(imagesDir)) {
                    return true
                }
                continue
            }

            if (event is VFilePropertyChangeEvent) {
                if (event.propertyName == "name" && isImageChange(imagesDir, file)) {
                    return true
                }
                continue
            }

            if (event is VFileCopyEvent) {
                if (isImage(event.file) && event.path.contains(imagesDir)) {
                    return true
                }
                continue
            }
        }

        return false
    }

    // 只要给的文件或文件夹有一个文件发生变动就任务图片有变动
    private fun isImageChange(imagesDir: String, file: VirtualFile?): Boolean {
        if (file == null) {
            return false
        }

        if (!file.isValid) {
            // 说明已经被删除
            return file.path.contains(imagesDir)
        }

        if (!file.isDirectory) {
            if (XUtils.isImage(file.name) && file.path.contains(imagesDir)) {
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
    }
}
