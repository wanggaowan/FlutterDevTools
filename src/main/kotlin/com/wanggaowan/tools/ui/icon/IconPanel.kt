package com.wanggaowan.tools.ui.icon

import icons.FlutterDevToolsIcons
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JComponent

/**
 * 将指定icon转化为组件绘制到界面
 *
 * @author Created by wanggaowan on 2024/9/9 下午5:12
 */
class IconPanel(icon: Icon?) : JComponent() {

    constructor(filePath: String?, isFitParent: Boolean = false) : this(null as Icon?) {
        val image = if (filePath == null) null else FlutterDevToolsIcons.getImage(filePath)
        icon = if (isFitParent) {
            AutoScaleIcon(image)
        } else {
            ImageIcon(image)
        }
    }

    var icon: Icon? = icon
        private set(value) {
            field = value
            revalidate()
            repaint()
        }

    override fun paint(g: Graphics) {
        super.paint(g)
        val icon = this.icon ?: return
        val iconWidth = icon.iconWidth
        val iconHeight = icon.iconHeight
        if (iconWidth <= 0 || iconHeight <= 0) {
            return
        }

        val x = ((width - iconWidth) / 2)
        val y = ((height - iconHeight) / 2)
        icon.paintIcon(this, g, x, y)
    }

    fun setIcon(filePath: String?, isFitParent: Boolean = false) {
        val image = if (filePath == null) null else FlutterDevToolsIcons.getImage(filePath)
        icon = if (isFitParent) {
            AutoScaleIcon(image)
        } else {
            ImageIcon(image)
        }
    }
}
