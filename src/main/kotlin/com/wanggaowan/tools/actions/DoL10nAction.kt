package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.wanggaowan.tools.utils.flutter.FlutterCommandUtils
import com.wanggaowan.tools.utils.flutter.YamlUtils
import io.flutter.actions.FlutterSdkAction
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLMapping

/**
 * 执行l10n生成多语言相关代码命令
 *
 * @author Created by wanggaowan on 2023/2/6 15:44
 */
open class DoL10nAction : FlutterSdkAction() {
    override fun startCommand(project: Project, sdk: FlutterSdk, root: PubRoot?, context: DataContext) {
        if (root == null) {
            return
        }

        val module = root.getModule(project) ?: return
        doGenL10n(module, sdk, root) {
            val exampleDir = root.exampleDir
            if (exampleDir != null) {
                val examplePubRoot = PubRoot.forDirectory(exampleDir)
                if (examplePubRoot != null) {
                    doGenL10n(module, sdk, examplePubRoot)
                }
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    protected fun doGenL10n(
        module: Module,
        sdk: FlutterSdk,
        pubRoot: PubRoot,
        onDone: ((existCode: Int) -> Unit)? = null
    ) {

        val project = module.project
        WriteCommandAction.runWriteCommandAction(project) {
            pubRoot.pubspec.toPsiFile(project)?.also { psiFile ->
                val packagesMap = pubRoot.packagesMap
                // 通过packagesMap可以多层级依赖的情况下flutter_localizations是否存在
                val haveLocalizations = packagesMap?.get("flutter_localizations") != null
                    || YamlUtils.haveDependencies(psiFile, YamlUtils.DEPENDENCY_TYPE_ALL, "flutter_localizations")
                val haveIntl = packagesMap?.get("intl") != null
                    || YamlUtils.haveDependencies(psiFile, YamlUtils.DEPENDENCY_TYPE_ALL, "intl")

                val yamlGenerator = YAMLElementGenerator.getInstance(project)
                // 两个节点之间的分隔符
                val eolElement = yamlGenerator.createEol()
                if (!haveLocalizations || !haveIntl) {
                    var dependencies = YamlUtils.findElement(psiFile, "dependencies")
                    if (dependencies == null) {
                        dependencies =
                            YamlUtils.createYAMLKeyValue(project, "dependencies:") ?: return@runWriteCommandAction
                        val document = psiFile.getChildOfType<YAMLDocument>() ?: return@runWriteCommandAction
                        val mapping = document.getChildOfType<YAMLMapping>()
                        dependencies = if (mapping == null) {
                            psiFile.add(dependencies) ?: return@runWriteCommandAction
                        } else {
                            mapping.add(eolElement)
                            mapping.add(dependencies) ?: return@runWriteCommandAction
                        }
                    }

                    if (!haveLocalizations) {
                        YamlUtils.createYAMLKeyValue(project, "flutter_localizations:\n  sdk: flutter")
                            ?.also { child ->
                                dependencies.add(eolElement)
                                dependencies.add(child)
                            }
                    }

                    if (!haveIntl) {
                        YamlUtils.createYAMLKeyValue(project, "intl: any")?.also { child ->
                            dependencies.add(eolElement)
                            dependencies.add(child)
                        }
                    }
                }

                var flutterElement = YamlUtils.findElement(psiFile, "flutter")
                if (flutterElement == null) {
                    flutterElement =
                        YamlUtils.createYAMLKeyValue(project, "flutter:") ?: return@runWriteCommandAction
                    val document = psiFile.getChildOfType<YAMLDocument>() ?: return@runWriteCommandAction
                    val mapping = document.getChildOfType<YAMLMapping>()
                    flutterElement = if (mapping == null) {
                        psiFile.add(flutterElement) ?: return@runWriteCommandAction
                    } else {
                        mapping.add(eolElement)
                        mapping.add(flutterElement) ?: return@runWriteCommandAction
                    }
                }

                if (YamlUtils.findElement(flutterElement, "generate") == null) {
                    YamlUtils.createYAMLKeyValue(project, "generate: true")?.also { child ->
                        flutterElement.add(eolElement)
                        flutterElement.add(child)
                    }
                }

                FlutterCommandUtils.genL10N(module, pubRoot, sdk, onDone)
            }
        }
    }
}
