package com.wanggaowan.tools.actions.image

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.wanggaowan.tools.Sdk
import com.wanggaowan.tools.settings.PluginSettings
import org.jetbrains.kotlin.konan.file.File

/**
 * 导入不同分辨率相同图片资源(右键菜单)
 *
 * @author Created by wanggaowan on 2022/7/12 09:42
 */
class ImportSameImageResAction2 : ImportSameImageResAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }

        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (virtualFiles.isNullOrEmpty() || virtualFiles.size > 1) {
            e.presentation.isVisible = false
            return
        }

        val file = virtualFiles[0]
        if (!file.isDirectory) {
            e.presentation.isVisible = false
            return
        }

        val names = file.path.split(File.separator)
        val regex = """^\d+(\.\d+)?x$""".toRegex()
        val finds = names.filter { it.matches(regex) }
        if (finds.isNotEmpty()) {
            e.presentation.isVisible = false
            return
        }

        val pubRoot = Sdk.getInstance(project).getPubRoot(file)
        if (pubRoot == null) {
            e.presentation.isVisible = false
            return
        }

        val imageRootPath = "${pubRoot.path}/${PluginSettings.getImagesFileDir(project)}"
        if (!file.path.startsWith(imageRootPath)) {
            e.presentation.isVisible = false
            return
        }

        importToFolder = file
        e.presentation.isVisible = true
    }
}
