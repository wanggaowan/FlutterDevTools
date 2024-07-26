package com.wanggaowan.tools.ui

import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.GraphicsUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon
import javax.swing.JButton


/**
 * 图片按钮
 *
 * @author Created by wanggaowan on 2022/6/20 08:44
 */
class ImageButton(icon: Icon? = null, arcSize: Int? = null) :
    JButton(icon) {

    private var mArcSize = scale(7)
    private var mBorderWidth = 0
    private var mBorderColor: Color? = null


    init {
        super.setOpaque(false)
        if (arcSize != null) {
            mArcSize = arcSize
        }
    }

    fun setArcSize(arcSize: Int) {
        if (mArcSize == arcSize) {
            return
        }
        mArcSize = arcSize
        repaint()
    }

    fun setBorderWidth(width: Int) {
        if (mBorderWidth == width) {
            return
        }
        mBorderWidth = width
        repaint()
    }

    fun setBorderColor(color: Color?) {
        if (mBorderColor == null && color == null) {
            return
        }
        mBorderColor = color
        repaint()
    }

    override fun setOpaque(isOpaque: Boolean) {

    }

    override fun getPreferredSize(): Dimension {
        if (isPreferredSizeSet) {
            return super.getPreferredSize()
        }

        val iconSize = icon.iconWidth
        val width = iconSize + mBorderWidth * 2
        val height = iconSize + mBorderWidth * 2
        return Dimension(width, height)
    }

    override fun paint(g2: Graphics) {
        if (!isEnabled) {
            return super.paint(g2)
        }

        val g = g2 as Graphics2D
        val config = GraphicsUtil.setupAAPainting(g)
        val w = g.clipBounds.width
        val h = g.clipBounds.height
        mBorderColor?.also {
            g.paint = it
            g.fillRoundRect(
                0,
                0,
                w,
                h,
                mArcSize,
                mArcSize
            )
        }

        g.paint = background
        val buttonArc = (mArcSize - 1).coerceAtLeast(0)
        g.fillRoundRect(
            mBorderWidth,
            mBorderWidth,
            w - mBorderWidth * 2,
            h - mBorderWidth * 2,
            buttonArc,
            buttonArc
        )
        icon.paintIcon(this, g, (width - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
        config.restore()
    }
}
