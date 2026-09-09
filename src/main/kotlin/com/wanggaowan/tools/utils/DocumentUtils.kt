package com.wanggaowan.tools.utils

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile

/**
 * 文档保存工具
 *
 * FileDocumentManager的保存API（saveDocument/saveAllDocuments）有两个约束：
 * 1. 必须在EDT线程调用，在后台线程调用会触发平台的assertIsDispatchThread断言
 * 2. 保存不属于写操作，不应放在write action中执行，否则write action长时间占用EDT
 *    会导致界面卡死、进度对话框不刷新
 *
 * 因此在后台线程中需要保存文档时，统一使用此类：将保存操作切换到EDT线程执行。
 *
 * 注意：不要在持有read action时调用，invokeAndWait等待EDT时可能与UI线程的写锁请求互相等待
 */
object DocumentUtils {

    /**
     * 保存所有未保存的文档
     */
    fun saveAllDocuments() {
        ProgressUtils.computeInEdt {
            FileDocumentManager.getInstance().saveAllDocuments()
        }
    }

    /**
     * 保存[document]对应的文件，[document]为null时保存所有未保存的文档
     */
    fun saveDocument(document: Document?) {
        if (document == null) {
            saveAllDocuments()
            return
        }

        ProgressUtils.computeInEdt {
            FileDocumentManager.getInstance().saveDocument(document)
        }
    }

    /**
     * 保存[psiFile]对应的文档，未获取到文档时保存所有未保存的文档
     *
     * 内部会将PSI读取及保存操作切换到EDT线程执行，避免在后台线程访问PSI
     */
    fun saveDocument(psiFile: PsiFile) {
        ProgressUtils.computeInEdt {
            val manager = FileDocumentManager.getInstance()
            val document = manager.getDocument(psiFile.virtualFile) ?: psiFile.viewProvider.document
            if (document == null) {
                manager.saveAllDocuments()
            } else {
                manager.saveDocument(document)
            }
        }
    }
}
