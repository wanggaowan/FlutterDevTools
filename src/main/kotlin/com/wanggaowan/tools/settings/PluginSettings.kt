package com.wanggaowan.tools.settings

import com.wanggaowan.tools.listener.ProjectManagerListenerImpl
import com.wanggaowan.tools.utils.PropertiesSerializeUtils

/**
 * 插件设置
 *
 * @author Created by wanggaowan on 2023/2/19 16:38
 */
object PluginSettings {
    private const val IMAGE_DIR = "imageDir"
    private const val IMAGES_DART_FILE_GENERATE_PATH = "imagesDartFileGeneratePath"

    /**
     * 图片文件存放的位置
     */
    var imagesFileDir: String
        get() {
            val project = ProjectManagerListenerImpl.project
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
        set(value) {
            val project = ProjectManagerListenerImpl.project
            if (project == null) {
                PropertiesSerializeUtils.putString(IMAGE_DIR, value )
            } else {
                PropertiesSerializeUtils.putString(project, IMAGE_DIR, value)
            }
        }

    /**
     * 生成的图片资源引用文件放置位置
     */
    var imagesDartFileGeneratePath: String
        get() {
            val project = ProjectManagerListenerImpl.project
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
        set(value) {
            val project = ProjectManagerListenerImpl.project
            if (project == null) {
                PropertiesSerializeUtils.putString(IMAGES_DART_FILE_GENERATE_PATH, value)
            } else {
                PropertiesSerializeUtils.putString(project, IMAGES_DART_FILE_GENERATE_PATH, value)
            }
        }
}
