/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.wanggaowan.tools.extensions.codeInsight

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.impl.source.tree.AstBufferUtil
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.DartTokenTypes
import com.jetbrains.lang.dart.psi.DartArguments
import com.jetbrains.lang.dart.psi.DartExpression
import com.jetbrains.lang.dart.psi.DartLiteralExpression
import io.flutter.FlutterBundle
import io.flutter.dart.DartPsiUtil
import io.flutter.editor.ExpressionParsingUtils
import io.flutter.editor.FlutterColors
import io.flutter.editor.FlutterCupertinoColors
import java.awt.Color
import java.util.*
import kotlin.math.min

// 拷贝Flutter插件源码，修复其中下标异常Bug
class FlutterColorProvider : ElementColorProvider {
    override fun getColorFrom(element: PsiElement): Color? {
        // This must return null for non-leaf nodes and any language other than Dart.
        if (element.node.elementType !== DartTokenTypes.IDENTIFIER) return null

        val name = element.text
        if (!(name == "Colors" || name == "CupertinoColors" || name == "Color")) return null

        val refExpr = DartPsiUtil.topmostReferenceExpression(element) ?: return null
        var parent: PsiElement = refExpr.parent ?: return null

        if (parent.node.elementType === DartTokenTypes.ARRAY_ACCESS_EXPRESSION) {
            // Colors.blue[200]
            val code = AstBufferUtil.getTextSkippingWhitespaceComments(parent.node)
            return parseColorText(code.substring(code.indexOf(name) + name.length + 1), name)
        } else if (parent.node.elementType === DartTokenTypes.CALL_EXPRESSION) {
            // foo(Color.fromRGBO(0, 255, 0, 0.5))
            return parseColorElements(parent, refExpr)
        } else if (parent.node.elementType === DartTokenTypes.SIMPLE_TYPE) {
            // const Color.fromARGB(100, 255, 0, 0)
            // parent.getParent().getParent() is a new expr
            parent = DartPsiUtil.getNewExprFromType(parent) ?: return null
            return parseColorElements(parent, refExpr)
        } else {
            // name.equals(refExpr.getFirstChild().getText()) -> Colors.blue
            val idNode = refExpr.firstChild ?: return null
            if (name == idNode.text) {
                val selectorNode = refExpr.lastChild ?: return null
                val code = AstBufferUtil.getTextSkippingWhitespaceComments(selectorNode.node)
                return parseColorText(code, name)
            }
            // refExpr.getLastChild().getText().startsWith("shade") -> Colors.blue.shade200
            val child = refExpr.lastChild ?: return null
            if (child.text.startsWith("shade")) {
                val code = AstBufferUtil.getTextSkippingWhitespaceComments(refExpr.node)
                return parseColorText(code.substring(code.indexOf(name) + name.length + 1), name)
            }
        }
        return null
    }

    private fun parseColorElements(parent: PsiElement, refExpr: PsiElement): Color? {
        val selectorNode = refExpr.lastChild ?: return null
        val selector = selectorNode.text
        val isFromARGB = "fromARGB" == selector
        val isFromRGBO = "fromRGBO" == selector
        if (isFromARGB || isFromRGBO) {
            var code = AstBufferUtil.getTextSkippingWhitespaceComments(parent.node)
            if (code.startsWith("constColor(")) {
                code = code.substring(5)
            }
            return ExpressionParsingUtils.parseColorComponents(code.substring(code.indexOf(selector)),
                "$selector(",
                isFromARGB)
        }
        val args = parent.lastChild
        if (args != null && args.node.elementType === DartTokenTypes.ARGUMENTS) {
            var code = AstBufferUtil.getTextSkippingWhitespaceComments(parent.node)
            if (code.startsWith("constColor(")) {
                code = code.substring(5)
            }
            return ExpressionParsingUtils.parseColor(code)
        }
        return null
    }

    private fun parseColorText(text: String, platform: String): Color? {
        val color = if ("CupertinoColors" == platform) {
            FlutterCupertinoColors.getColor(text)
        } else {
            FlutterColors.getColor(text)
        }
        if (color != null) {
            return color.awtColor
        }
        return null
    }

