package com.wanggaowan.tools.ui

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color

/**
 * UI颜色文件
 *
 * @author Created by wanggaowan on 2023/2/12 20:55
 */
object UIColor {
    // 控件设置JBColor后，可以自动根据当前主题来采用对应颜色
    /**
     * 分割线颜色
     */
    val LINE_COLOR: Color = UIUtil.getBoundsColor()

    val TRANSPARENT = JBColor("transparent", Color(0, 0, 0, 0))
}
