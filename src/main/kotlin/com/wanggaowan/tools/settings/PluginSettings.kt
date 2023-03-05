package com.wanggaowan.tools.settings

import com.intellij.openapi.project.Project
import com.wanggaowan.tools.utils.PropertiesSerializeUtils

/**
 * 插件设置
 *
 * @author Created by wanggaowan on 2023/2/19 16:38
 */
object PluginSettings {
    private const val IMAGE_DIR = "imageDir"
    private const val IMAGES_DART_FILE_GENERATE_PATH = "imagesDartFileGeneratePath"

    fun getImagesFileDir(project: Project? = null):String {
        return if (project == null) {
            var dir = PropertiesSerializeUtils.getString(IMAGE_DIR, "assets/images")
            if (dir.isEmpty()) {
                dir = "assets/images"
            }
            dir
        } else {
            var dir = PropertiesSerializeUtils.getString(project, IMAGE_DIR)
            if (dir.isEmpty()) {
                dir = PropertiesSerializeUtils.getString(IMAGE_DIR, "assets/images")
                if (dir.isEmpty()) {
                    dir = "assets/images"
                }
            }
            dir
        }
    }

    fun setImagesFileDir(project: Project? = null,value: String) {
        if (project == null) {
            PropertiesSerializeUtils.putString(IMAGE_DIR, value )
        } else {
            PropertiesSerializeUtils.putString(project, IMAGE_DIR, value)
        }
    }

    fun getImagesDartFileGeneratePath(project: Project? = null):String {
        return if (project == null) {
            var dir = PropertiesSerializeUtils.getString(IMAGES_DART_FILE_GENERATE_PATH, "lib/resources")
            if (dir.isEmpty()) {
                dir = "lib/resources"
            }
            dir
        } else {
            var dir = PropertiesSerializeUtils.getString(project, IMAGES_DART_FILE_GENERATE_PATH)
            if (dir.isEmpty()) {
                dir = PropertiesSerializeUtils.getString(IMAGES_DART_FILE_GENERATE_PATH, "lib/resources")
                if (dir.isEmpty()) {
                    dir = "lib/resources"
                }
            }
            dir
        }
    }

    fun setImagesDartFileGeneratePath(project: Project? = null,value: String) {
        if (project == null) {
            PropertiesSerializeUtils.putString(IMAGES_DART_FILE_GENERATE_PATH, value)
        } else {
            PropertiesSerializeUtils.putString(project, IMAGES_DART_FILE_GENERATE_PATH, value)
        }
    }
}
