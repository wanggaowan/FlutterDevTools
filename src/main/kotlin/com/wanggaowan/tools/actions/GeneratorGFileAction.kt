package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.wanggaowan.tools.utils.flutter.FlutterCommandLine
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
class GeneratorGFileAction : FlutterSdkAction() {
    override fun startCommand(project: Project, sdk: FlutterSdk, root: PubRoot?, context: DataContext) {
        root?.also {
            it.pubspec.toPsiFile(project)?.also { psiFile ->
                val haveJsonAnnotation = YamlUtils.haveDependencies(psiFile, YamlUtils.DEPENDENCY_TYPE_ALL, "build_runner")
                if (haveJsonAnnotation) {
                    FlutterCommandUtils.startGeneratorJsonSerializable(project, root, sdk)
                    return
                }

                FlutterCommandUtils.startAddDependencies(project, it, sdk,
                    FlutterCommandLine.Type.ADD_BUILD_RUNNER_DEV, { existCode ->
                        if (existCode == 0) {
                            FlutterCommandUtils.startGeneratorJsonSerializable(project, root, sdk)
                        }
                    }
                )
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
