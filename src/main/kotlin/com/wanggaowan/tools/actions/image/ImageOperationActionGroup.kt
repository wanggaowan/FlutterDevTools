package com.wanggaowan.tools.actions.image

import com.intellij.openapi.actionSystem.*
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.isFlutterProject

/**
 * 图片操作分组
 *
 * @author Created by wanggaowan on 2023/8/31 14:51
 */
class ImageOperationActionGroup : DefaultActionGroup() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val module = e.getData(LangDataKeys.MODULE)
        if (module == null) {
            e.presentation.isVisible = false
            return
        }

        if (!module.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (virtualFiles.isNullOrEmpty()) {
            e.presentation.isVisible = false
            return
        }

        val basePath = module.basePath
        val imageDir = PluginSettings.getImagesFileDir(module.project)
        if (!virtualFiles[0].path.startsWith("$basePath/$imageDir") && !virtualFiles[0].path.startsWith("$basePath/example/$imageDir")) {
            e.presentation.isVisible = false
            return
        }

        for (file in virtualFiles) {
            if (file.isDirectory) {
                e.presentation.isVisible = false
                return
            }
        }

        e.presentation.isVisible = true
    }
}
