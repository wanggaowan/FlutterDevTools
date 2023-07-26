package com.wanggaowan.tools.listener

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener.ActionOnSave
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.findChild
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.ex.rootDir
import com.wanggaowan.tools.utils.flutter.FlutterCommandLine
import io.flutter.sdk.FlutterSdk
import kotlinx.coroutines.*
import org.jetbrains.kotlin.idea.util.projectStructure.getModule

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
        coroutineScope.value.launch {
            for (document in documents) {
                val file = fileDocumentManager.value.getFile(document)
                if (file != null && file.name.endsWith(".arb")) {
                    val module = file.getModule(project) ?: continue
                    doGenL10n(module, file.path.startsWith("${module.basePath}/example/"))
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

        fileDocumentManager.value.getDocument(file)?.addDocumentListener(documentListener.value)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        super.fileClosed(source, file)
        if (!file.name.endsWith(".arb")) {
            return
        }

        if (!source.project.isFlutterProject) {
            return
        }

        fileDocumentManager.value.getDocument(file)?.removeDocumentListener(documentListener.value)

        if (needDoGenL10nMap[file.path] != true) {
            return
        }

        needDoGenL10nMap.remove(file.path)
        val module = file.getModule(source.project) ?: return
        fileDocumentManager.value.saveAllDocuments()
        doGenL10n(module, file.path.startsWith("${module.basePath}/example/"))
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        super.selectionChanged(event)
        val file: VirtualFile = event.oldFile ?: return
        if (needDoGenL10nMap[file.path] == true) {
            needDoGenL10nMap.remove(file.path)
            val module = ModuleUtilCore.findModuleForFile(file,event.manager.project) ?: return
            fileDocumentManager.value.saveAllDocuments()
            doGenL10n(module, file.path.startsWith("${module.basePath}/example/"))
        }
    }

    private fun doGenL10n(module: Module, isExample: Boolean = false) {
        module.basePath?.also { basePath ->
            val path = if (isExample) "$basePath/Example" else basePath
            var job = jobMap[path]
            // 仅仅最后一次文本变更时执行,如果多个项目都有文本变化，则只处理最后一个项目
            if (job?.isActive == true) {
                job.cancel()
            }

            job = coroutineScope.value.launch {
                delay(500)
                val workDir = if (isExample) module.findChild("example") else module.rootDir
                workDir?.also {
                    FlutterSdk.getFlutterSdk(module.project)?.also { sdk ->
                        val commandLine = FlutterCommandLine(sdk, it, FlutterCommandLine.Type.GEN_L10N)
                        commandLine.start()
                    }
                }
            }

            jobMap[path] = job
        }
    }

    companion object {
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
    }
}
