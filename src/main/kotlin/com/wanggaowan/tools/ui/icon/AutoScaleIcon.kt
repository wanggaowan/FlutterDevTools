package com.wanggaowan.tools.ui.icon

import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import javax.swing.Icon

/**
 * 将指定[Image]自动按照宽高比缩放至父组件尺寸内的最大图形
 *
 * @author Created by wanggaowan on 2024/9/9 下午4:27
 */
class AutoScaleIcon(
    val image: Image?
) : Icon {
    private val width: Int = image?.getWidth(null) ?: 0
    private val height: Int = image?.getHeight(null) ?: 0

    override fun getIconHeight(): Int = height

    override fun getIconWidth(): Int = width

    override fun paintIcon(c: Component, g: Graphics, x1: Int, y1: Int) {
        if (image == null) {
            return
        }

        val cWidth = c.width
        val cHeight = c.height
        if (width > 0 && height > 0 && cWidth > 0 && cHeight > 0) {
            val x: Int
            val y: Int
            val drawWidth: Int
            val drawHeight: Int
            if (width <= cWidth && height <= cHeight) {
                val scale = (width * 1f / cWidth).coerceAtLeast(height * 1f / cHeight)
                drawWidth = (width / scale).toInt()
                drawHeight = (height / scale).toInt()
                x = (cWidth - drawWidth) / 2
                y = (cHeight - drawHeight) / 2
            } else {
                val scale = (width * 1f / cWidth).coerceAtLeast(height * 1f / cHeight)
                drawWidth = (width / scale).toInt()
                drawHeight = (height / scale).toInt()
                x = (cWidth - drawWidth) / 2
                y = (cHeight - drawHeight) / 2
            }

            g.drawImage(image, x, y, drawWidth, drawHeight, null)
        }
    }
}
