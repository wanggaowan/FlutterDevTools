package com.wanggaowan.tools.listener

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener.ActionOnSave
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.wanggaowan.tools.Sdk
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.flutter.FlutterCommandLine
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

private val fileDocumentManager = lazy { FileDocumentManager.getInstance() }
private val documentListener = lazy {
    object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            val file = fileDocumentManager.value.getFile(event.document)
            if (file != null) {
                needDoGenL10nMap[file.path] = true
            }
        }
    }
}
private val coroutineScope = lazy { CoroutineScope(Dispatchers.Default) }
private val jobMap: MutableMap<String, Job> = mutableMapOf()

// 是否需要执行 gen-l10n命令
private val needDoGenL10nMap: MutableMap<String, Boolean> = mutableMapOf()

/**
 * 监听自动执行gen-l10n命令需要的条件，一旦达成，则自动执行
 *
 * @author Created by wanggaowan on 2023/2/21 21:26
 */
class GenL10nListener : ActionOnSave(), FileEditorManagerListener {
    override fun isEnabledForProject(project: Project): Boolean {
        return project.isFlutterProject
    }

    // 处理文件保存逻辑
    override fun processDocuments(project: Project, documents: Array<Document>) {
        coroutineScope.value.launch {
            for (document in documents) {
                val file = fileDocumentManager.value.getFile(document)
                if (file != null && file.name.endsWith(".arb")) {
                    val pubRoot = Sdk.getInstance(project).getPubRoot(file) ?: continue
                    doGenL10n(project, pubRoot)
                }
            }
        }
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        super.fileOpened(source, file)
        if (!file.name.endsWith(".arb")) {
            return
        }

        if (!source.project.isFlutterProject) {
            return
        }

        fileDocumentManager.value.getDocument(file)?.addDocumentListener(documentListener.value) {

        }
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        super.fileClosed(source, file)
        if (!file.name.endsWith(".arb")) {
            return
        }

        val pubRoot = Sdk.getInstance(source.project).getPubRoot(file) ?: return
        fileDocumentManager.value.getDocument(file)?.removeDocumentListener(documentListener.value)
        if (needDoGenL10nMap[file.path] != true) {
            return
        }

        needDoGenL10nMap.remove(file.path)
        fileDocumentManager.value.saveAllDocuments()
        doGenL10n(source.project, pubRoot)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        super.selectionChanged(event)
        val file: VirtualFile = event.oldFile ?: return
        val path = file.path
        if (needDoGenL10nMap[path] == true) {
            needDoGenL10nMap.remove(path)
            val project = event.manager.project
            val pubRoot = Sdk.getInstance(project).getPubRoot(path) ?: return
            // 保存文档与执行gen-l10n命令均不需要write action，gen-l10n命令会异步执行，不会阻塞当前线程
            fileDocumentManager.value.saveAllDocuments()
            doGenL10n(project, pubRoot)
        }
    }

    private fun doGenL10n(project: Project, pubRoot: PubRoot) {
        val path = pubRoot.path
        var job = jobMap[path]
        // 仅仅最后一次文本变更时执行,如果多个项目都有文本变化，则只处理最后一个项目
        if (job?.isActive == true) {
            job.cancel()
        }

        job = coroutineScope.value.launch {
            delay(500.milliseconds)
            val workDir = pubRoot.root
            FlutterSdk.getFlutterSdk(project)?.also { sdk ->
                val commandLine = FlutterCommandLine(sdk, workDir, FlutterCommandLine.Type.GEN_L10N)
                commandLine.start()
            }
        }

        jobMap[path] = job
    }

}
