package com.wanggaowan.tools.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 后台任务执行工具
 *
 * 使用约定（务必遵守，否则会导致界面卡死或进度不刷新）：
 * 1. 耗时操作（网络请求、Thread.sleep、外部进程、大量计算、文件IO）必须放在后台任务中，
 *    不能放进WriteCommandAction：write action会被调度到EDT执行，长时间占用会阻塞UI线程
 * 2. 只有PSI/VFS/Document的修改才需要write action，且应尽量短小
 * 3. 保存文档（FileDocumentManager.save*）不是写操作，不应放在write action中；
 *    但保存API必须在EDT线程调用，后台线程中需使用[DocumentUtils]保存文档
 *
 * @author Created by wanggaowan on 2024/1/5 15:28
 */
object ProgressUtils {

    /**
     * 在后台线程执行[run]，[run]执行结束即认为任务结束
     *
     * 注意：[run]运行在后台线程，内部访问PSI/VFS需要自行持有read action或write action
     */
    fun runBackground(
        project: Project,
        title: String,
        canBeCancelled: Boolean = false,
        run: (progressIndicator: ProgressIndicator) -> Unit
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, canBeCancelled) {
            override fun run(p0: ProgressIndicator) {
                run(p0)
            }
        })
    }

    /**
     * 在后台线程执行[run]，与[runBackground]的差异：[run]中允许启动异步任务（如协程、invokeLater）
     *
     * 所有异步任务执行完成后必须调用[finish]，否则进度对话框会一直等待；
     * 任务被取消或[run]抛出异常时，内部会自动结束等待
     */
    fun runBackgroundAsync(
        project: Project,
        title: String,
        canBeCancelled: Boolean = true,
        run: (progressIndicator: ProgressIndicator, finish: () -> Unit) -> Unit
    ) {
        val latch = CountDownLatch(1)
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, canBeCancelled) {
            override fun run(p0: ProgressIndicator) {
                try {
                    run(p0) { latch.countDown() }
                    while (!p0.isCanceled && !latch.await(50, TimeUnit.MILLISECONDS)) {
                        // 等待异步任务执行完成，此处停顿在后台线程，不会阻塞EDT，进度可正常刷新
                    }
                } finally {
                    // 任务异常或被取消时，保证能退出等待
                    latch.countDown()
                }
            }
        })
    }

    /**
     * 在EDT线程执行[computable]并返回结果，当前线程已是EDT时直接执行
     *
     * 适用于必须在EDT线程调用的API（如显示对话框、FindUsagesManager相关API）
     */
    fun <T> computeInEdt(computable: () -> T): T {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            return computable()
        }

        val result = AtomicReference<T>()
        application.invokeAndWait { result.set(computable()) }
        return result.get()
    }
}
