package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.flutter.FlutterCommandUtils
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk

/**
 * 根据项目中DART类生成对应的.g.dart文件,只生成选中的单个文件内容
 *
 * @author Created by wanggaowan on 2023/6/15 19:05
 */
open class GeneratorSingleGFileAction : GeneratorGFileAction() {
    override fun update(e: AnActionEvent) {
        if (!e.isFlutterProject) {
            e.presentation.isVisible = false
            return
        }

        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null && !virtualFile.isDirectory) {
            e.presentation.isVisible = virtualFile.name.endsWith(".dart")
            return
        }

        e.presentation.isVisible = true
    }


    override fun startGeneratorGFile(module: Module, sdk: FlutterSdk, root: PubRoot, context: DataContext) {
        val file = context.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file != null) {
            // 只生成当前文件的.g.dart
            FlutterCommandUtils.startGeneratorJsonSerializable(
                module, root, sdk,
                includeFiles = listOf(file),
                onDone = {
                    onCommandEnd(context)
                })
        } else {
            FlutterCommandUtils.startGeneratorJsonSerializable(module, root, sdk, onDone = {
                onCommandEnd(context)
            })
        }
    }
}
