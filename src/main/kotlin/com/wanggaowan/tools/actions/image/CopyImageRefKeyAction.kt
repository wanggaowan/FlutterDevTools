package com.wanggaowan.tools.actions.image

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.wanggaowan.tools.utils.NotificationUtils
import com.wanggaowan.tools.utils.XUtils
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
        val project = e.getData(LangDataKeys.PROJECT) ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val parent = virtualFile.parent ?: return
        val referenceKey = XUtils.imageFileToImageKey(project, parent, virtualFile.name, true) ?: return
        Toolkit.getDefaultToolkit().systemClipboard.setContents(TextTransferable(referenceKey), null)
        NotificationUtils.showBalloonMsg(project, "已复制到剪切板", NotificationType.INFORMATION)
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
