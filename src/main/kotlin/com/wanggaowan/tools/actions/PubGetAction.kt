package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.wanggaowan.tools.extensions.complete.CodeAnalysisService
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.flutter.FlutterCommandUtils
import io.flutter.actions.FlutterSdkAction
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk

/**
 * 执行flutter pub get 命令
 *
 * @author Created by wanggaowan on 2023/9/4 10:57
 */
class PubGetAction : FlutterSdkAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        if (!e.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }

    override fun startCommand(project: Project, sdk: FlutterSdk, root: PubRoot?, context: DataContext) {
        root?.also { pubRoot ->
            val module = pubRoot.getModule(project) ?: return
            FlutterCommandUtils.pubGet(module, pubRoot, sdk, onDone = { existCode ->
                if (existCode == 0) {
                    CodeAnalysisService.startAnalysisModules(project, project.modules.toList())
                }
            })
        }
    }
}
