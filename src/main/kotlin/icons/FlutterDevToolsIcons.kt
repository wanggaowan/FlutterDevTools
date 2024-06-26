package icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * 图片ICON资源加载
 *
 * @author Created by wanggaowan on 2022/5/1 21:44
 */
object FlutterDevToolsIcons {
    // IconLoader.getIcon，只要在同一个路径下放置同名，后缀为_dark的图片，则自动在暗色主题时加载dart图片
    // 比如：/icons/ic_search.svg   /icons/ic_search_dark.svg

    /**
     * 搜索Icon
     */
    @JvmField
    val search: Icon = IconLoader.getIcon("/icons/ic_search.svg", FlutterDevToolsIcons::class.java)

    /**
     * 图片预览方式为列表的Icon
     */
    @JvmField
    val list: Icon = IconLoader.getIcon("/icons/ic_list.svg", FlutterDevToolsIcons::class.java)

    /**
     * 图片预览方式为网格的Icon
     */
    @JvmField
    val grid: Icon = IconLoader.getIcon("/icons/ic_grid.svg", FlutterDevToolsIcons::class.java)

    /**
     * 刷新Icon
     */
    @JvmField
    val refresh: Icon = IconLoader.getIcon("/icons/ic_refresh.svg", FlutterDevToolsIcons::class.java)

    /**
     * 关闭Icon
     */
    @JvmField
    val close: Icon = IconLoader.getIcon("/icons/ic_close.svg", FlutterDevToolsIcons::class.java)

    /**
     * 关闭获取到焦点时的Icon
     */
    @JvmField
    val closeFocus: Icon = IconLoader.getIcon("/icons/ic_close_focus.svg", FlutterDevToolsIcons::class.java)

    @JvmField
    val add: Icon = IconLoader.getIcon("/icons/add.svg", FlutterDevToolsIcons::class.java)

    @JvmField
    val delete: Icon = IconLoader.getIcon("/icons/delete.svg", FlutterDevToolsIcons::class.java)

    @JvmField
    val arbFile: Icon = IconLoader.getIcon("/icons/arb_file2.svg", FlutterDevToolsIcons::class.java)

    @JvmField
    val fileTemplate: Icon = IconLoader.getIcon("/icons/file_template.svg", FlutterDevToolsIcons::class.java)

    @JvmField
    val log: Icon = IconLoader.getIcon("/icons/log.svg", FlutterDevToolsIcons::class.java)

    @JvmField
    val addLog: Icon = IconLoader.getIcon("/icons/edit_log.svg", FlutterDevToolsIcons::class.java)
}
