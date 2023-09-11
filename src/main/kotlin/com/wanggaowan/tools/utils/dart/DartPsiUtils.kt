package com.wanggaowan.tools.utils.dart

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.LocalTimeCounter
import com.jetbrains.lang.dart.DartFileType
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartIncompleteDeclaration

// import com.jetbrains.lang.dart.psi.DartFile

/**
 * 创建Dart PsiElement工具类
 *
 * @author Created by wanggaowan on 2023/2/5 22:34
 */
object DartPsiUtils {

    /**
     * 查找指定下标位置element，如果找不到则往前一位查找，直到下标<0
     */
    tailrec fun findElementAtOffset(psiFile: PsiFile, offset: Int): PsiElement? {
        if (offset < 0) {
            return null
        }
        val element = psiFile.findElementAt(offset)
        if (element != null) {
            return element
        }

        return findElementAtOffset(psiFile, offset - 1)
    }


    /**
     * 创建[DartFile]文件，[name]为文件名称，不带后缀
     */
    @Throws(IncorrectOperationException::class)
    fun createFile(project: Project, name: String, content: String = ""): DartFile? {
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "$name.${DartFileType.INSTANCE.defaultExtension}",
            DartFileType.INSTANCE, content, LocalTimeCounter.currentTime(), false
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
            "dummy.${DartFileType.INSTANCE.defaultExtension}",
            DartFileType.INSTANCE, text, LocalTimeCounter.currentTime(), false
        )
    }

    /**
     * 创建分号(;)PsiElement
     */
    @Throws(IncorrectOperationException::class)
    fun createSemicolonElement(project: Project): PsiElement? {
        val psiFile = createCommonPsiFile(project, ";")
        return PsiTreeUtil.findChildOfType(psiFile, LeafPsiElement::class.java)
    }

    /**
     * 创建逗号(,)PsiElement
     */
    @Throws(IncorrectOperationException::class)
    fun createCommaElement(project: Project): PsiElement? {
        val psiFile = createCommonPsiFile(project, ",")
        return PsiTreeUtil.findChildOfType(psiFile, LeafPsiElement::class.java)
    }

    /**
     * 创建两个PsiElement之间的空白分隔符
     */
    @Throws(IncorrectOperationException::class)
    fun createWhiteSpaceElement(project: Project): PsiElement? {
        val psiFile = createCommonPsiFile(project, " ")
        return psiFile.firstChild
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
        return psiFile.firstChild
    }

    /**
     * 创建Dart Class
     */
    @Throws(IncorrectOperationException::class)
    fun createClassElement(project: Project, className: String): PsiElement? {
        val psiFile = createCommonPsiFile(project, "class $className { \n}")
        return psiFile.firstChild
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
            val element = psiFile.firstChild
            if (element is DartIncompleteDeclaration) {
                // 注解
                element.firstChild
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
            "dummy.${DartFileType.INSTANCE.defaultExtension}",
            DartFileType.INSTANCE, "class Dummy { $text }", LocalTimeCounter.currentTime(), false
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

    /**
     * 创建列表项
     */
    fun createListItem(project: Project, text: String): PsiElement? {
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "dummy.${DartFileType.INSTANCE.defaultExtension}",
            DartFileType.INSTANCE, "List dummy = [$text]; ", LocalTimeCounter.currentTime(), false
        )

        return try {
            return psiFile.children[0].children[1].children[0].children[0]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 执行格式化
     *
     * @param project     项目对象
     * @param psiFile 需要格式化文件
     */
    fun reformatFile(project: Project, psiFile: PsiFile) {
        CodeStyleManagerImpl(project).reformatText(psiFile, mutableListOf(TextRange(0, psiFile.textLength)))
    }

    /**
     * 执行格式化
     *
     * @param project     项目对象
     * @param psiFile 需要格式化文件
     * @param ranges 需要格式化数据区域
     */
    fun reformatFile(project: Project, psiFile: PsiFile, ranges: Collection<TextRange>) {
        CodeStyleManagerImpl(project).reformatText(psiFile, ranges)
    }
}
