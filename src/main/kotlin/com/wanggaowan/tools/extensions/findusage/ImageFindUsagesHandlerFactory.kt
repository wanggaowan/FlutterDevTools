package com.wanggaowan.tools.extensions.findusage

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.jetbrains.lang.dart.ide.findUsages.DartServerFindUsagesHandler
import com.jetbrains.lang.dart.psi.*
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.XUtils
import io.flutter.pub.PubRoot
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType

/**
 * 图片查找使用位置
 *
 * @author Created by wanggaowan on 2024/2/27 11:26
 */
class ImageFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean {
        if (element !is PsiFile) {
            return false
        }

        val file = element.virtualFile ?: return false
        if (!XUtils.isImage(element.name)) {
            return false
        }

        val imagesDir = PluginSettings.getImagesFileDir(element.project)
        val exampleImagesDir = PluginSettings.getExampleImagesFileDir(element.project)
        return !(!file.path.contains(imagesDir) && !file.path.contains(exampleImagesDir))
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler {
        return ImageUsagesHandler(element)
    }
}

/**
 * 查找图片
 */
class ImageUsagesHandler(psiElement: PsiElement, private val findDefined: Boolean = false) :
    FindUsagesHandler(psiElement) {
    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Boolean {
        if (element !is PsiFile) {
            return false
        }

        ApplicationManager.getApplication().runReadAction {
            val file = element.virtualFile
            val pubRoot = PubRoot.forFile(file) ?: return@runReadAction
            var parent = file.parent ?: return@runReadAction
            if (XUtils.isImageVariantsFolder(parent.name)) {
                parent = parent.parent ?: return@runReadAction
            }

            val exampleDir = pubRoot.exampleDir
            val isExample = exampleDir != null && file.path.startsWith("${exampleDir.path}/")
            val dartClass: DartClass = findImagesClass(pubRoot, isExample, exampleDir) ?: return@runReadAction

            val filePath = "${parent.path}/${file.name}"
            val fileRelativePath = if (isExample) {
                filePath.replace("${exampleDir.path}/", "")
            } else {
                filePath.replace("${pubRoot.root.path}/", "")
            }

            val member = findImageFileKey(dartClass, fileRelativePath) ?: return@runReadAction
            val child =
                member.getChildOfType<DartComponentName>()?.getChildOfType<DartId>()?.firstChild ?: return@runReadAction
            if (findDefined) {
                // 是否查找定义图片引用字段位置
                val usageInfo = UsageInfo(member)
                processor.process(usageInfo)
            }

            DartServerFindUsagesHandler(child).processElementUsages(child, processor, options)
        }
        return true
    }

    private fun findImageFileKey(dartClass: DartClass, fileRelativePath: String): PsiElement? {
        dartClass.methods.forEach {
            val text = it.getChildOfType<DartFunctionBody>()
                ?.getChildOfType<DartStringLiteralExpression>()?.firstChild?.nextSibling?.text
            if (text == fileRelativePath) {
                return it
            }
        }
        return null
    }

    private fun findImagesClass(pubRoot: PubRoot, isExample: Boolean, exampleDir: VirtualFile?): DartClass? {
        if (isExample) {
            val path = PluginSettings.getExampleImagesRefFilePath(project)
            val fileName = PluginSettings.getExampleImagesRefFileName(project)
            val className = PluginSettings.getExampleImagesRefClassName(project)
            val file = VirtualFileManager.getInstance().findFileByUrl("file://${exampleDir!!.path}/$path/$fileName")
                ?: return null
            val psiFile = file.toPsiFile(project) ?: return null

            var dartClazz: DartClass? = null
            psiFile.getChildrenOfType<DartClass>().forEach {
                if (it.name == className) {
                    dartClazz = it
                    return@forEach
                }
            }
            return dartClazz
        }

        val path = PluginSettings.getImagesRefFilePath(project)
        val fileName = PluginSettings.getImagesRefFileName(project)
        val className = PluginSettings.getExampleImagesRefClassName(project)
        val file =
            VirtualFileManager.getInstance().findFileByUrl("file://${pubRoot.root.path}/$path/$fileName") ?: return null
        val psiFile = file.toPsiFile(project) ?: return null

        var dartClazz: DartClass? = null
        psiFile.getChildrenOfType<DartClass>().forEach {
            if (it.name == className) {
                dartClazz = it
                return@forEach
            }
        }
        return dartClazz
    }
}
