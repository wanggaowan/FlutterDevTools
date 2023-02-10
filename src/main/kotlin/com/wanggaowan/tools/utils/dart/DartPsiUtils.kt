package com.wanggaowan.tools.utils.dart

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.util.IncorrectOperationException
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartIncompleteDeclaration

// import com.jetbrains.lang.dart.psi.DartFile

/**
 * 创建Dart PsiElement工具类
 *
 * @author Created by wanggaowan on 2023/2/5 22:34
 */
object DartPsiUtils {

    // val DART_CLASS: IElementType = IElementType("", DartLanguage.INSTANCE)
    //
    // fun getElementClass(psiElement: PsiElement):PsiElement? {
    //     if (psiElement is DartClassBody) {
    //         return psiElement
    //     }
    //
    //     val parent = psiElement.parent ?: return null
    //
    //
    //     for (child in parent.children) {
    //         if (child is DartClassBody) {
    //             return child
    //         }
    //     }
    //
    //     getElementClass(parent)
    // }


    /**
     * 创建[DartFile]文件，[name]为文件名称，不带后缀
     */
    @Throws(IncorrectOperationException::class)
    fun createFile(project: Project, name: String): DartFile? {
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "$name.dart",
            DartLanguage.INSTANCE, "", false, true
        )

        if (psiFile is DartFile) {
            return psiFile
        }

        return null
    }

    @Throws(IncorrectOperationException::class)
    private fun createCommonPsiFile(project: Project, text: String): PsiFile {
        // val factory: FileViewProviderFactory = LanguageFileViewProviders.INSTANCE.forLanguage(DartLanguage.INSTANCE)
        // val viewProvider = factory?.createFileViewProvider(virtualFile, language, myManager, physical)
        // return DartFile(viewProvider)
        return PsiFileFactory.getInstance(project).createFileFromText(
            "dummy.dart",
            DartLanguage.INSTANCE, text, false, true
        )
    }

    /**
     * 创建分号(;)PsiElement
     */
    @Throws(IncorrectOperationException::class)
    fun createSemicolonElement(project: Project): PsiElement? {
        val psiFile = createCommonPsiFile(project, ";")
        return try {
            psiFile.children[0].children[0]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 创建两个PsiElement之间的空白分隔符
     */
    @Throws(IncorrectOperationException::class)
    fun createWhiteSpaceElement(project: Project): PsiElement? {
        val psiFile = createCommonPsiFile(project, " ")

        val children = psiFile.children
        if (children.isEmpty()) {
            return null
        }

        return children[0]
    }

    /**
     * 创建注释,支持以下四种格式
     *
     * // 单行注释
     *
     * /* 多行注释 */
     *
     * /// 文档注释
     *
     * /** 文档注释 */
     */
    @Throws(IncorrectOperationException::class)
    fun createDocElement(project: Project, doc: String): PsiElement? {
        val psiFile = createCommonPsiFile(project, doc)

        val children = psiFile.children
        if (children.isEmpty()) {
            return null
        }

        return children[0]
    }

    /**
     * 创建Dart Class
     */
    @Throws(IncorrectOperationException::class)
    fun createClassElement(project: Project, className: String): PsiElement? {
        val psiFile = createCommonPsiFile(project, "class $className { \n}")

        val children = psiFile.children
        if (children.isEmpty()) {
            return null
        }

        return children[0]
    }

    /**
     * 创建通用对象，如：
     *
     * // 属性定义, ';'需要使用[createSemicolonElement]单独创建，通过以下方式最终仅能得到 String name
     * String name;
     *
     * // 构造函数
     * Test({required this.name});
     *
     * // 方法
     * String toString() => name;
     *
     * // 注解
     * @JsonKey()
     */
    fun createCommonElement(project: Project, text: String): PsiElement? {
        val psiFile = createCommonPsiFile(project, text)
        return try {
            val element = psiFile.children[0]
            if (element is DartIncompleteDeclaration) {
                // 注解
                element.children[0]
            } else {
                element
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 创建class中的对象，如：
     *
     * // 属性定义, ';'需要使用[createSemicolonElement]单独创建，通过以下方式最终仅能得到 String name
     * String name;
     *
     * // 构造函数
     * Test({required this.name});
     *
     * // 方法
     * String toString() => name;
     *
     * // 注解
     * @JsonKey()
     *
     * // 工厂方法只能使用此方法创建
     * factory Author.fromJson(Map<String, dynamic> json) => _$AuthorFromJson(json);
     */
    fun createClassMember(project: Project, text: String): PsiElement? {
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "dummy.dart",
            DartLanguage.INSTANCE, "class Dummy { $text }", false, true
        )

        return try {
            val element = psiFile.children[0].children[1].children[0].children[0]
            if (element is DartIncompleteDeclaration) {
                // 注解
                element.children[0]
            } else {
                element
            }
        } catch (e: Exception) {
            null
        }
    }
}
