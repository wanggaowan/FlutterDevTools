package com.wanggaowan.tools.utils.flutter

import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.project.Project
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk

/**
 * Flutter命令行工具
 *
 * @author Created by wanggaowan on 2023/2/6 15:02
 */
object FlutterCommandUtils {

    /**
     * 执行添加依赖命令
     */
    fun startAddDependencies(
        project: Project,
        root: PubRoot,
        sdk: FlutterSdk,
        type: FlutterCommandLine.Type,
        onDone: ((existCode: Int) -> Unit)? = null,
        processListener: ProcessListener? = null
    ): Process? {
        val module = root.getModule(project) ?: return null
        val commandLine = FlutterCommandLine(sdk, root.root, type)
        return commandLine.startInModuleConsole(module, onDone, processListener)
    }

    /**
     * 根据Dart实体生成JSON序列化实体文件
     */
    fun startGeneratorJsonSerializable(
        project: Project,
        root: PubRoot,
        sdk: FlutterSdk,
        onDone: ((existCode: Int) -> Unit)? = null,
        processListener: ProcessListener? = null
    ): Process? {
        val module = root.getModule(project) ?: return null
        val commandLine = FlutterCommandLine(sdk, root.root, FlutterCommandLine.Type.GENERATOR_JSON_SERIALIZABLE)
        return commandLine.startInModuleConsole(module, onDone, processListener)
    }

    /**
     * 开启根据Dart实体转JSON序列化文件观察者
     */
    fun startJsonSerializableWatcher(
        project: Project,
        root: PubRoot,
        sdk: FlutterSdk,
        onDone: ((existCode: Int) -> Unit)? = null,
        processListener: ProcessListener? = null
    ): Process? {
        val module = root.getModule(project) ?: return null
        val commandLine = FlutterCommandLine(sdk, root.root, FlutterCommandLine.Type.JSON_SERIALIZABLE_WATCHER)
        return commandLine.startInModuleConsole(module, onDone, processListener)
    }

    /**
     * 执行pub get命令
     */
    fun pubGet(
        project: Project,
        root: PubRoot,
        sdk: FlutterSdk,
        onDone: ((existCode: Int) -> Unit)? = null,
        processListener: ProcessListener? = null
    ): Process? {
        val module = root.getModule(project) ?: return null
        val commandLine = FlutterCommandLine(sdk, root.root, FlutterCommandLine.Type.PUB_GET)
        return commandLine.startInModuleConsole(module, onDone, processListener)
    }

    /**
     * 执行添加build_runner依赖依赖命令，生成序列化文件
     */
    fun addBuildRunner(
        project: Project,
        pubRoot: PubRoot,
        flutterSdk: FlutterSdk,
        haveBuildRunner: Boolean,
        onDone: Runnable? = null
    ) {
        if (!haveBuildRunner) {
            startAddDependencies(project, pubRoot, flutterSdk,
                FlutterCommandLine.Type.ADD_BUILD_RUNNER_DEV, {
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
     * 执行添加json_annotation依赖命令
     */
    fun doPubGet(
        project: Project,
        pubRoot: PubRoot,
        flutterSdk: FlutterSdk,
        havePubspecLockFile: Boolean,
        onDone: Runnable? = null
    ) {
        if (!havePubspecLockFile) {
            pubGet(project, pubRoot, flutterSdk, {
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