    override fun setColorTo(element: PsiElement, color: Color) {
        // Not trying to look up Material or Cupertino colors.
        // Unfortunately, there is no way to prevent the color picker from showing (if clicked) for those expressions.
        if (element.text != "Color") return
        val document = PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile)
        val command = Runnable {
            val refExpr = DartPsiUtil.topmostReferenceExpression(element) ?: return@Runnable
            var parent: PsiElement = refExpr.parent ?: return@Runnable
            if (parent.node.elementType === DartTokenTypes.CALL_EXPRESSION) {
                // foo(Color.fromRGBO(0, 255, 0, 0.5))
                replaceColor(parent, refExpr, color)
            } else if (parent.node.elementType === DartTokenTypes.SIMPLE_TYPE) {
                // const Color.fromARGB(100, 255, 0, 0)
                // parent.getParent().getParent() is a new expr
                parent = DartPsiUtil.getNewExprFromType(parent)?:return@Runnable
                replaceColor(parent, refExpr, color)
            }
        }
        CommandProcessor.getInstance()
            .executeCommand(element.project,
                command,
                FlutterBundle.message("change.color.command.text"),
                null,
                document)
    }

    private fun replaceColor(parent: PsiElement, refExpr: PsiElement, color: Color) {
        val selectorNode = refExpr.lastChild ?: return
        val selector = selectorNode.text
        val isFromARGB = "fromARGB" == selector
        val isFromRGBO = "fromRGBO" == selector
        val args = parent.lastChild
        if (args == null || args.node.elementType !== DartTokenTypes.ARGUMENTS) return
        val list = (args as DartArguments).argumentList ?: return
        if (isFromARGB) {
            replaceARGB(list.expressionList, color)
        } else if (isFromRGBO) {
            replaceRGBO(list.expressionList, color)
        } else {
            replaceArg(list.expressionList, color)
        }
    }

    private fun replaceARGB(args: List<DartExpression>, color: Color) {
        if (args.size != 4) return
        val colors = listOf(color.alpha, color.red, color.green, color.blue)
        for (i in args.indices) {
            replaceInt(args[i], colors[i])
        }
    }

    private fun replaceRGBO(args: List<DartExpression>, color: Color) {
        if (args.size != 4) return
        val colors = listOf(color.red, color.green, color.blue)
        for (i in colors.indices) {
            replaceInt(args[i], colors[i])
        }
        replaceDouble(args[3], color.alpha.toDouble() / 255.0)
    }

    private fun replaceArg(args: List<DartExpression>, color: Color) {
        if (args.size != 1) return
        replaceInt(args[0], color.rgb)
    }

    private fun replaceInt(expr: DartExpression, value: Int) {
        if (expr is DartLiteralExpression) {
            val source = expr.getText()
            val number: String = source.substring(min(2, source.length))
            // Preserve case of 0x separate from hex string, eg. 0xFFEE00DD.
            val isHex = source.startsWith("0x") || source.startsWith("0X")
            val isUpper = isHex && number.uppercase(Locale.getDefault()) == number
            val newValue = if (isHex) Integer.toHexString(value) else value.toString()
            val num = if (isUpper) newValue.uppercase(Locale.getDefault()) else newValue
            val hex = if (isHex) source.substring(0, 2) + num else num
            val factory = PsiFileFactoryImpl(expr.getManager())
            val newPsi =
                factory.createElementFromText(hex,
                    DartLanguage.INSTANCE,
                    DartTokenTypes.LITERAL_EXPRESSION,
                    expr.getContext())
            if (newPsi != null) {
                expr.replace(newPsi)
            }
        }
    }

    private fun replaceDouble(expr: DartExpression, value: Double) {
        if (expr is DartLiteralExpression) {
            val factory = PsiFileFactoryImpl(expr.getManager())
            val number = value.toString()
            val newPsi =
                factory.createElementFromText(number,
                    DartLanguage.INSTANCE,
                    DartTokenTypes.LITERAL_EXPRESSION,
                    expr.getContext())
            if (newPsi != null) {
                expr.replace(newPsi)
            }
        }
    }
}
