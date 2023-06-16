package com.wanggaowan.tools.utils.flutter

import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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
     *
     * @param includeFiles 指定具体文件生成.g.dart文件，未指定则全盘扫描
     */
    fun startGeneratorJsonSerializable(
        project: Project,
        root: PubRoot,
        sdk: FlutterSdk,
        includeFiles: List<VirtualFile>? = null,
        onDone: ((existCode: Int) -> Unit)? = null,
        processListener: ProcessListener? = null
    ): Process? {
        val module = root.getModule(project) ?: return null
        val commandLine = if (includeFiles.isNullOrEmpty()) {
            FlutterCommandLine(sdk, root.root, FlutterCommandLine.Type.GENERATOR_JSON_SERIALIZABLE)
        } else {
            val array = includeFiles.map {
                var path = it.path.replace(root.path, "")
                val index = path.indexOf(".dart")
                if (index != -1) {
                    path = path.substring(0, index) + "*" + ".dart"
                }

                if (path.startsWith("/")) {
                    path = path.substring(1)
                }

                // 不管用那种方法在path两边加双引号，最后控制台输出都是\",由于不加也可执行，暂时先不加
                // "--build-filter=\u0022$path\u0022"
                "--build-filter=$path"
            }.toTypedArray()
            FlutterCommandLine(sdk, root.root, FlutterCommandLine.Type.GENERATOR_JSON_SERIALIZABLE, *array)
        }
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
     * 执行gen-l10n命令
     */
    fun genL10N(
        project: Project,
        root: PubRoot,
        sdk: FlutterSdk,
        onDone: ((existCode: Int) -> Unit)? = null,
        processListener: ProcessListener? = null
    ): Process? {
        val module = root.getModule(project) ?: return null
        val commandLine = FlutterCommandLine(sdk, root.root, FlutterCommandLine.Type.GEN_L10N)
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




