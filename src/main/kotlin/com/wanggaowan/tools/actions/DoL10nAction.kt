package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
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
        root?.also {
            it.pubspec.toPsiFile(project)?.also { psiFile ->
                val haveLocalizations =
                    YamlUtils.haveDependencies(psiFile, YamlUtils.DEPENDENCY_TYPE_ALL, "flutter_localizations")
                val haveIntl =
                    YamlUtils.haveDependencies(psiFile, YamlUtils.DEPENDENCY_TYPE_ALL, "intl")
                if (!haveLocalizations || !haveIntl) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        var dependencies = YamlUtils.findElement(psiFile, "dependencies")
                        val yamlGenerator = YAMLElementGenerator.getInstance(project)
                        // 两个节点之间的分隔符
                        val eolElement = yamlGenerator.createEol()
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
                            YamlUtils.createYAMLKeyValue(project, "flutter_localizations:\n  sdk:flutter")
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
                }

                FlutterCommandUtils.genL10N(project, root, sdk)
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
