package com.wanggaowan.tools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.ex.findChild
import io.flutter.pub.PubRoot

/**
 * 提供通用工具方法
 *
 * @author Created by wanggaowan on 2023/2/3 09:48
 */
object XUtils {
    /**
     * 检测项目是否有pubspec.lock文件
     */
    fun havePubspecLockFile(project: Project): Boolean {
        return project.findChild("pubspec.lock") != null
    }

    /**
     * 判断给的名称是否是图片格式
     */
    fun isImage(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith("png")
            || lower.endsWith("jpg")
            || lower.endsWith("jpeg")
            || lower.endsWith("webp")
            || lower.endsWith("gif")
            || lower.endsWith("svg")
    }

    /**
     * 判断是否是图片分辨率变体目录
     */
    fun isImageVariantsFolder(name: String?): Boolean {
        return name == "1.5x" || name == "2.0x" || name == "3.0x" || name == "4.0x"
    }

    /**
     * 将图片路径数据转化为符合Dart规则的Key
     */
    fun imagePathToDartKey(name: String): String {
        return StringUtils.lowerCamelCase(
            name.substring(0, name.lastIndexOf("."))
                .replace("/", "_")
                .replace("-", "_")
                .replace("@", ""), false
        )
    }

    /**
     * 将指定图片文件转化为符合引用文件images.dart中的key.[isCallStyle]表示返回的key是否是引用样式，
     * 比如key是：homePic，则[isCallStyle]为true时，返回Images.homePic,否则返回homePic
     */
    fun imageFileToImageKey(
        project: Project,
        parent: VirtualFile,
        fileName: String,
        isCallStyle: Boolean = false
    ): String? {
        val pubRoot = PubRoot.forFile(parent) ?: return null
        val example = pubRoot.exampleDir

        val isExample = example != null && parent.path.startsWith(example.path)

        // 生成图片资源引用文件类名称
        val imageRefClassName: String
        // 图片资源在项目中的相对路径
        val imagesRelDirPath: String = if (isExample) {
            imageRefClassName = PluginSettings.getExampleImagesRefClassName(project)
            "example/" + PluginSettings.getExampleImagesFileDir(project)
        } else {
            imageRefClassName = PluginSettings.getImagesRefClassName(project)
            PluginSettings.getImagesFileDir(project)
        }

        val dirName = parent.name
        var path = if (isImageVariantsFolder(dirName)) {
            parent.path.replace(dirName, "")
        } else {
            parent.path
        }
        path = path.replace("${pubRoot.root.path}/$imagesRelDirPath", "")
        return if (!isCallStyle) imagePathToDartKey("$path/$fileName")
        else "$imageRefClassName.${imagePathToDartKey("$path/$fileName")}"
    }
}
