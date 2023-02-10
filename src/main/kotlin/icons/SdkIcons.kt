package icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * 图片ICON资源加载
 *
 * @author Created by wanggaowan on 2022/5/1 21:44
 */
object SdkIcons {
    var isDarkTheme = false

    @JvmField
    val defaultIcon: Icon = IconLoader.getIcon("/icons/flutter.png", SdkIcons::class.java)

    @JvmField
    val defaultIconGrey: Icon = IconLoader.getIcon("/icons/default_icon_grey.svg", SdkIcons::class.java)

    @JvmStatic
    val search: Icon
        get() {
            return if (isDarkTheme) {
                IconLoader.getIcon("/icons/ic_search_dark.svg", SdkIcons::class.java)
            } else {
                IconLoader.getIcon("/icons/ic_search.svg", SdkIcons::class.java)
            }
        }

    @JvmStatic
    val list: Icon
        get() {
            return if (isDarkTheme) {
                IconLoader.getIcon("/icons/ic_list_dark.svg", SdkIcons::class.java)
            } else {
                IconLoader.getIcon("/icons/ic_list.svg", SdkIcons::class.java)
            }
        }

    @JvmStatic
    val grid: Icon
        get() {
            return if (isDarkTheme) {
                IconLoader.getIcon("/icons/ic_grid_dark.svg", SdkIcons::class.java)
            } else {
                IconLoader.getIcon("/icons/ic_grid.svg", SdkIcons::class.java)
            }
        }

    @JvmStatic
    val refresh: Icon
        get() {
            return if (isDarkTheme) {
                IconLoader.getIcon("/icons/ic_refresh_dark.svg", SdkIcons::class.java)
            } else {
                IconLoader.getIcon("/icons/ic_refresh.svg", SdkIcons::class.java)
            }
        }

    @JvmStatic
    val close: Icon
        get() {
            return if (isDarkTheme) {
                IconLoader.getIcon("/icons/ic_close_dark.svg", SdkIcons::class.java)
            } else {
                IconLoader.getIcon("/icons/ic_close.svg", SdkIcons::class.java)
            }
        }

    @JvmStatic
    val closeFocus: Icon
        get() {
            return if (isDarkTheme) {
                IconLoader.getIcon("/icons/ic_close_focus_dark.svg", SdkIcons::class.java)
            } else {
                IconLoader.getIcon("/icons/ic_close_focus.svg", SdkIcons::class.java)
            }
        }

}
