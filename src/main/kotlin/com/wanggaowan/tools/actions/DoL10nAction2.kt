package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.wanggaowan.tools.Sdk
import com.wanggaowan.tools.utils.ex.isFlutterProject
import io.flutter.sdk.FlutterSdk

/**
 * 执行l10n生成多语言相关代码命令
 *
 * @author Created by wanggaowan on 2023/2/6 15:44
 */
class DoL10nAction2 : DoL10nAction() {
    override fun update(e: AnActionEvent) {
        if (!e.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val virtualFile = event.getData(LangDataKeys.VIRTUAL_FILE) ?: return
        val pubRoot = Sdk.getInstance(project).getPubRoot(virtualFile) ?: return
        val module = pubRoot.getModule(project) ?: return
        val fluterSdk = FlutterSdk.getFlutterSdk(project) ?: return
        doGenL10n(module, fluterSdk, pubRoot)
    }
}
