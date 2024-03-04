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
import com.jetbrains.lang.dart.psi.DartClass
import com.jetbrains.lang.dart.psi.DartComponentName
import com.jetbrains.lang.dart.psi.DartId
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.StringUtils
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
class ImageFindUsagesHandlerFactory(private val findDefined: Boolean = true) : FindUsagesHandlerFactory() {
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
        return ImageUsagesHandler(element,findDefined)
    }
}

/**
 * 查找图片
 */
class ImageUsagesHandler(psiElement: PsiElement, private val findDefined: Boolean = true) :
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
            val parent = file.parent ?: return@runReadAction

            val exampleImagesDir = PluginSettings.getExampleImagesFileDir(element.project)
            val exampleDir = pubRoot.exampleDir
            val isExample = exampleDir != null && file.path.startsWith("${exampleDir.path}/$exampleImagesDir")

            val dartClass: DartClass = findImagesClass(pubRoot, isExample, exampleDir) ?: return@runReadAction

            val endDirName = if (isExample) {
                val splits = exampleImagesDir.split("/")
                splits[splits.size - 1]
            } else {
                val imagesDir = PluginSettings.getImagesFileDir(element.project)
                val splits = imagesDir.split("/")
                splits[splits.size - 1]
            }

            val parentName = parent.name
            var fileRelativePath =
                if (XUtils.isImageVariantsFolder(parentName)) {
                    file.name
                } else {
                    "$parentName/${file.name}"
                }
            fileRelativePath = getFileRelativePath(fileRelativePath, parent, endDirName)

            val member = dartClass.findMemberByName(getPropertyKey(fileRelativePath)) ?: return@runReadAction
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

    private fun getPropertyKey(value: String): String {
        return StringUtils.lowerCamelCase(
            value.substring(0, value.lastIndexOf("."))
                .replace("/", "_")
                .replace("-", "_")
                .replace("@", ""), false
        )
    }

    private fun getFileRelativePath(basePath: String, file: VirtualFile, endDirName: String): String {
        val parent: VirtualFile = file.parent ?: return basePath
        val parentName = parent.name
        if (parentName == endDirName) {
            return basePath
        }
        return getFileRelativePath("$parentName/$basePath", parent, endDirName)
    }
}
