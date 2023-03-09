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
import com.wanggaowan.tools.utils.ex.findChild
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.ex.project
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
        // 是否是example项目发生变化
        var isExample = false
        // 是否是根项目发生变化
        var isProject = false
        for (document in documents) {
            val file = fileDocumentManager.value.getFile(document)
            if (file != null && file.name.endsWith(".arb")) {
                isArbFileSave = true
                val example = file.path.contains("/example/")
                if (!isExample) {
                    isExample = example
                }

                if (!isProject) {
                    isProject = !example
                }
            }
        }

        if (!isArbFileSave) {
            return
        }

        if (isProject) {
            job?.cancel()
            job = null
            doGenL10n(project)
        }

        if (isExample) {
            jobForExample?.cancel()
            jobForExample = null
            doGenL10n(project, true)
        }
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        super.fileOpened(source, file)
        if (!source.project.isFlutterProject) {
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
        if (!source.project.isFlutterProject) {
            return
        }

        if (!file.name.endsWith(".arb")) {
            return
        }

        fileDocumentManager.value.getDocument(file)?.removeDocumentListener(documentListener.value)
        delayDoGenL10n(source.project, file.path.contains("/example/"))
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        super.selectionChanged(event)
        var file = event.newFile
        if (file == null) {
            file = event.oldFile
        }

        if (file == null) {
            return
        }

        val project = file.project ?: return
        delayDoGenL10n(project, file.path.contains("/example/"))
    }

    private fun delayDoGenL10n(project: Project?, isExample: Boolean = false) {
        if (isExample) {
            if (mNeedDoGenL10nForExample) {
                // 保存所有文件，触发ActionOnSave相关方法，不能直接执行doGenL10n，
                // 因为arb文件编辑的数据可能还只是存在缓存中，直接执行doGenL10n读取不到最新数据
                fileDocumentManager.value.saveAllDocuments()

                // 以下方法防止ActionOnSave未能触发时执行
                jobForExample = coroutineScope.value.launch {
                    delay(1000)
                    if (mNeedDoGenL10nForExample) {
                        doGenL10n(project, true)
                    }
                }
            }
            return
        }

        if (mNeedDoGenL10n) {
            // 保存所有文件，触发ActionOnSave相关方法，不能直接执行doGenL10n，
            // 因为arb文件编辑的数据可能还只是存在缓存中，直接执行doGenL10n读取不到最新数据
            fileDocumentManager.value.saveAllDocuments()

            // 以下方法防止ActionOnSave未能触发时执行
            job = coroutineScope.value.launch {
                delay(1000)
                if (mNeedDoGenL10n) {
                    doGenL10n(project)
                }
            }
        }
    }

    private fun doGenL10n(project: Project?, isExample: Boolean = false) {
        if (isExample) {
            mNeedDoGenL10nForExample = false
            val project2 = project ?: return
            project2.findChild("example")?.also {
                FlutterSdk.getFlutterSdk(project2)?.also { sdk ->
                    val commandLine = FlutterCommandLine(sdk, it, FlutterCommandLine.Type.GEN_L10N)
                    commandLine.start()
                }
            }
            return
        }

        mNeedDoGenL10n = false
        val project2 = project ?: return
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
        private var mNeedDoGenL10nForExample = false

        private val documentListener = lazy {
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    val file = fileDocumentManager.value.getFile(event.document)
                    if (file != null && file.path.contains("/example/")) {
                        mNeedDoGenL10nForExample = true
                    } else {
                        mNeedDoGenL10n = true
                    }
                }
            }
        }

        private val coroutineScope = lazy { CoroutineScope(Dispatchers.Default) }
        private var job: Job? = null
        private var jobForExample: Job? = null
    }
}
