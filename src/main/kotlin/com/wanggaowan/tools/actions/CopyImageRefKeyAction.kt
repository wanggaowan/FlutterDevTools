package com.wanggaowan.tools.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.StringUtils
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.basePath
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

/**
 * 选中图片，复制引用Key
 *
 * @author Created by wanggaowan on 2023/10/8 16:10
 */
class CopyImageRefKeyAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (virtualFiles.isNullOrEmpty() || virtualFiles.size > 1) {
            e.presentation.isVisible = false
            return
        }

        if (!XUtils.isImage(virtualFiles[0].name)) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true

    }

    override fun actionPerformed(e: AnActionEvent) {
        val module = e.getData(LangDataKeys.MODULE) ?: return
        val basePath = module.basePath ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val isExampleModule = virtualFile.path.startsWith("$basePath/example/")

        val project = module.project


        // 图片资源在项目中的相对路径
        val imagesRelDirPath: String
        // 生成图片资源引用文件类名称
        val imageRefClassName: String
        if (isExampleModule) {
            imagesRelDirPath = "example/" + PluginSettings.getExampleImagesFileDir(project)
            imageRefClassName = PluginSettings.getExampleImagesRefClassName(project)
        } else {
            imagesRelDirPath = PluginSettings.getImagesFileDir(project)
            imageRefClassName = PluginSettings.getImagesRefClassName(project)
        }

        val dirName = virtualFile.parent.name
        var path = if (dirName == "1.5x" || dirName == "2.0x" || dirName == "3.0x" || dirName == "4.0x") {
            virtualFile.path.replace("$dirName/", "")
        } else {
            virtualFile.path
        }
        path = path.replace("$basePath/$imagesRelDirPath/", "")
        path = imageRefClassName + "." + getPropertyKey(path)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(TextTransferable(path), null)
        NotificationUtils.showBalloonMsg(project, "已复制到剪切板", NotificationType.INFORMATION)
    }

    private fun getPropertyKey(value: String): String {
        return StringUtils.lowerCamelCase(
            value.substring(0, value.lastIndexOf("."))
                .replace("/", "_")
                .replace("-", "_")
                .replace("@", ""), false
        )
    }
}

/**
 * 复制的文本数据
 */
class TextTransferable(private val content: String) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return arrayOf(DataFlavor.stringFlavor)
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
        return DataFlavor.stringFlavor.equals(flavor)
    }

    override fun getTransferData(flavor: DataFlavor?): Any {
        return if (!DataFlavor.stringFlavor.equals(flavor)) {
            throw UnsupportedFlavorException(flavor)
        } else {
            content
        }
    }
}
