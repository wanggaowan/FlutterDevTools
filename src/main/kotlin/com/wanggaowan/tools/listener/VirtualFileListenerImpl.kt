package com.wanggaowan.tools.listener

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorEventListener
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.ex.rootDir
import com.wanggaowan.tools.utils.flutter.FlutterCommandLine
import com.wanggaowan.tools.utils.msg.Toast
import io.flutter.sdk.FlutterSdk
import java.util.TimerTask

/**
 * 文件变化监听
 *
 * @author Created by wanggaowan on 2023/2/21 23:19
 */
class VirtualFileListenerImpl : EditorEventListener {

    override fun documentChanged(event: DocumentEvent) {
        super.documentChanged(event)
        ProjectManagerListenerImpl.project?.also {
            Toast.show(it, MessageType.INFO, "文档变更")
        }

    }

    private var timer: java.util.Timer? = null
    private var timerTask: TimerTask? = null

    /**
     * 处理gen-l10n命令
     */
    private fun genL10n(events: MutableList<out VFileEvent>) {
        val project = ProjectManagerListenerImpl.project ?: return
        if (!project.isFlutterProject) {
            return
        }

        var isArbFileChange = false
        for (event in events) {
            if (event is VFileContentChangeEvent) {
                if (event.file.name.endsWith(".arb")) {
                    isArbFileChange = true
                    break
                }
            }
        }

        if (!isArbFileChange) {
            return
        }

        Toast.show(project, MessageType.INFO, "xxxx")
        project.rootDir?.also {
            FlutterSdk.getFlutterSdk(project)?.also { sdk ->
                val commandLine = FlutterCommandLine(sdk, it, FlutterCommandLine.Type.GEN_L10N)
                commandLine.startInConsole(project)
            }
        }
    }
}
