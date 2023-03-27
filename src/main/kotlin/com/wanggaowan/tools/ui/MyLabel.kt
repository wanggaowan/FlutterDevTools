package com.wanggaowan.tools.ui

import com.intellij.ui.components.JBLabel
import java.awt.Font
import java.awt.FontMetrics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * 展示文本标签，增加多种文本超出显示宽度时的截断策略
 *
 * @author Created by wanggaowan on 2023/3/27 08:44
 */
class MyLabel(text: String = "") : JBLabel() {
    /**
     * 文本超出时缩略模式
     */
    var ellipsize: TruncateAt = TruncateAt.END
        set(value) {
            if (field == value) {
                return
            }

            field = value
            setMapText(originalText)
        }

    /**
     * 文本展示缩略模式时，缩略文本内容
     */
    var ellipsizeText: String = "..."
        set(value) {
            if (field == value) {
                return
            }

            field = value
            setMapText(originalText)
        }

    /**
     * 计算文本是否超出可显示宽度采用的计算规则。
     *
     * 此值为false时只测量一个字符的宽度，然后用总字符数相乘得到最终字符总宽度。
     *
     * 此值为true时遍历测量每个字符，逐个字符进行裁剪判断截断后文本是否超出可显示宽度
     */
    var strictMode = false
        set(value) {
            if (field == value) {
                return
            }

            field = value
            setMapText(originalText)
        }

    /**
     * 原始的未经裁剪的文本内容
     */
    var originalText: String?
        private set

    /**
     * 原始的未经裁剪的文本内容宽度
     */
    var originalTextWidth = 0
        private set

    private var oneCharWidth = 0
    private var fontMetrics: FontMetrics?
    private var ellipsisWidth = 0
    private var mInit = false

    init {
        mInit = true
        originalText = text
        fontMetrics = getFontMetrics(font)
        calculateWidths()
        setMapText(originalText)
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                setMapText(originalText)
            }
        })
    }

    private fun setMapText(text: String?) {
        if (oneCharWidth <= 0 || text.isNullOrEmpty()) {
            super.setText(text)
            return
        }

        var width = width
        val insetsHorizontal = insets.left + insets.right
        val borderInsets = border?.getBorderInsets(this)
        val borderHorizontal = if (borderInsets == null) 0 else borderInsets.left + borderInsets.right
        width -= (insetsHorizontal + borderHorizontal)
        if (width <= 0) {
            if (preferredSize != null) {
                width = preferredSize.width - (insetsHorizontal + borderHorizontal)
            }

            if (width <= 0) {
                return
            }
        }

        if (originalTextWidth <= width) {
            super.setText(text)
            return
        }

        if (strictMode) {
            setMapTextStrictMode(width, text)
            return
        }

        val maxChartCount = (width - ellipsisWidth) / oneCharWidth
        if (maxChartCount <= 0) {
            super.setText(ellipsizeText)
            return
        }

        if (ellipsize == TruncateAt.END) {
            if (maxChartCount > text.length) {
                super.setText(text)
            } else {
                super.setText(text.substring(0, maxChartCount) + ellipsizeText)
            }
        } else if (ellipsize == TruncateAt.START) {
            if (maxChartCount > text.length) {
                super.setText(text)
            } else {
                super.setText(ellipsizeText + text.substring(text.length - maxChartCount))
            }
        } else if (maxChartCount > text.length) {
            super.setText(text)
        } else {
            val half = maxChartCount / 2
            if (half == 0) {
                super.setText(text.substring(0, maxChartCount) + ellipsizeText)
            } else {
                super.setText(text.substring(0, half) + ellipsizeText + text.substring(text.length - half))
            }
        }
    }

    private fun setMapTextStrictMode(width: Int, text: String) {
        val textMaxWidth = width - ellipsisWidth
        if (textMaxWidth <= 0) {
            super.setText(ellipsizeText)
            return
        }

        val fontMetrics = fontMetrics
        if (fontMetrics == null) {
            super.setText(text)
            return
        }

        var newText = text
        when (ellipsize) {
            TruncateAt.END -> {
                do {
                    newText = newText.substring(0, newText.length - 1)
                } while (newText.isNotEmpty() && fontMetrics.stringWidth(newText) > textMaxWidth)
                super.setText(newText)
            }

            TruncateAt.START -> {
                do {
                    newText = newText.substring(1)
                } while (newText.isNotEmpty() && fontMetrics.stringWidth(newText) > textMaxWidth)
                super.setText(newText)
            }

            else -> {
                if (newText.length <= 1) {
                    super.setText(ellipsizeText)
                    return
                }

                val half = newText.length / 2
                var newText2 = newText.substring(newText.length - half)
                newText = newText.substring(0, half)
                var isStart = true
                do {
                    if (isStart) {
                        if (newText.isNotEmpty()) {
                            newText = newText.substring(0, newText.length - 1)
                        }
                    } else {
                        if (newText2.isNotEmpty()) {
                            newText2 = newText2.substring(1)
                        }
                    }
                    isStart = !isStart
                } while ((newText2.isNotEmpty() || newText.isNotEmpty()) &&
                    (fontMetrics.stringWidth(newText) + fontMetrics.stringWidth(newText2)) > textMaxWidth
                )
                super.setText(newText + ellipsizeText + newText2)
            }
        }
    }

    override fun setText(text: String?) {
        if (text == originalText) {
            return
        }

        originalText = text
        if (!mInit) {
            return
        }
        calculateWidths()
        setMapText(originalText)
    }

    override fun setFont(font: Font) {
        super.setFont(font)
        if (!mInit) {
            return
        }

        fontMetrics = getFontMetrics(getFont())
        calculateWidths()
        setMapText(originalText)
    }

    private fun calculateWidths() {
        oneCharWidth = (fontMetrics?.stringWidth("Aa") ?: 0) / 2

        val text = originalText
        originalTextWidth = if (text.isNullOrEmpty()) {
            0
        } else {
            fontMetrics?.stringWidth(text) ?: 0
        }

        val ellipsis = ellipsizeText
        ellipsisWidth = if (ellipsis.isEmpty()) {
            0
        } else {
            fontMetrics?.stringWidth(ellipsis) ?: 0
        }
    }

    enum class TruncateAt {
        START, MIDDLE, END
    }
}
