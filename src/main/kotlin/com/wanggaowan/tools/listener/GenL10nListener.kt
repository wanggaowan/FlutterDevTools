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
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.ex.rootDir
import com.wanggaowan.tools.utils.flutter.FlutterCommandLine
import io.flutter.sdk.FlutterSdk
import kotlinx.coroutines.*

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
    override fun processDocuments(project: Project, documents: Array<out Document>) {
        var isArbFileSave = false
        for (document in documents) {
            val file = fileDocumentManager.value.getFile(document)
            if (file != null && file.name.endsWith(".arb")) {
                isArbFileSave = true
                break
            }
        }

        if (!isArbFileSave) {
            return
        }

        job?.cancel()
        job = null
        doGenL10n(project)
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        super.fileOpened(source, file)
        if (ProjectManagerListenerImpl.project?.isFlutterProject != true) {
            return
        }

        if (!file.name.endsWith(".arb")) {
            return
        }

        val document = fileDocumentManager.value.getDocument(file) ?: return
        document.addDocumentListener(documentListener.value)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        super.fileClosed(source, file)
        if (ProjectManagerListenerImpl.project?.isFlutterProject != true) {
            return
        }

        if (!file.name.endsWith(".arb")) {
            return
        }

        fileDocumentManager.value.getDocument(file)?.removeDocumentListener(documentListener.value)
        if (mNeedDoGenL10n) {
            // 保存所有文件，触发ActionOnSave相关方法，不能直接执行doGenL10n，
            // 因为arb文件编辑的数据可能还只是存在缓存中，直接执行doGenL10n读取不到最新数据
            fileDocumentManager.value.saveAllDocuments()
            job = coroutineScope.value.launch {
                delay(1000)
                if (mNeedDoGenL10n) {
                    doGenL10n()
                }
            }
        }
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        super.selectionChanged(event)
        if (mNeedDoGenL10n) {
            // 保存所有文件，触发ActionOnSave相关方法，不能直接执行doGenL10n，
            // 因为arb文件编辑的数据可能还只是存在缓存中，直接执行doGenL10n读取不到最新数据
            fileDocumentManager.value.saveAllDocuments()
            job = coroutineScope.value.launch {
                delay(1000)
                if (mNeedDoGenL10n) {
                    doGenL10n()
                }
            }
        }
    }

    private fun doGenL10n(project: Project? = null) {
        mNeedDoGenL10n = false
        val project2 = project ?: ProjectManagerListenerImpl.project ?: return
        project2.rootDir?.also {
            FlutterSdk.getFlutterSdk(project2)?.also { sdk ->
                val commandLine = FlutterCommandLine(sdk, it, FlutterCommandLine.Type.GEN_L10N)
                commandLine.start()
            }
        }
    }

    companion object {
        private val fileDocumentManager = lazy { FileDocumentManager.getInstance() }

        // 是否需要执行 gen-l10n命令
        private var mNeedDoGenL10n = false

        private val documentListener = lazy {
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    mNeedDoGenL10n = true
                }
            }
        }

        private val coroutineScope = lazy { CoroutineScope(Dispatchers.Default) }
        private var job: Job? = null
    }
}
