package icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * 图片ICON资源加载
 *
 * @author Created by wanggaowan on 2022/5/1 21:44
 */
object SdkIcons {
    // IconLoader.getIcon，只要在同一个路径下放置同名，后缀为_dark的图片，则自动在暗色主题时加载dart图片
    // 比如：/icons/ic_search.svg   /icons/ic_search_dark.svg

    /**
     * 搜索Icon
     */
    @JvmStatic
    val search: Icon = IconLoader.getIcon("/icons/ic_search.svg", SdkIcons::class.java)

    /**
     * 图片预览方式为列表的Icon
     */
    @JvmStatic
    val list: Icon = IconLoader.getIcon("/icons/ic_list.svg", SdkIcons::class.java)

    /**
     * 图片预览方式为网格的Icon
     */
    @JvmStatic
    val grid: Icon = IconLoader.getIcon("/icons/ic_grid.svg", SdkIcons::class.java)

    /**
     * 刷新Icon
     */
    @JvmStatic
    val refresh: Icon = IconLoader.getIcon("/icons/ic_refresh.svg", SdkIcons::class.java)

    /**
     * 关闭Icon
     */
    @JvmStatic
    val close: Icon = IconLoader.getIcon("/icons/ic_close.svg", SdkIcons::class.java)

    /**
     * 关闭获取到焦点时的Icon
     */
    @JvmStatic
    val closeFocus: Icon = IconLoader.getIcon("/icons/ic_close_focus.svg", SdkIcons::class.java)

    @JvmStatic
    val add: Icon = IconLoader.getIcon("/icons/add.svg", SdkIcons::class.java)

    @JvmStatic
    val delete: Icon = IconLoader.getIcon("/icons/delete.svg", SdkIcons::class.java)

    @JvmStatic
    val arbFile: Icon = IconLoader.getIcon("/icons/arb_file.svg", SdkIcons::class.java)

    @JvmStatic
    val fileTemplate: Icon = IconLoader.getIcon("/icons/file_template.svg", SdkIcons::class.java)
}
