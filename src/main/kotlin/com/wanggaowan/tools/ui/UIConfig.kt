package com.wanggaowan.tools.ui

import com.intellij.ui.Gray
import com.intellij.util.ui.StartupUiUtil
import java.awt.Color

/**
 * UI配置文件
 *
 * @author Created by wanggaowan on 2023/2/12 20:55
 */
@Suppress("UseJBColor")
object UIConfig {
    val isDarkTheme:Boolean
        get() = StartupUiUtil.isUnderDarcula()

    private val LINE_COLOR = Gray._209
    private val LINE_COLOR_DARK = Gray._50

    private val MOUSE_ENTER_COLOR = Gray._223
    private val MOUSE_ENTER_COLOR_DARK = Color(76, 80, 82)

    // 用于透明Icon使用
    private val MOUSE_ENTER_COLOR2 = Color(191, 197, 200)
    private val MOUSE_ENTER_COLOR_DARK2 = Color(98, 106, 110)

    private val MOUSE_PRESS_COLOR = Gray._207
    private val MOUSE_PRESS_COLOR_DARK = Color(92, 97, 100)

    // 用于透明Icon使用
    private val MOUSE_PRESS_COLOR2 = Color(162, 166, 169)
    private val MOUSE_PRESS_COLOR_DARK2 = Color(82, 87, 91)

    private val INPUT_FOCUS_COLOR = Color(71, 135, 201)

    private val INPUT_UN_FOCUS_COLOR = Gray._196
    private val INPUT_UN_FOCUS_COLOR_DARK = Gray._100

    private val IMAGE_TITLE_BG_COLOR = Gray._252
    private val IMAGE_TITLE_BG_COLOR_DARK = Color(49, 52, 53)

    val TRANSPARENT = Color(0, 0, 0, 0)

    fun getLineColor(): Color {
        if (isDarkTheme) {
            return LINE_COLOR_DARK
        }

        return LINE_COLOR
    }

    fun getMouseEnterColor(): Color {
        if (isDarkTheme) {
            return MOUSE_ENTER_COLOR_DARK
        }

        return MOUSE_ENTER_COLOR
    }

    fun getMouseEnterColor2(): Color {
        if (isDarkTheme) {
            return MOUSE_ENTER_COLOR_DARK2
        }

        return MOUSE_ENTER_COLOR2
    }

    fun getMousePressColor2(): Color {
        if (isDarkTheme) {
            return MOUSE_PRESS_COLOR_DARK2
        }

        return MOUSE_PRESS_COLOR2
    }

    fun getMousePressColor(): Color {
        if (isDarkTheme) {
            return MOUSE_PRESS_COLOR_DARK
        }

        return MOUSE_PRESS_COLOR
    }

    fun getInputFocusColor(): Color {
        if (isDarkTheme) {
            return INPUT_FOCUS_COLOR
        }

        return INPUT_FOCUS_COLOR
    }

    fun getInputUnFocusColor(): Color {
        if (isDarkTheme) {
            return INPUT_UN_FOCUS_COLOR_DARK
        }

        return INPUT_UN_FOCUS_COLOR
    }

    fun getImageTitleBgColor(): Color {
        if (isDarkTheme) {
            return IMAGE_TITLE_BG_COLOR_DARK
        }

        return IMAGE_TITLE_BG_COLOR
    }
}
