package com.wanggaowan.tools.utils.flutter

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.LocalTimeCounter
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequenceItem

/**
 * 解析Yaml文件
 *
 * @author Created by wanggaowan on 2023/2/9 10:45
 */
object YamlUtils {

    /**
     * 仅查找dependencies分组的依赖
     */
    const val DEPENDENCY_TYPE_RELEASE = 0

    /**
     * 仅查找dev_dependencies分组的依赖
     */
    const val DEPENDENCY_TYPE_DEV = 1

    /**
     * 查找dependencies和dev_dependencies分组的依赖
     */
    const val DEPENDENCY_TYPE_ALL = 2

    /**
     * 解析指定文件是否包含[dependency]指定的依赖,[type]指定查找的依赖分组
     */
    fun haveDependencies(psiFile: PsiFile, type: Int, dependency: String): Boolean {
        when (type) {
            DEPENDENCY_TYPE_DEV -> {
                val element = findElement(psiFile, "dev_dependencies")
                return parseDependencies(element, dependency)
            }

            DEPENDENCY_TYPE_RELEASE -> {
                val element = findElement(psiFile, "dependencies")
                return parseDependencies(element, dependency)
            }

            else -> {
                var element = findElement(psiFile, "dev_dependencies")
                if (parseDependencies(element, dependency)) {
                    return true
                }

                element = findElement(psiFile, "dependencies")
                return parseDependencies(element, dependency)
            }
        }
    }

    private fun parseDependencies(psiElement: PsiElement?, dependency: String): Boolean {
        if (psiElement == null) {
            return false
        }

        for (child in psiElement.children) {
            if (child is YAMLMapping) {
                for (child2 in child.children) {
                    if (child2 is YAMLKeyValue) {
                        if (child2.firstChild.textMatches(dependency)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    /**
     * 查找[parent]节点下指定[name]的子节点，只查找[parent]的直接子类，比如有如下结构：
     *
     * ```
     * # pubspec.yaml
     * ...
     * flutter:
     *   assets:
     *      -assets/images/
     * ```
     * 想得到pubspec.yaml下flutter下的assets节点，需要调用两次：
     * ```
     * val flutterElement = findElement(pubspecPsiFle, "flutter")
     * val assetsElement = findElement(flutterElement, "assets")
     * ```
     */
    fun findElement(parent: PsiElement, name: String): PsiElement? {
        for (child in parent.children) {
            if (child is YAMLDocument) {
                val element = child.firstChild
                return if (element == null) {
                    null
                } else {
                    findElement(child.firstChild, name)
                }
            } else if (child is YAMLMapping) {
                val element = findElement(child, name)
                if (element != null) {
                    return element
                }
            } else if (child is YAMLKeyValue) {
                if (child.firstChild.textMatches(name)) {
                    return child
                }
            }
        }
        return null
    }

    /**
     * 创建yaml文件
     */
    fun createDummyFile(project: Project, content: String = ""): PsiFile {
        return PsiFileFactory.getInstance(project).createFileFromText(
            "dummy.${YAMLFileType.YML.defaultExtension}",
            YAMLFileType.YML, content, LocalTimeCounter.currentTime(), false
        )
    }

    /**
     * 创建 key:value结构，[content]如果没有值，可以只传 key: 这种结构，一定要带:
     */
    fun createYAMLKeyValue(project: Project, content: String): YAMLKeyValue? {
        val psiFile = createDummyFile(project, content)
        return PsiTreeUtil.findChildOfType(psiFile, YAMLKeyValue::class.java)
    }

    /**
     * 创建如下结构中 - images/ 或 - images/a.png 节点
     * ```
     * assets:
     *  - images/
     *  - images/a.png
     * ```
     */
    fun createYAMLSequenceItem(project: Project, content: String): YAMLSequenceItem? {
        val psiFile = createDummyFile(project, content)
        return PsiTreeUtil.findChildOfType(psiFile, YAMLSequenceItem::class.java)
    }
}


