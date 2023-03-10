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
    private const val IMAGES_REF_FILE_PATH = "imagesRefFilePath"
    private const val IMAGES_REF_FILE_NAME = "imagesRefFileName"
    private const val IMAGES_REF_CLASS_NAME = "imagesRefClassName"

    private const val EXAMPLE_IMAGE_DIR = "exampleImageDir"
    private const val EXAMPLE_IMAGES_REF_FILE_PATH = "exampleImagesRefFilePath"
    private const val EXAMPLE_IMAGES_REF_FILE_NAME = "exampleImagesRefFileName"
    private const val EXAMPLE_IMAGES_REF_CLASS_NAME = "exampleImagesRefClassName"

    const val DEFAULT_IMAGE_DIR = "assets/images"
    const val DEFAULT_IMAGES_REF_FILE_PATH = "lib/resources"
    const val DEFAULT_IMAGES_REF_FILE_NAME = "images.dart"
    const val DEFAULT_IMAGES_REF_CLASS_NAME = "Images"

    fun getImagesFileDir(project: Project? = null): String {
        return getValue(project, IMAGE_DIR, "assets/images")
    }

    fun setImagesFileDir(project: Project? = null, value: String) {
        setValue(project, IMAGE_DIR, value)
    }

    fun getImagesRefFilePath(project: Project? = null): String {
        return getValue(project, IMAGES_REF_FILE_PATH, "lib/resources")
    }

    fun setImagesRefFilePath(project: Project? = null, value: String) {
        setValue(project, IMAGES_REF_FILE_PATH, value)
    }

    fun getImagesRefFileName(project: Project? = null): String {
        return getValue(project, IMAGES_REF_FILE_NAME, "images.dart")
    }

    fun setImagesRefFileName(project: Project? = null, value: String) {
        setValue(project, IMAGES_REF_FILE_NAME, value)
    }

    fun getImagesRefClassName(project: Project? = null): String {
        return getValue(project, IMAGES_REF_CLASS_NAME, "Images")
    }

    fun setImagesRefClassName(project: Project? = null, value: String) {
        setValue(project, IMAGES_REF_CLASS_NAME, value)
    }

    fun getExampleImagesFileDir(project: Project? = null): String {
        return getValue(project, EXAMPLE_IMAGE_DIR, "assets/images")
    }

    fun setExampleImagesFileDir(project: Project? = null, value: String) {
        setValue(project, EXAMPLE_IMAGE_DIR, value)
    }

    fun getExampleImagesRefFilePath(project: Project? = null): String {
        return getValue(project, EXAMPLE_IMAGES_REF_FILE_PATH, "lib/resources")
    }

    fun setExampleImagesRefFilePath(project: Project? = null, value: String) {
        setValue(project, EXAMPLE_IMAGES_REF_FILE_PATH, value)
    }

    fun getExampleImagesRefFileName(project: Project? = null): String {
        return getValue(project, EXAMPLE_IMAGES_REF_FILE_NAME, "images.dart")
    }

    fun setExampleImagesRefFileName(project: Project? = null, value: String) {
        setValue(project, EXAMPLE_IMAGES_REF_FILE_NAME, value)
    }

    fun getExampleImagesRefClassName(project: Project? = null): String {
        return getValue(project, EXAMPLE_IMAGES_REF_CLASS_NAME, "Images")
    }

    fun setExampleImagesRefClassName(project: Project? = null, value: String) {
        setValue(project, EXAMPLE_IMAGES_REF_CLASS_NAME, value)
    }

    private fun getValue(project: Project?, key: String, defValue: String): String {
        return if (project == null) {
            var dir = PropertiesSerializeUtils.getString(key, defValue)
            if (dir.isEmpty()) {
                dir = defValue
            }
            dir
        } else {
            var dir = PropertiesSerializeUtils.getString(project, key)
            if (dir.isEmpty()) {
                dir = PropertiesSerializeUtils.getString(key, defValue)
                if (dir.isEmpty()) {
                    dir = defValue
                }
            }
            dir
        }
    }

    private fun setValue(project: Project? = null, key: String, value: String) {
        if (project == null) {
            PropertiesSerializeUtils.putString(key, value)
        } else {
            PropertiesSerializeUtils.putString(project, key, value)
        }
    }
}
