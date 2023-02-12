package com.wanggaowan.tools.ui

import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon
import javax.swing.JPanel


/**
 * 图片按钮
 *
 * @author Created by wanggaowan on 2022/6/20 08:44
 */
class ImageButton(icon: Icon? = null) : JPanel() {

    var icon: Icon? = null
        set(value) {
            field = value
            repaint()
        }

    /**
     * 圆角半径
     */
    var radius: Int = 0
        set(value) {
            field = value
            repaint()
        }

    /**
     * 内径，相当于设置一个EmptyBorder，四周边距设置为padding值
     */
    var padding: Int = 0
        set(value) {
            field = value
            repaint()
        }

    init {
        this.icon = icon
        preferredSize = Dimension(icon?.iconWidth ?: 0, icon?.iconHeight ?: 0)
    }

    override fun paint(g: Graphics) {
        // g.clearRect(0, 0, width, height)
        super.paint(g)
        if (g is Graphics2D) {
            // 消除文字锯齿
            // g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            // 消除画图锯齿
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        }

        if (background != null && background.alpha > 0) {
            g.color = background
            g.fillRoundRect(padding, padding, width - padding * 2, height - padding * 2, radius, radius)
        }

        icon?.let {
            val width = width - padding * 2
            val height = height - padding * 2
            val imgWidth = it.iconWidth
            val imgHeight = it.iconHeight
            if (imgWidth > 0 && imgHeight > 0 && width > 0 && height > 0) {
                val x: Int = (width - imgWidth) / 2 + padding
                val y: Int = (height - imgHeight) / 2 + padding
                it.paintIcon(this, g, x, y)
            }
        }
    }
}
