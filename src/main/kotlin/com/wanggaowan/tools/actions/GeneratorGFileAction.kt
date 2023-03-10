package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.haveDependencies
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.flutter.FlutterCommandUtils
import com.wanggaowan.tools.utils.flutter.YamlUtils
import io.flutter.actions.FlutterSdkAction
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk
import org.jetbrains.kotlin.idea.core.util.toPsiFile

/**
 * 根据项目中DART类生成对应的.g.dart文件
 *
 * @author Created by wanggaowan on 2023/2/6 15:44
 */
open class GeneratorGFileAction : FlutterSdkAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        if (!project.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }

    override fun startCommand(project: Project, sdk: FlutterSdk, root: PubRoot?, context: DataContext) {
        root?.also {
            it.pubspec.toPsiFile(project)?.also { psiFile ->
                val haveJsonAnnotation = root.haveDependencies("build_runner") ||
                    YamlUtils.haveDependencies(psiFile, YamlUtils.DEPENDENCY_TYPE_ALL, "build_runner")
                val havePubspecLockFile = XUtils.havePubspecLockFile(project)
                FlutterCommandUtils.addBuildRunner(project, it, sdk, haveJsonAnnotation) {
                    FlutterCommandUtils.doPubGet(project, it, sdk, havePubspecLockFile) {
                        FlutterCommandUtils.startGeneratorJsonSerializable(project, root, sdk, onDone = {
                            onCommandEnd(context)
                        })
                    }
                }
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    protected open fun onCommandEnd(context: DataContext) {
        context.getData(CommonDataKeys.VIRTUAL_FILE)?.parent?.refresh(true, false)
    }
}
