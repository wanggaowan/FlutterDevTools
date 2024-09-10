package com.wanggaowan.tools.ui

import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ImageUtil
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.reflect.KProperty

// Chessboard texture constants
private val CHESSBOARD_COLOR_1 = JBColor(0xCDCDCD, 0x414243)

private val CHESSBOARD_COLOR_2 = JBColor(0xF1F1F1, 0x393A3B)

private fun createTextureAnchor(scaledPatternSize: Int) = Rectangle(0, 0, scaledPatternSize, scaledPatternSize)

private fun createTexturePattern(scaledCellSize: Int): BufferedImage {
    val patternSize = scaledCellSize * 2
    return ImageUtil.createImage(patternSize, patternSize, BufferedImage.TYPE_INT_ARGB).apply {
        with(this.graphics) {
            color = CHESSBOARD_COLOR_1
            fillRect(0, 0, patternSize, patternSize)
            color = CHESSBOARD_COLOR_2
            fillRect(0, scaledCellSize, scaledCellSize, scaledCellSize)
            fillRect(scaledCellSize, 0, scaledCellSize, scaledCellSize)
            dispose()
        }
    }
}

/**
 * 绘制黑白相间的网格背景
 */
class ChessBoardPanel(
    cellSize: Int = 10,
    layoutManager: LayoutManager = BorderLayout())
    : JPanel(layoutManager) {

    private val chessBoardPaint by object {
        private val scaledCellSize = JBUIScale.scale(cellSize)
        private val scaledPatternSize = scaledCellSize * 2

        private var isDarkTheme = !JBColor.isBright()
        private var paint = TexturePaint(createTexturePattern(scaledCellSize), createTextureAnchor(scaledPatternSize))
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Paint {
            if (isDarkTheme == JBColor.isBright()) {
                isDarkTheme = !JBColor.isBright()
                paint = TexturePaint(createTexturePattern(scaledCellSize), createTextureAnchor(scaledPatternSize))
            }
            return paint
        }
    }

    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        with(g as Graphics2D) {
            val oldPaint = paint
            paint = chessBoardPaint
            val insets = insets
            fillRect(insets.left,
                insets.top,
                size.width - insets.right - insets.left,
                size.height - insets.bottom - insets.top)
            paint = oldPaint
        }
        super.paintComponent(g)
    }
}
