package com.wanggaowan.tools.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.ui.ImportImageFolderChooser
import com.wanggaowan.tools.ui.RenameEntity
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.PropertiesSerializeUtils
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.flutterModules
import com.wanggaowan.tools.utils.ex.isFlutterProject

/**
 * 导入不同分辨率相同图片资源
 *
 * @author Created by wanggaowan on 2022/7/12 09:42
 */
class ImportSameImageResAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
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

    // override fun getActionUpdateThread(): ActionUpdateThread {
    //     return ActionUpdateThread.BGT
    // }

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

    fun import(project: Project, files: List<VirtualFile>, importToFolder: VirtualFile? = null) {
        val distinctFiles = getDistinctFiles(files)
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
            val file = dialog.getSelectedFolder() ?: return@setOkActionListener
            PropertiesSerializeUtils.putString(project, IMPORT_TO_FOLDER, file.path)
            importImages(project, distinctFiles, file, dialog.getRenameFileMap())
        }
        dialog.isVisible = true
    }

    /**
     * 获取选择的文件去重后的数据
     */
    private fun getDistinctFiles(selectedFiles: List<VirtualFile>): List<VirtualFile> {
        val dataList = mutableListOf<VirtualFile>()
        selectedFiles.forEach {
            if (it.isDirectory) {
                val dirName = it.name
                it.children?.forEach { child ->
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
                                val name2 = child2.name
                                if (!name2.startsWith(".")) {
                                    if (!child2.isDirectory) {
                                        dataList.add(child2)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (fileCouldAdd(it)) {
                dataList.add(it)
            }
        }

        // 去重，每个文件名称只保留一个数据
        return distinctFile(dataList)
    }

    private fun fileCouldAdd(file: VirtualFile): Boolean {
        if (file.name.startsWith(".")) {
            return false
        }

        val parent = file.parent ?: return false
        val parentName = parent.name
        return parentName.startsWith("drawable") || parentName.startsWith("mipmap")

    }

    /**
     * 转化数据，获取所有需要导入的文件
     */
    private fun mapChosenFiles(selectedFiles: List<VirtualFile>): Map<String, MutableList<VirtualFile>> {
        val allFiles = mutableMapOf<String, MutableList<VirtualFile>>()
        // 获取不同分辨率下相同文件名称的图片
        selectedFiles.forEach {
            it.parent.parent.children?.forEach { child ->
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
        importToFolder: VirtualFile, renameMap: Map<String, List<RenameEntity>>
    ) {
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
                        folders.add(importToFolder.createChildDirectory(null, it))
                    } catch (e: Exception) {
                        return@runWriteCommandAction
                    }
                }
            }

            folders.forEach { folder ->
                val files = if (folder == importToFolder) {
                    mapFiles["1.0x"]
                } else {
                    mapFiles[folder.name]
                }

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

                            if (renameEntity != null && renameEntity.existFile && renameEntity.coverExistFile) {
                                // 删除已经存在的文件
                                folder.findChild(renameEntity.newName)?.delete(project)
                            }

                            child.copy(project, folder, renameEntity?.newName ?: child.name)
                        } catch (e: Exception) {
                            // 可能是导入文件已经存在
                        }
                    }
                }
            }

            NotificationUtils.showBalloonMsg(project, "图片已导入", NotificationType.INFORMATION)
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
