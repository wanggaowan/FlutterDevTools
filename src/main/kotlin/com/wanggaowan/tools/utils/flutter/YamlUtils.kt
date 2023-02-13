package com.wanggaowan.tools.utils.flutter

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

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
        for (child in psiFile.children) {
            if (child is YAMLDocument) {
                for (child2 in child.children) {
                    if (child2 is YAMLMapping) {
                        for (child3 in child2.children) {
                            if (child3 is YAMLKeyValue) {
                                if (type == DEPENDENCY_TYPE_DEV) {
                                    if (child3.firstChild.textMatches("dev_dependencies")) {
                                        return parseDependencies(child3, dependency)
                                    }
                                } else if (type == DEPENDENCY_TYPE_RELEASE) {
                                    if (child3.firstChild.textMatches("dependencies")) {
                                        return parseDependencies(child3, dependency)
                                    }
                                } else {
                                    if (child3.firstChild.textMatches("dev_dependencies")) {
                                        if (parseDependencies(child3, dependency)) {
                                            return true
                                        }
                                    }

                                    if (child3.firstChild.textMatches("dependencies")) {
                                        if (parseDependencies(child3, dependency)) {
                                            return true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    private fun parseDependencies(psiElement: PsiElement, dependency: String): Boolean {
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
}


