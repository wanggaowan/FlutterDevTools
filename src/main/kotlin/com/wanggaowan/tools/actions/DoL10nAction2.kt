package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.Project
import com.wanggaowan.tools.utils.ex.isFlutterProject
import io.flutter.pub.PubRoot
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

    override fun startCommand(project: Project, sdk: FlutterSdk, root: PubRoot?, context: DataContext) {
        if (root == null) {
            return
        }

        val virtualFile = context.getData(LangDataKeys.VIRTUAL_FILE) ?: return
        if (virtualFile.path.contains("/example/")) {
            val exampleDir = root.exampleDir
            if (exampleDir != null) {
                val examplePubRoot = PubRoot.forDirectory(exampleDir)
                if (examplePubRoot != null) {
                    doGenL10n(project, sdk, examplePubRoot)
                }
            }
            return
        }

        doGenL10n(project, sdk, root)
    }
}
