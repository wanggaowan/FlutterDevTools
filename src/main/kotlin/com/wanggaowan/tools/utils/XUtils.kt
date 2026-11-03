package com.wanggaowan.tools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.wanggaowan.tools.Sdk
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.ex.findChild

/**
 * 提供通用工具方法
 *
 * @author Created by wanggaowan on 2023/2/3 09:48
 */
object XUtils {
    /**
     * 图片引用生成器
     * 根据图片文件生成 Dart 类的 getter 方法
     */
    val DART_KEYWORDS = setOf(
        "abstract",
        "as",
        "assert",
        "async",
        "await",
        "base",
        "break",
        "case",
        "catch",
        "class",
        "const",
        "continue",
        "covariant",
        "default",
        "deferred",
        "do",
        "dynamic",
        "else",
        "enum",
        "export",
        "extends",
        "extension",
        "external",
        "factory",
        "false",
        "final",
        "finally",
        "for",
        "Function",
        "get",
        "hide",
        "if",
        "implements",
        "import",
        "in",
        "interface",
        "is",
        "late",
        "library",
        "mixin",
        "new",
        "null",
        "of",
        "on",
        "operator",
        "part",
        "required",
        "rethrow",
        "return",
        "sealed",
        "set",
        "show",
        "static",
        "super",
        "switch",
        "sync",
        "this",
        "throw",
        "true",
        "try",
        "type",
        "typedef",
        "var",
        "void",
        "when",
        "while",
        "with",
        "yield",
    )

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
            || lower.endsWith("webm")
            || lower.endsWith("bmp")
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
        val nameMap = name.replace("""\.[^/.]+$""".toRegex(), "") // 去掉后缀名
            .replace("""[0-9]+\.[0-9]+x/""".toRegex(), "") // 去掉变体目录
            .replace("@", "")
            .replace("""[^A-Za-z0-9_]+""".toRegex(), "_") // 将非法标识符字符（含 /, -, ., 空格, 中文等）统一替换为下划线

        var methodName = StringUtils.lowerCamelCase(nameMap, false)
        if (methodName.isNotEmpty() && methodName[0].isDigit()) {
            // Dart 标识符不能以数字开头
            methodName = "img${methodName}"
        }

        if (DART_KEYWORDS.indexOf(methodName) != -1) {
            // 避免生成 `static String get class => ...` 这类非法代码
            methodName = "img${methodName}"
        }

        return methodName
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
        val pubRoot = Sdk.getInstance(project).getPubRoot(parent) ?: return null

        // 生成图片资源引用文件类名称
        val imageRefClassName: String = PluginSettings.getImagesRefClassName(project)
        // 图片资源在项目中的相对路径
        val imagesRelDirPath: String = PluginSettings.getImagesFileDir(project)

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
