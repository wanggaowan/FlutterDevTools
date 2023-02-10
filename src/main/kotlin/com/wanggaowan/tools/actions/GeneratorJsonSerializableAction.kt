package com.wanggaowan.tools.actions

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
 * 根据项目中DART实体生成对应的JSON序列化类
 *
 * @author Created by wanggaowan on 2023/2/6 15:44
 */
class GeneratorJsonSerializableAction : FlutterSdkAction() {
    override fun startCommand(project: Project, sdk: FlutterSdk, root: PubRoot?, context: DataContext) {
        root?.also {
            it.pubspec.toPsiFile(project)?.also { psiFile ->
                val haveJsonAnnotation = YamlUtils.haveDependencies(psiFile, true, "build_runner")
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
}
