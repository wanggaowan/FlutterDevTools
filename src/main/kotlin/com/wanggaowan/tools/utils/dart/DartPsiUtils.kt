package com.wanggaowan.tools.utils.dart

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.LocalTimeCounter
import com.jetbrains.lang.dart.DartFileType
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartImportStatement
import com.jetbrains.lang.dart.psi.DartIncompleteDeclaration
import com.jetbrains.lang.dart.psi.DartPartStatement
import com.wanggaowan.tools.utils.dart.DartPsiUtils.createSemicolonElement

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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 创建参数项[PsiElement]
     */
    fun createArgumentItem(project: Project, text: String): PsiElement? {
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "dummy.${DartFileType.INSTANCE.defaultExtension}",
            DartFileType.INSTANCE, "List dummy = [$text]; ", LocalTimeCounter.currentTime(), false
        )

        return try {
            return psiFile.children[0].children[1].children[0].children[0]
        } catch (_: Exception) {
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
        CodeStyleManager.getInstance(project).reformatText(psiFile, mutableListOf(TextRange(0, psiFile.textLength)))
    }

    /**
     * 执行格式化
     *
     * @param project     项目对象
     * @param psiFile 需要格式化文件
     * @param ranges 需要格式化数据区域
     */
    fun reformatFile(project: Project, psiFile: PsiFile, ranges: Collection<TextRange>) {
        CodeStyleManager.getInstance(project).reformatText(psiFile, ranges)
    }

    /**
     * 添加Import导入,[importStr]为导入的内容，如：import 'package:json_annotation/json_annotation.dart'; 。
     * 如果已存在则不会插入
     */
    fun addImport(project: Project, psiFile: PsiFile, importStr: String) {
        val isRelPath = !importStr.startsWith("import 'package:")

        // import 'dart:io';
        //
        // import 'package:dio/dio.dart';
        // import 'package:kq_flutter_core_widget/network/http.dart' as http;
        //
        // import '../resources/l10n/l10n.dart';
        // import '../utils/account_util.dart';

        // 插入到import 'package:分组的最后一个
        val imports = PsiTreeUtil.getChildrenOfType(psiFile, DartImportStatement::class.java)
        if (imports.isNullOrEmpty()) {
            createCommonElement(project, importStr)?.also {
                psiFile.addBefore(it, psiFile.firstChild)
            }
            return
        }

        val find = imports.find { it.textMatches(importStr) } != null
        if (find) {
            return
        }

        if (isRelPath) {
            createCommonElement(project, importStr)?.also {
                psiFile.addAfter(it, imports.last())
            }
            return
        }

        var lastPackageImport: PsiElement? = null
        for (child in imports) {
            if (child.text.startsWith("import 'package:")) {
                lastPackageImport = child
            } else if (lastPackageImport != null) {
                break
            }
        }

        if (lastPackageImport == null) {
            createCommonElement(project, importStr)?.also {
                psiFile.addBefore(it, psiFile.firstChild)
            }
        } else {
            createCommonElement(project, importStr)?.also {
                psiFile.addAfter(it, lastPackageImport)
            }
        }
    }

    /**
     * 添加part导入,[partStr]为导入的内容，如：part 'test.g.dart';。
     * 如果已存在则不会插入
     */
    fun addPartImport(project: Project, psiFile: PsiFile, partStr: String) {
        var lastImportElement: PsiElement? = null
        var lastPartElement: PsiElement? = null
        var existAnyPart = false
        for (child in psiFile.children) {
            if (child is DartPartStatement) {
                existAnyPart = true
                if (child.textMatches(partStr)) {
                    return
                }
            } else if (child is DartImportStatement) {
                lastImportElement = child
            } else if (child !is PsiWhiteSpace && existAnyPart) {
                lastPartElement = child
                break
            }
        }

        createCommonElement(project, partStr)?.also {
            if (lastPartElement != null) {
                psiFile.addAfter(it, lastPartElement)
            } else if (lastImportElement != null) {
                psiFile.addAfter(it, lastImportElement)
            } else {
                psiFile.addBefore(it, psiFile.firstChild)
            }
        }
    }
}
