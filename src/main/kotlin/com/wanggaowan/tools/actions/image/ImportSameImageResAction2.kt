package com.wanggaowan.tools.actions.image

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.application
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.*
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.flutterModules
import com.wanggaowan.tools.utils.ex.isFlutterProject
import io.flutter.utils.ProgressHelper
import org.jetbrains.kotlin.konan.file.File

private val LOG = logger<ImportSameImageResUtils>()

/**
 * 导入不同分辨率相同图片资源(右键菜单)
 *
 * @author Created by wanggaowan on 2022/7/12 09:42
 */
class ImportSameImageResAction2 : ImportSameImageResAction() {

    override fun update(e: AnActionEvent) {
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
        importToFolder = file
        e.presentation.isVisible = true
    }
}
