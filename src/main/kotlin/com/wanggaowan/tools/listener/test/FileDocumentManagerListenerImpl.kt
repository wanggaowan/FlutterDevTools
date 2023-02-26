package com.wanggaowan.tools.listener.test

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VirtualFile
import com.wanggaowan.tools.listener.ProjectManagerListenerImpl
import com.wanggaowan.tools.utils.msg.Toast

/**
 *
 *
 * @author Created by wanggaowan on 2023/2/23 23:13
 */
class FileDocumentManagerListenerImpl: FileDocumentManagerListener {

    override fun beforeAllDocumentsSaving() {
        super.beforeAllDocumentsSaving()
        ProjectManagerListenerImpl.project?.also {
            Toast.show(it, MessageType.INFO, "beforeAllDocumentsSaving")
        }
    }

    override fun beforeDocumentSaving(document: Document) {
        super.beforeDocumentSaving(document)
        ProjectManagerListenerImpl.project?.also {
            Toast.show(it, MessageType.INFO, "beforeDocumentSaving")
        }
    }

    override fun beforeFileContentReload(file: VirtualFile, document: Document) {
        super.beforeFileContentReload(file, document)
        ProjectManagerListenerImpl.project?.also {
            Toast.show(it, MessageType.INFO, "beforeFileContentReload")
        }
    }

    override fun fileWithNoDocumentChanged(file: VirtualFile) {
        super.fileWithNoDocumentChanged(file)
        ProjectManagerListenerImpl.project?.also {
            Toast.show(it, MessageType.INFO, "fileWithNoDocumentChanged")
        }
    }

    override fun fileContentReloaded(file: VirtualFile, document: Document) {
        super.fileContentReloaded(file, document)
        ProjectManagerListenerImpl.project?.also {
            Toast.show(it, MessageType.INFO, "fileContentReloaded")
        }
    }

    override fun fileContentLoaded(file: VirtualFile, document: Document) {
        super.fileContentLoaded(file, document)
        ProjectManagerListenerImpl.project?.also {
            Toast.show(it, MessageType.INFO, "fileContentLoaded")
        }
    }

    override fun unsavedDocumentDropped(document: Document) {
        super.unsavedDocumentDropped(document)
    }

    override fun unsavedDocumentsDropped() {
        super.unsavedDocumentsDropped()
    }

    override fun afterDocumentUnbound(file: VirtualFile, document: Document) {
        super.afterDocumentUnbound(file, document)
        ProjectManagerListenerImpl.project?.also {
            Toast.show(it, MessageType.INFO, "afterDocumentUnbound")
        }
    }
}
