package com.wanggaowan.tools.utils.flutter

import com.google.common.collect.ImmutableList
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import io.flutter.FlutterBundle
import io.flutter.FlutterMessages
import io.flutter.android.IntelliJAndroidSdk
import io.flutter.console.FlutterConsoles
import io.flutter.dart.DartPlugin
import io.flutter.sdk.FlutterCommandStartResult
import io.flutter.sdk.FlutterCommandStartResultStatus
import io.flutter.sdk.FlutterSdk
import io.flutter.sdk.FlutterSdkUtil
import io.flutter.utils.MostlySilentColoredProcessHandler
import java.nio.charset.StandardCharsets
import java.util.function.Consumer

/**
 * Flutter命令行命令
 *
 * @param workDir 当前命令执行的目录范围，不传则为Flutter环境变量配置的目录
 * @param type 要执行的命令类型
 *
 * @author Created by wanggaowan on 2023/2/6 17:35
 */
class FlutterCommandLine internal constructor(
    private var sdk: FlutterSdk,
    workDir: VirtualFile?,
    type: Type,
    vararg args: String
) {
    private val log = Logger.getInstance(FlutterCommandLine::class.java)
    private var workDir: VirtualFile? = null
    private var type: Type
    private var args: List<String>

    init {
        this.workDir = workDir
        this.type = type
        this.args = ImmutableList.copyOf(args)
    }

    fun getDisplayCommand(): String {
        val words: MutableList<String> = mutableListOf()
        words.add("flutter")
        words.addAll(type.subCommand)
        words.addAll(args)
        return java.lang.String.join(" ", words)
    }

    fun start(onDone: Consumer<ProcessOutput>? = null, processListener: ProcessListener? = null): Process? {
        val handler = startProcessOrShowError(null as Project?)
        return if (handler == null) {
            null
        } else {
            if (processListener != null) {
                handler.addProcessListener(processListener)
            }
            if (onDone != null) {
                val listener: CapturingProcessAdapter = object : CapturingProcessAdapter() {
                    override fun processTerminated(event: ProcessEvent) {
                        super.processTerminated(event)
                        onDone.accept(this.output)
                    }
                }
                handler.addProcessListener(listener)
            }
            handler.startNotify()
            handler.process
        }
    }

    fun startInConsole(project: Project): ColoredProcessHandler? {
        val handler = startProcessOrShowError(project)
        if (handler != null) {
            FlutterConsoles.displayProcessLater(handler, project, null) { handler.startNotify() }
        }
        return handler
    }

    fun startInModuleConsole(
        module: Module,
        onDone: ((existCode: Int) -> Unit)?,
        processListener: ProcessListener?
    ): Process? {
        val handler = startProcessOrShowError(module.project)
        return if (handler == null) {
            null
        } else {
            if (processListener != null) {
                handler.addProcessListener(processListener)
            }
            handler.addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    onDone?.invoke(event.exitCode)
                }
            })
            FlutterConsoles.displayProcessLater(handler, module.project, module) { handler.startNotify() }
            handler.process
        }
    }

    override fun toString(): String {
        return "FlutterCommandLine(" + getDisplayCommand() + ")"
    }

    fun startProcess(sendAnalytics: Boolean): ColoredProcessHandler? {
        return try {
            val commandLine = createGeneralCommandLine(null as Project?)
            log.info(commandLine.toString())
            ColoredProcessHandler(commandLine)
        } catch (var4: ExecutionException) {
            FlutterMessages.showError(
                type.title,
                FlutterBundle.message("flutter.command.exception.message", var4.message ?: ""),
                null
            )
            null
        }
    }

    fun startProcess(project: Project?): FlutterCommandStartResult {
        if (type.longTime) {
            DartPlugin.setPubActionInProgress(true)
        }
        return try {
            val commandLine = createGeneralCommandLine(project)
            log.info(commandLine.toString())
            val handler: ColoredProcessHandler = MostlySilentColoredProcessHandler(commandLine)
            handler.addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    if (type.longTime) {
                        DartPlugin.setPubActionInProgress(false)
                    }
                }
            })
            FlutterCommandStartResult(handler)
        } catch (var4: ExecutionException) {
            if (type.longTime) {
                DartPlugin.setPubActionInProgress(false)
            }
            FlutterCommandStartResult(var4)
        }
    }

    fun startProcessOrShowError(project: Project?): ColoredProcessHandler? {
        val result = startProcess(project)
        if (result.status == FlutterCommandStartResultStatus.EXCEPTION && result.exception != null) {
            FlutterMessages.showError(
                type.title, FlutterBundle.message(
                    "flutter.command.exception.message", result.exception?.message ?: ""
                ), project
            )
        }
        return result.processHandler
    }

    fun createGeneralCommandLine(project: Project?): GeneralCommandLine {
        val line = GeneralCommandLine()
        line.charset = StandardCharsets.UTF_8
        line.withEnvironment("FLUTTER_HOST", FlutterSdkUtil().flutterHostEnvValue)
        val androidHome = IntelliJAndroidSdk.chooseAndroidHome(project, false)
        if (androidHome != null) {
            line.withEnvironment("ANDROID_HOME", androidHome)
        }
        val var10001 = sdk.homePath
        line.exePath = FileUtil.toSystemDependentName(var10001 + "/bin/" + FlutterSdkUtil.flutterScriptName())
        if (workDir != null) {
            line.setWorkDirectory(workDir!!.path)
        }
        if (!type.highlight) {
            line.addParameter("--no-color")
        }
        line.addParameters(type.subCommand)
        line.addParameters(args)
        return line
    }

    enum class Type(
        val title: String,
        /**
         * 命令是否耗时较长
         */
        val longTime: Boolean,
        /**
         * 命令输出是否要高亮
         */
        val highlight: Boolean,
        vararg subCommand: String
    ) {
        GENERATOR_JSON_SERIALIZABLE(
            "根据dart生成json序列化文件",
            true,
            false,
            "pub",
            "run",
            "build_runner",
            "build"
        ),
        JSON_SERIALIZABLE_WATCHER(
            "开启dart生成json序列化文件观察者，只要dart文件配置了相关属性，自动检测然后生成序列化文件",
            false,
            false,
            "pub",
            "run",
            "build_runner",
            "watch",
            "--delete-conflicting-outputs"
        ),
        ADD_JSON_ANNOTATION(
            "添加json_annotation依赖",
            true,
            false,
            "pub",
            "add",
            "json_annotation"
        ),
        ADD_JSON_SERIALIZABLE_DEV(
            "添加json_serializable依赖",
            true,
            false,
            "pub",
            "add",
            "json_serializable",
            "--dev"
        ),
        ADD_BUILD_RUNNER_DEV(
            "添加build_runner依赖",
            true,
            false,
            "pub",
            "add",
            "build_runner",
            "--dev"
        ),
        PUB_GET(
            "pub get",
            true,
            false,
            "pub",
            "get",
        ),
        GEN_L10N(
            "gen-l10n",
            false,
            false,
            "gen-l10n",
        ),
        PUB_DEPS(
            "pub deps",
            false,
            false,
            "pub",
            "deps"
        );

        /**
         * 命令参数
         */
        val subCommand: ImmutableList<String> = ImmutableList.copyOf(subCommand)
    }
}
