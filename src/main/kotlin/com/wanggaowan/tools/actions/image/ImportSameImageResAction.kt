package com.wanggaowan.tools.actions.image

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.*
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.flutterModules
import com.wanggaowan.tools.utils.ex.isFlutterProject
import io.flutter.utils.ProgressHelper

private val LOG = logger<ImportSameImageResUtils>()

/**
 * 导入不同分辨率相同图片资源
 *
 * @author Created by wanggaowan on 2022/7/12 09:42
 */
class ImportSameImageResAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptor(true, true, true, true, false, true)
        val selectFile = PropertiesSerializeUtils.getString(project, IMPORT_FROM_FOLDER).let {
            if (it.isEmpty()) null else VirtualFileManager.getInstance().findFileByUrl("file://$it")
        }

        FileChooser.chooseFiles(descriptor, project, selectFile) { files ->
            if (!files.isNullOrEmpty()) {
                PropertiesSerializeUtils.putString(project, IMPORT_FROM_FOLDER, files[0]?.path ?: "")
                var module = e.getData(LangDataKeys.MODULE)
                module = if (module.isFlutterProject) module else {
                    val modules = project.flutterModules
                    if (modules.isNullOrEmpty()) {
                        null
                    } else {
                        modules[0]
                    }
                }

                val importToFolder = if (module == null) null else
                    VirtualFileManager.getInstance()
                        .findFileByUrl("file://${module.basePath}/${PluginSettings.getImagesFileDir(project)}")
                ImportSameImageResUtils.import(project, files, importToFolder)
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    companion object {
        /**
         * 图片导出的目录
         */
        private const val IMPORT_FROM_FOLDER = "importFromFolder"
    }
}

object ImportSameImageResUtils {
    /**
     * 图片导入的目录
     */
    private const val IMPORT_TO_FOLDER = "importToFolder"

    fun import(
        project: Project,
        files: List<VirtualFile>,
        importToFolder: VirtualFile? = null,
        doneCallback: (() -> Unit)? = null
    ) {
        val progressHelper = ProgressHelper(project)
        progressHelper.start("parse image data")
        val distinctFiles = getDistinctFiles(project, files)
        progressHelper.done()
        var selectFile: VirtualFile? = importToFolder
        if (importToFolder == null) {
            val preSelectFileFolder = PropertiesSerializeUtils.getString(project, IMPORT_TO_FOLDER)
            if (preSelectFileFolder.isNotEmpty()) {
                selectFile =
                    VirtualFileManager.getInstance().findFileByUrl("file://${preSelectFileFolder}")
            }
        }

        if (selectFile == null) {
            selectFile =
                VirtualFileManager.getInstance()
                    .findFileByUrl("file://${project.basePath}/${PluginSettings.getImagesFileDir(project)}")
        }

        val dialog = ImportImageFolderChooser(project, "导入图片", selectFile, distinctFiles)
        dialog.setOkActionListener {
            val file = dialog.getSelectedFolder()
            if (file == null) {
                TempFileUtils.clearUnZipCacheFolder(project)
                return@setOkActionListener
            }

            PropertiesSerializeUtils.putString(project, IMPORT_TO_FOLDER, file.path)
            importImages(project, distinctFiles, file, dialog.getRenameFileMap(), doneCallback)
        }
        dialog.setCancelActionListener {
            TempFileUtils.clearUnZipCacheFolder(project)
        }
        dialog.isVisible = true
    }

    /**
     * 获取选择的文件去重后的数据
     */
    private fun getDistinctFiles(project: Project, selectedFiles: List<VirtualFile>): List<VirtualFile> {
        val dataList = mutableListOf<VirtualFile>()
        selectedFiles.forEach {
            if (it.isDirectory) {
                val dirName = it.name
                it.children?.forEach { child ->
                    val name = child.name
                    if (!name.startsWith(".")) {
                        if (!child.isDirectory) {
                            if (it.name.lowercase().endsWith(".zip")) {
                                parseZipFile(project, it, dirName, dataList)
                            } else if (fileCouldAdd(child)) {
                                dataList.add(child)
                            }
                        } else if (
                        // 当前child父对象不是drawable或mipmap开头，只解析这个目录中的图片，不解析目录
                            (!dirName.startsWith("drawable") && !dirName.startsWith("mipmap")) &&
                            (name.startsWith("drawable") || name.startsWith("mipmap"))
                        ) {
                            // 只解析两层目录
                            child.children?.forEach { child2 ->
                                if (fileCouldAdd(child2)) {
                                    dataList.add(child2)
                                }
                            }
                        }
                    }
                }
            } else if (it.name.lowercase().endsWith(".zip")) {
                parseZipFile(project, it, "", dataList)
            } else if (fileCouldAdd(it)) {
                dataList.add(it)
            }
        }

        // 去重，每个文件名称只保留一个数据
        return distinctFile(dataList)
    }

    private fun parseDirectory(directory: VirtualFile, dataList: MutableList<VirtualFile>) {
        val dirName = directory.name
        directory.children?.forEach { child ->
            val name = child.name
            if (!name.startsWith(".")) {
                if (!child.isDirectory) {
                    if (fileCouldAdd(child)) {
                        dataList.add(child)
                    }
                } else if (
                // 当前child父对象不是drawable或mipmap开头，只解析这个目录中的图片，不解析目录
                    (!dirName.startsWith("drawable") && !dirName.startsWith("mipmap")) &&
                    (name.startsWith("drawable") || name.startsWith("mipmap"))
                ) {
                    // 只解析两层目录
                    child.children?.forEach { child2 ->
                        if (fileCouldAdd(child2)) {
                            dataList.add(child2)
                        }
                    }
                }
            }
        }
    }

    private fun parseZipFile(
        project: Project,
        file: VirtualFile,
        parentName: String,
        dataList: MutableList<VirtualFile>
    ) {
        val folder = TempFileUtils.getUnZipCacheFolder(project)
        if (folder != null) {
            val descDir = if (parentName.isNotEmpty()) {
                folder.path + parentName
            } else {
                folder.path
            }

            val unZipFile = ZipUtil.unzip(file.path, descDir) ?: return
            val directory =
                VirtualFileManager.getInstance().refreshAndFindFileByUrl("file://${unZipFile.path}") ?: return
            parseDirectory(directory, dataList)
        }
    }

    private fun fileCouldAdd(file: VirtualFile): Boolean {
        if (file.isDirectory) {
            return false
        }

        val name = file.name
        if (name.startsWith(".")) {
            return false
        }

        return XUtils.isImage(name)
    }


    /**
     * 转化数据，获取所有需要导入的文件
     */
    private fun mapChosenFiles(selectedFiles: List<VirtualFile>): Map<String, MutableList<VirtualFile>> {
        val allFiles = mutableMapOf<String, MutableList<VirtualFile>>()
        // 获取不同分辨率下相同文件名称的图片
        selectedFiles.forEach {
            val parent = it.parent
            val name = parent.name
            if (name.contains("drawable") || name.contains("mipmap")) {
                parent.parent.children?.forEach { child ->
                    if ((child.name.contains("drawable") && it.path.contains("drawable"))
                        || child.name.contains("mipmap") && it.path.contains("mipmap")
                    ) {
                        child.findChild(it.name)?.let { file ->
                            val mapFolder = ANDROID_DIR_MAP_FLUTTER_DIR[child.name]
                            if (mapFolder != null) {
                                var list = allFiles[mapFolder]
                                if (list == null) {
                                    list = mutableListOf()
                                    allFiles[mapFolder] = list
                                }
                                list.add(file)
                            }
                        }
                    }
                }
            } else {
                val mapFolder = "1.0x"
                var list2 = allFiles[mapFolder]
                if (list2 == null) {
                    list2 = mutableListOf()
                    allFiles[mapFolder] = list2
                }
                list2.add(it)
            }
        }
        return allFiles
    }

    /**
     * 去除重复的数据
     */
    private fun distinctFile(dataList: List<VirtualFile>): List<VirtualFile> {
        val list = mutableListOf<VirtualFile>()
        dataList.forEach {
            var exist = false
            val isDrawable = it.path.contains("drawable")
            val isMipmap = it.path.contains("mipmap")
            for (file in list) {
                if (file.name == it.name) {
                    if (file.path.contains("drawable") && isDrawable) {
                        exist = true
                        break
                    } else if (file.path.contains("mipmap") && isMipmap) {
                        exist = true
                        break
                    }
                }
            }

            if (!exist) {
                list.add(it)
            }
        }
        return list
    }

    private fun importImages(
        project: Project, importFiles: List<VirtualFile>,
        importToFolder: VirtualFile, renameMap: Map<String, List<RenameEntity>>,
        doneCallback: (() -> Unit)? = null
    ) {
        val progressHelper = ProgressHelper(project)
        progressHelper.start("import image")
        WriteCommandAction.runWriteCommandAction(project) {
            val mapFiles = mapChosenFiles(importFiles)
            val folders: LinkedHashSet<VirtualFile> = LinkedHashSet()
            folders.add(importToFolder)
            val importDstFolders = importToFolder.children
            mapFiles.keys.forEach {
                var exist = it == "1.0x"
                if (!exist && importDstFolders != null) {
                    for (importDstFolder in importDstFolders) {
                        if (importDstFolder.isDirectory && importDstFolder.name == it) {
                            exist = true
                            folders.add(importDstFolder)
                            break
                        }
                    }
                }

                if (!exist) {
                    try {
                        folders.add(importToFolder.createChildDirectory(project, it))
                    } catch (_: Exception) {
                        return@runWriteCommandAction
                    }
                }
            }

            var existsException = false
            var fileName: String? = null
            var parent: VirtualFile? = null
            folders.forEach { folder ->
                val files = if (folder == importToFolder) {
                    mapFiles["1.0x"]
                } else {
                    mapFiles[folder.name]
                }
                parent = folder

                files?.let {
                    it.forEach { child ->
                        try {
                            val parentName = child.parent?.name
                            val mapFolder = if (parentName == null) ""
                            else if (parentName.contains("drawable")) "Drawable"
                            else if (parentName.contains("mipmap")) {
                                "Mipmap"
                            } else {
                                ""
                            }

                            val renameList = renameMap[mapFolder]
                            var renameEntity: RenameEntity? = null
                            if (renameList != null) {
                                for (rename in renameList) {
                                    if (rename.oldName == child.name) {
                                        renameEntity = rename
                                        break
                                    }
                                }
                            }

                            if (renameEntity != null && renameEntity.existFile) {
                                if (!renameEntity.coverExistFile) {
                                    fileName = renameEntity.newName
                                    return@forEach
                                }
                                // 删除已经存在的文件
                                folder.findChild(renameEntity.newName)?.delete(project)
                            }

                            val name = renameEntity?.newName ?: child.name
                            fileName = name
                            child.copy(project, folder, name)
                        } catch (e: Exception) {
                            existsException = true
                            LOG.error(e)
                        }
                    }
                }
            }

            if (existsException) {
                NotificationUtils.showBalloonMsg(project, "图片已导入，部分图片导入失败", NotificationType.WARNING)
            } else {
                if (importFiles.size == 1) {
                    // 导入的图片为单张时，自动创建图片的引用key到剪切板
                    if (parent != null && fileName != null) {
                        CopyImageRefKeyAction.copy(project, parent, fileName)
                    }
                }
                NotificationUtils.showBalloonMsg(project, "图片已导入", NotificationType.INFORMATION)
            }
            progressHelper.done()
            TempFileUtils.clearUnZipCacheFolder(project)
            doneCallback?.invoke()
        }
    }
}


/**
 * Android目录转化为Flutter目录对应关系
 */
val ANDROID_DIR_MAP_FLUTTER_DIR = mapOf(
    Pair("drawable", "1.0x"),
    Pair("drawable-mdpi", "1.0x"),
    Pair("drawable-hdpi", "1.5x"),
    Pair("drawable-xhdpi", "2.0x"),
    Pair("drawable-xxhdpi", "3.0x"),
    Pair("drawable-xxxhdpi", "4.0x"),
    Pair("mipmap", "1.0x"),
    Pair("mipmap-mdpi", "1.0x"),
    Pair("mipmap-hdpi", "1.5x"),
    Pair("mipmap-xhdpi", "2.0x"),
    Pair("mipmap-xxhdpi", "3.0x"),
    Pair("mipmap-xxxhdpi", "4.0x"),
)
