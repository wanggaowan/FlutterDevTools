package com.wanggaowan.tools.listener

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.PsiDirectoryImpl
import com.wanggaowan.tools.Sdk
import com.wanggaowan.tools.actions.image.GeneratorImageRefUtils
import com.wanggaowan.tools.listener.GeneratorImageRefListener.Companion.CHANGE
import com.wanggaowan.tools.listener.GeneratorImageRefListener.Companion.NO_CHANGE
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.XUtils
import io.flutter.pub.PubRoot
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * 生成图片资源引用，并在pubspec.yaml中生成图片位置声明监听
 *
 * @author Created by wanggaowan on 2023/3/2 22:34
 */
class GeneratorImageRefListener : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        super.after(events)
        coroutineScope.value.launch {
            val changeDataList = parseFileEvent(events)
            changeDataList.forEach {
                runJob(it.project, it.pubRoot)
            }
        }
    }

    /**
     * 执行生成图片引用文件和配置Assets节点命令
     */
    private fun runJob(project: Project, pubRoot: PubRoot) {
        val path = pubRoot.path
        var job = jobMap[path]
        // 仅仅最后一次图片变更时执行
        if (job?.isActive == true) {
            job.cancel()
        }

        job = coroutineScope.value.launch {
            delay(500.milliseconds)
            GeneratorImageRefUtils.generate(project, pubRoot)
        }

        jobMap[path] = job
    }

    /**
     * 解析文件事件，返回需要处理的文件数据
     */
    private fun parseFileEvent(events: MutableList<out VFileEvent>): List<ChangeData> {
        val changeData = mutableListOf<ChangeData>()
        for (event in events) {
            val file = event.file ?: continue
            val project = getProject(event) ?: continue

            if (event is VFileCopyEvent) {
                val pubRoot = Sdk.getInstance(project).getPubRoot(event.path) ?: continue
                val imageChange = isImageChange(1, project, pubRoot, file, event.path)
                if (imageChange != NO_CHANGE) {
                    changeData.add(ChangeData(project, pubRoot))
                }
                continue
            }

            if (event is VFileCreateEvent || event is VFileDeleteEvent) {
                val pubRoot = Sdk.getInstance(project).getPubRoot(file) ?: continue
                val imageChange = isImageChange(0, project, pubRoot, file, "")
                if (imageChange != NO_CHANGE) {
                    changeData.add(ChangeData(project, pubRoot))
                }

                continue
            }

            if (event is VFileMoveEvent) {
                var pubRoot = Sdk.getInstance(project).getPubRoot(event.oldPath)
                if (pubRoot != null) {
                    val imageChange = isImageChange(1, project, pubRoot, file, event.oldPath)
                    if (imageChange != NO_CHANGE) {
                        changeData.add(ChangeData(project, pubRoot))
                    }
                }

                pubRoot = Sdk.getInstance(project).getPubRoot(event.newPath)
                if (pubRoot != null) {
                    val imageChange = isImageChange(1, project, pubRoot, file, event.newPath)
                    if (imageChange != NO_CHANGE) {
                        changeData.add(ChangeData(project, pubRoot))
                    }
                }

                continue
            }

            if (event is VFilePropertyChangeEvent) {
                if (event.propertyName == "name") {
                    val pubRoot = Sdk.getInstance(project).getPubRoot(file) ?: continue
                    val imageChange = isImageChange(0, project, pubRoot, file, "")
                    if (imageChange != NO_CHANGE) {
                        changeData.add(ChangeData(project, pubRoot))
                    }
                }
                continue
            }
        }
        return changeData
    }

    /**
     * 图片是否变更，[type]为0表示通过[file]判断文件变更，[type]为1表示通过[path]判断文件变更。
     * 返回[NO_CHANGE]、[CHANGE]
     */
    private fun isImageChange(type: Int, project: Project, pubRoot: PubRoot, file: VirtualFile?, path: String): Int {
        if (file == null) {
            return NO_CHANGE
        }

        val imagesDir = "${pubRoot.path}/${PluginSettings.getImagesFileDir(project)}"
        if (type == 0) {
            if (isFileInDir(imagesDir, file)) {
                return CHANGE
            }

            return NO_CHANGE
        }

        if (isImage(file) && path.startsWith(imagesDir)) {
            return CHANGE
        }

        return NO_CHANGE
    }

    /**
     * 获取文件时间所处的模块
     */
    private fun getProject(event: VFileEvent): Project? {
        val requestor = event.requestor
        if (requestor is PsiManager) {
            return requestor.project
        }

        if (requestor is PsiDirectoryImpl) {
            return requestor.project
        }

        if (requestor is Project) {
            return requestor
        }

        if (requestor is Module) {
            return requestor.project
        }

        return null
    }

    /**
     * 指定文件[file]是否是给定目录[imagesDir]下的文件
     */
    @Suppress("UnsafeVfsRecursion")
    private fun isFileInDir(imagesDir: String, file: VirtualFile?): Boolean {
        if (file == null) {
            return false
        }

        if (!file.isValid) {
            // 说明已经被删除
            return file.path.startsWith(imagesDir)
        }

        if (!file.isDirectory) {
            return XUtils.isImage(file.name) && file.path.startsWith(imagesDir)
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
    @Suppress("UnsafeVfsRecursion")
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
    }
}

/**
 * 需要处理的数据
 */
data class ChangeData(
    val project: Project,
    val pubRoot: PubRoot
)

