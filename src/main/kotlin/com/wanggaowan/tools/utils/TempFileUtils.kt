package com.wanggaowan.tools.utils

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.wanggaowan.tools.utils.ex.basePath
import java.io.File

/**
 * 临时文件工具类
 *
 * @author Created by wanggaowan on 2023/11/15 08:33
 */
object TempFileUtils {

    /**
     * 获取备份缓存目录
     */
    fun getCopyCacheFolder(project: Project): VirtualFile? {
        val basePath = project.basePath ?: return null
        return getCacheFolder(basePath, "copyCache", true)
    }

    /**
     * 获取备份缓存目录
     */
    fun getCopyCacheFolder(module: Module): VirtualFile? {
        val basePath = module.basePath ?: return null
        return getCacheFolder(basePath, "copyCache", false) ?: return getCopyCacheFolder(module.project)
    }

    /**
     * 获取解压缩缓存目录
     */
    fun getUnZipCacheFolder(project: Project): VirtualFile? {
        val basePath = project.basePath ?: return null
        return getCacheFolder(basePath, "unZipCache", true)
    }

    /**
     * 获取解压缩缓存目录
     */
    fun getUnZipCacheFolder(module: Module): VirtualFile? {
        val basePath = module.basePath ?: return null
        return getCacheFolder(basePath, "unZipCache", false) ?: return getCopyCacheFolder(module.project)
    }

    /**
     * 清除备份缓存目录
     */
    fun clearCopyCacheFolder(project: Project) {
        val basePath = project.basePath ?: return
        clearCacheFolder(basePath, "copyCache", true)
    }

    /**
     * 清除备份缓存目录
     */
    fun clearCopyCacheFolder(module: Module) {
        clearCopyCacheFolder(module.project)
        val basePath = module.basePath ?: return
        clearCacheFolder(basePath, "copyCache", true)
    }

    /**
     * 清除解压缩缓存目录
     */
    fun clearUnZipCacheFolder(project: Project) {
        val basePath = project.basePath ?: return
        clearCacheFolder(basePath, "unZipCache", false)
    }

    /**
     * 清除解压缩缓存目录
     */
    fun clearUnZipCacheFolder(module: Module) {
        clearUnZipCacheFolder(module.project)
        val basePath = module.basePath ?: return
        clearCacheFolder(basePath, "unZipCache", false)
    }


    private fun getCacheFolder(
        basePath: String,
        folderName: String,
        noIedCreate: Boolean
    ): VirtualFile? {
        val rootFolder = File(basePath)
        if (!rootFolder.exists()) {
            return null
        }

        val ideaFolder = File(rootFolder,".idea")
        if (!ideaFolder.exists()) {
            if (!noIedCreate) {
                return null
            }

            if (!ideaFolder.mkdirs()) {
                return null
            }
        }

        val cacheFolder = File(ideaFolder,folderName)
        if (!cacheFolder.exists()) {
            if (!cacheFolder.mkdirs()) {
                return null
            }
        }

        return VirtualFileManager.getInstance().refreshAndFindFileByUrl("file://${cacheFolder.path}")
    }

    private fun clearCacheFolder(
        basePath: String,
        folderName: String,
        justDeleteChildren: Boolean
    ) {
        val ideaFolder = File(basePath + File.separator + ".idea")
        if (!ideaFolder.exists()) {
            return
        }

        val cacheFolder = File(ideaFolder,folderName)
        if (!cacheFolder.exists()) {
            return
        }

        deleteDirectory(cacheFolder,justDeleteChildren)
    }

    private fun deleteDirectory(directory: File,justDeleteChildren: Boolean){
        if (directory.isDirectory) {
            val files = directory.listFiles()
            if (files != null && files.isNotEmpty()) {
                for (file in files) {
                    if (file.isDirectory) {
                        deleteDirectory(file,false)
                    } else {
                        file.delete()
                    }
                }
            }
        }

        if (justDeleteChildren) {
            return
        }

        directory.delete()
    }
}
