package com.wanggaowan.tools.extensions.filetype

import com.intellij.json.JsonFileType
import icons.SdkIcons
import javax.swing.Icon

/**
 * 解析arb文件
 *
 * @author Created by wanggaowan on 2023/7/28 16:34
 */
class ArbFileType : JsonFileType() {

    override fun getName(): String {
        return "ARB"
    }

    override fun getDescription(): String {
        return "Flutter i18n Resource"
    }

    override fun getIcon(): Icon {
        return SdkIcons.arbFile
    }

    override fun getDefaultExtension(): String {
        return "arb"
    }
}
