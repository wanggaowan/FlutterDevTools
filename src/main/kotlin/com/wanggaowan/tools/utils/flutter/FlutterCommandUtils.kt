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
}




