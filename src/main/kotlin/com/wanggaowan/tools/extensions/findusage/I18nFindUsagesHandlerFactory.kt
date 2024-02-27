package com.wanggaowan.tools.extensions.findusage

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.json.JsonLanguage
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.jetbrains.lang.dart.ide.findUsages.DartServerFindUsagesHandler
import com.jetbrains.lang.dart.psi.DartClass
import com.jetbrains.lang.dart.psi.DartComponentName
import com.jetbrains.lang.dart.psi.DartId
import com.wanggaowan.tools.utils.flutter.YamlUtils
import io.flutter.pub.PubRoot
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * 多语言查找使用位置
 *
 * @author Created by wanggaowan on 2024/2/27 11:26
 */
class I18nFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean {
        if (element.language != JsonLanguage.INSTANCE || element !is JsonProperty) {
            return false
        }

        if (element.containingFile?.name?.endsWith(".arb") != true) {
            return false
        }


        return true
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler {
        return I18nUsagesHandler(element)
    }
}

class I18nUsagesHandler(psiElement: PsiElement) : FindUsagesHandler(psiElement) {
    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Boolean {
        val result = super.processElementUsages(element, processor, options)
        if (element !is JsonProperty) {
            return result
        }

        ApplicationManager.getApplication().runReadAction {
            val project = element.project
            val selectedFile = element.containingFile ?: return@runReadAction
            val pubRoot = PubRoot.forPsiFile(selectedFile) ?: return@runReadAction

            var rootDir = pubRoot.root
            val example = pubRoot.exampleDir
            if (example != null && selectedFile.virtualFile.path.startsWith(example.path)) {
                rootDir = example
            }

            val rootDirPath = rootDir.path
            var outputDir = ".dart_tool/flutter_gen/gen_l10n"
            var outputLocalizationFile = "app_localizations.dart"
            val config = rootDir.findChild("l10n.yaml")
            if (config != null) {
                val psiFile = config.toPsiFile(project)
                if (psiFile != null) {
                    var node = YamlUtils.findElement(psiFile, "output-dir")
                    if (node != null && node is YAMLKeyValue) {
                        node.value?.text?.also {
                            outputDir = it
                        }
                    }

                    node = YamlUtils.findElement(psiFile, "output-localization-file")
                    if (node != null && node is YAMLKeyValue) {
                        node.value?.text?.also {
                            outputLocalizationFile = it
                        }
                    }
                }
            }


            val file =
                VirtualFileManager.getInstance().findFileByUrl("file://$rootDirPath/$outputDir/$outputLocalizationFile")
                    ?: return@runReadAction
            val psiFile = file.toPsiFile(project) ?: return@runReadAction
            val clazz = psiFile.getChildOfType<DartClass>() ?: return@runReadAction
            val member = clazz.findMemberByName(element.name) ?: return@runReadAction
            val child = member.getChildOfType<DartComponentName>()?.getChildOfType<DartId>()?.firstChild?:return@runReadAction
            DartServerFindUsagesHandler(child).processElementUsages(child, processor, options)
        }
        return true
    }
}
