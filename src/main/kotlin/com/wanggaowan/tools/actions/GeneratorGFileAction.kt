package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
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
open class GeneratorGFileAction : FlutterSdkAction() {

    override fun update(e: AnActionEvent) {
        if (!e.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun startCommand(project: Project, sdk: FlutterSdk, root: PubRoot?, context: DataContext) {
        root?.also { pubRoot ->
            val module = pubRoot.getModule(project) ?: return
            addGeneratorGFileDependencies(module, sdk, pubRoot) {
                startGeneratorGFile(module, sdk, pubRoot, context)
            }
        }
    }

    /**
     * 执行生产.g文件指令
     */
    protected open fun startGeneratorGFile(
        module: Module,
        sdk: FlutterSdk,
        root: PubRoot,
        context: DataContext
    ) {
        FlutterCommandUtils.startGeneratorJsonSerializable(module, root, sdk, onDone = {
            onCommandEnd(context)
        })
    }

    protected open fun onCommandEnd(context: DataContext) {
        context.getData(CommonDataKeys.VIRTUAL_FILE)?.parent?.refresh(true, false)
    }

    companion object {
        /**
         * 添加生产.g.dart需要的pub依赖
         */
        fun addGeneratorGFileDependencies(
            module: Module,
            sdk: FlutterSdk,
            pubRoot: PubRoot,
            onDone: Runnable? = null
        ) {

            pubRoot.pubspec.toPsiFile(module.project)?.also { pubspec ->
                val packagesMap = pubRoot.packagesMap
                val haveJsonAnnotation = packagesMap?.get("json_annotation") != null
                    || YamlUtils.haveDependencies(pubspec, YamlUtils.DEPENDENCY_TYPE_ALL, "json_annotation")
                val haveJsonSerializable = packagesMap?.get("json_serializable") != null
                    || YamlUtils.haveDependencies(pubspec, YamlUtils.DEPENDENCY_TYPE_ALL, "json_serializable")
                val haveBuildRunner = packagesMap?.get("build_runner") != null
                    || YamlUtils.haveDependencies(pubspec, YamlUtils.DEPENDENCY_TYPE_ALL, "build_runner")
                val havePubspecLockFile = XUtils.havePubspecLockFile(module.project)
                addJsonAnnotation(module, pubRoot, sdk, haveJsonAnnotation) {
                    addJsonSerializable(module, pubRoot, sdk, haveJsonSerializable) {
                        FlutterCommandUtils.addBuildRunner(module, pubRoot, sdk, haveBuildRunner) {
                            FlutterCommandUtils.doPubGet(module, pubRoot, sdk, havePubspecLockFile) {
                                onDone?.run()
                            }
                        }
                    }
                }
            }
        }

        /**
         * 执行添加json_annotation依赖命令
         */
        private fun addJsonAnnotation(
            module: Module,
            pubRoot: PubRoot,
            flutterSdk: FlutterSdk,
            haveJsonAnnotation: Boolean,
            onDone: Runnable? = null
        ) {
            if (!haveJsonAnnotation) {
                FlutterCommandUtils.startAddDependencies(
                    module, pubRoot, flutterSdk,
                    FlutterCommandLine.Type.ADD_JSON_ANNOTATION, {
                        if (it == 0) {
                            onDone?.run()
                        }
                    }
                )
            } else {
                onDone?.run()
            }
        }

        /**
         * 执行添加json_serializable依赖命令
         */
        private fun addJsonSerializable(
            module: Module,
            pubRoot: PubRoot,
            flutterSdk: FlutterSdk,
            haveJsonSerializable: Boolean,
            onDone: Runnable? = null
        ) {
            if (!haveJsonSerializable) {
                FlutterCommandUtils.startAddDependencies(
                    module, pubRoot, flutterSdk,
                    FlutterCommandLine.Type.ADD_JSON_SERIALIZABLE_DEV, {
                        if (it == 0) {
                            onDone?.run()
                        }
                    }
                )
            } else {
                onDone?.run()
            }
        }
    }
}
