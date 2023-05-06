package com.wanggaowan.tools.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.jetbrains.lang.dart.psi.*
import com.wanggaowan.tools.entity.Property
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.StringUtils
import com.wanggaowan.tools.utils.XUtils.isImage
import com.wanggaowan.tools.utils.dart.DartPsiUtils
import com.wanggaowan.tools.utils.ex.flutterModules
import com.wanggaowan.tools.utils.ex.rootDir
import com.wanggaowan.tools.utils.flutter.YamlUtils
import io.flutter.pub.PubRoot
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

/**
 * 生成图片资源引用，并在pubspec.yaml中生成图片位置声明
 *
 * @author Created by wanggaowan on 2023/2/16 20:33
 */
class GeneratorImageRefAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.flutterModules?.forEach {
            GeneratorImageRefUtils.generate(it)
            GeneratorImageRefUtils.generate(it, true)
        }
    }

    // override fun getActionUpdateThread(): ActionUpdateThread {
    //     return ActionUpdateThread.BGT
    // }
}

object GeneratorImageRefUtils {
    /**
     * 生成图片引用文件，[isExampleModule]表示当前是否为项目中example模块生成
     */
    fun generate(module: Module?, isExampleModule: Boolean = false) {
        val projectWrapper = module?.project ?: return
        val pubRoot = PubRoot.forDirectory(module.rootDir) ?: return
        val exampleDir = pubRoot.exampleDir
        if (isExampleModule && (exampleDir == null || pubRoot.exampleLibMain == null)) {
            return
        }

        val virtualFileManager = VirtualFileManager.getInstance()
        // 图片资源在项目中的相对路径
        val imagesRelDirPath: String
        // 图片资源目录
        val imagesDir: VirtualFile?
        // 生成图片资源引用文件路径
        val imageRefFilePath: String
        // 生成图片资源引用文件名称
        val imageRefFileName: String
        // 生成图片资源引用文件类名称
        val imageRefClassName: String
        val moduleRootFile: VirtualFile
        if (isExampleModule) {
            imagesRelDirPath = PluginSettings.getExampleImagesFileDir(projectWrapper)
            imageRefFilePath = PluginSettings.getExampleImagesRefFilePath(projectWrapper)
            imageRefFileName = PluginSettings.getExampleImagesRefFileName(projectWrapper)
            imageRefClassName = PluginSettings.getExampleImagesRefClassName(projectWrapper)
            imagesDir = virtualFileManager.findFileByUrl("file://${exampleDir!!.path}/${imagesRelDirPath}")
            moduleRootFile = exampleDir
        } else {
            imagesRelDirPath = PluginSettings.getImagesFileDir(projectWrapper)
            imageRefFilePath = PluginSettings.getImagesRefFilePath(projectWrapper)
            imageRefFileName = PluginSettings.getImagesRefFileName(projectWrapper)
            imageRefClassName = PluginSettings.getImagesRefClassName(projectWrapper)
            imagesDir = virtualFileManager.findFileByUrl("file://${pubRoot.path}/${imagesRelDirPath}")
            moduleRootFile = pubRoot.root
        }

        if (imagesDir == null) {
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(projectWrapper, "Create images res ref") {
            override fun run(progressIndicator: ProgressIndicator) {
                progressIndicator.isIndeterminate = true
                WriteCommandAction.runWriteCommandAction(projectWrapper) {
                    // 写入数据之前先对文件进行保存，否则可能抛出异常：对未保存的文件进行写入
                    FileDocumentManager.getInstance().saveAllDocuments()
                    createImageRefFile(
                        projectWrapper,
                        moduleRootFile,
                        imagesDir,
                        imagesRelDirPath,
                        imageRefFilePath,
                        imageRefFileName,
                        imageRefClassName
                    )
                    progressIndicator.fraction = 0.5
                    insertAssets(projectWrapper, moduleRootFile, imagesDir, imagesRelDirPath)
                }
                progressIndicator.isIndeterminate = false
                progressIndicator.fraction = 1.0
            }
        })
    }

    // <editor-fold desc="创建Images.dart相关逻辑">

    /**
     * 创建Images.dart
     */
    private fun createImageRefFile(
        project: Project,
        moduleRootFile: VirtualFile,
        imagesDir: VirtualFile,
        imageDirRelPath: String,
        imageRefFilePath: String,
        imageRefFileName: String,
        imageRefClassName: String
    ) {
        val imagesPsiFile =
            findOrCreateResourcesDir(project, moduleRootFile, imageRefFilePath, imageRefFileName).toPsiFile(project)
                ?: return
        val classMember = findOrCreateClass(project, imagesPsiFile, imageRefClassName) ?: return
        val allImages = getDeDuplicationList(imagesDir, basePath = imageDirRelPath)
        if (allImages.isEmpty()) {
            return
        }

        removeNotExistImageNode(moduleRootFile, classMember)
        addNewImagesNode(project, classMember, allImages)
        DartPsiUtils.reformatFile(project, imagesPsiFile)
    }

    /**
     * 查找Images.dart是否存在Images类，不存在则创建，返回classMembers节点
     */
    private fun findOrCreateClass(project: Project, imagesPsiFile: PsiFile, imageRefClassName: String): PsiElement? {
        var imagesClass: PsiElement? = null
        for (child in imagesPsiFile.children) {
            if (child is DartClass) {
                val element = PsiTreeUtil.getChildOfType(child, DartComponentName::class.java)
                if (element != null && element.textMatches(imageRefClassName)) {
                    imagesClass = child
                }
            }
        }

        if (imagesClass == null) {
            imagesClass = DartPsiUtils.createClassElement(project, imageRefClassName) ?: return null
            imagesClass = imagesPsiFile.add(imagesClass)
        }

        return PsiTreeUtil.getChildOfType(imagesClass, DartClassBody::class.java)?.classMembers
    }

    /**
     * 查找或创建lib/resources/images.dart目录
     */
    private fun findOrCreateResourcesDir(
        project: Project, parent: VirtualFile,
        imageRefFilePath: String, imageRefFileName: String
    ): VirtualFile {
        val path = "$imageRefFilePath/$imageRefFileName"
        var virtualFile: VirtualFile = parent
        val nodes = path.split("/")
        nodes.indices.forEach { index ->
            val dirName = nodes[index]
            virtualFile = virtualFile.findChild(dirName)
                ?: if (index == nodes.size - 1 && dirName.contains(".")) virtualFile.createChildData(
                    project,
                    dirName
                ) else virtualFile.createChildDirectory(project, dirName)
        }
        return virtualFile
    }

    /**
     * 获取去重后的图片文件列表
     */
    private fun getDeDuplicationList(
        rootDir: VirtualFile,
        basePath: String = "",
        parentPath: String = ""
    ): LinkedHashSet<Property> {
        val childrenSet = linkedSetOf<Property>()
        val dirName = rootDir.name
        // 记录rootDir下不同分辨率的变体目录
        val variantsDir = mutableListOf<VirtualFile>()
        // 记录rootDir下除variantsDir以为的目录
        val childDir = mutableListOf<VirtualFile>()
        for (child in rootDir.children) {
            if (child.isDirectory) {
                val name = child.name
                if (name == "1.5x" || name == "2.0x" || name == "3.0x" || name == "4.0x") {
                    variantsDir.add(child)
                } else {
                    childDir.add(child)
                }
            } else if (isImage(child.name)) {
                val path = if (dirName == "1.5x" || dirName == "2.0x" || dirName == "3.0x" || dirName == "4.0x") {
                    parentPath.replace("$dirName/", "")
                } else {
                    parentPath
                }

                val value = path + child.name
                val key = getPropertyKey(value)
                childrenSet.add(Property(key, "$basePath/$value"))
            }
        }

        if (variantsDir.isNotEmpty()) {
            variantsDir.forEach {
                childrenSet.addAll(getDeDuplicationList(it, basePath, "$parentPath${it.name}/"))
            }
        }

        if (childDir.isNotEmpty()) {
            childDir.forEach {
                childrenSet.addAll(getDeDuplicationList(it, basePath, "$parentPath${it.name}/"))
            }
        }

        return childrenSet
    }

    private fun getPropertyKey(value: String): String {
        return StringUtils.lowerCamelCase(
            value.substring(0, value.lastIndexOf("."))
                .replace("/", "_")
                .replace("-", "_")
                .replace("@", ""), false
        )
    }

    /**
     * 移除Images类中已不存在的图片资源
     */
    private fun removeNotExistImageNode(moduleRootFile: VirtualFile, classMember: PsiElement) {
        val manager = VirtualFileManager.getInstance()
        // 移除不存在的数据
        classMember.children.forEach {
            if (it is DartGetterDeclaration) {
                val value = it.getChildOfType<DartFunctionBody>()?.let { child ->
                    child.getChildOfType<DartStringLiteralExpression>()?.firstChild?.nextSibling?.text
                }

                if (manager.findFileByUrl("file://${moduleRootFile.path}/$value") == null) {
                    it.delete()
                }
            }
        }
    }

    /**
     * 将新的图片资源引用插入Images类中
     */
    private fun addNewImagesNode(project: Project, classMember: PsiElement, allImages: Set<Property>) {
        allImages.forEach {
            val value = classMember.children.find { child ->
                if (child is DartGetterDeclaration) {
                    val value = child.getChildOfType<DartFunctionBody>()?.let { child2 ->
                        child2.getChildOfType<DartStringLiteralExpression>()?.firstChild?.nextSibling?.text
                    }

                    var exist = false
                    for (property in allImages) {
                        if (value == it.value) {
                            exist = true
                            break
                        }
                    }
                    exist
                } else {
                    false
                }
            }

            if (value == null) {
                DartPsiUtils.createCommonElement(project, "static String get ${it.key} => '${it.value}';")
                    ?.also { child ->
                        classMember.add(child)
                    }
            }
        }
    }
    // </editor-fold>


    /**
     * 将图片目录插入pubspec.yaml文件flutter assets节点下
     */
    private fun insertAssets(
        project: Project,
        projectFile: VirtualFile,
        imagesDir: VirtualFile,
        imagesDirRelPath: String
    ) {
        val pubspec = findOrCreatePubspec(project, projectFile)
        val pubspecPsiFile = pubspec.toPsiFile(project) ?: return

        val yamlGenerator = YAMLElementGenerator.getInstance(project)
        // 两个节点之间的分隔符
        val eolElement = yamlGenerator.createEol()
        var flutterYamlElement = YamlUtils.findElement(pubspecPsiFile, "flutter")
        if (flutterYamlElement == null) {
            flutterYamlElement = YamlUtils.createYAMLKeyValue(project, "flutter:") ?: return
            val document = pubspecPsiFile.getChildOfType<YAMLDocument>() ?: return
            val mapping = document.getChildOfType<YAMLMapping>()
            flutterYamlElement = if (mapping == null) {
                pubspecPsiFile.add(flutterYamlElement) ?: return
            } else {
                mapping.add(eolElement)
                mapping.add(flutterYamlElement) ?: return
            }
        }

        var assetsYamlElement = YamlUtils.findElement(flutterYamlElement, "assets")
        if (assetsYamlElement == null) {
            assetsYamlElement = YamlUtils.createYAMLKeyValue(project, "assets:") ?: return
            assetsYamlElement = addYamlElementNoCheck(flutterYamlElement, eolElement, assetsYamlElement)

            assetsYamlElement =
                addYamlElementNoCheck(assetsYamlElement, eolElement, yamlGenerator.createEmptySequence())
        } else {
            assetsYamlElement = assetsYamlElement.getChildOfType<YAMLSequence>() ?: addYamlElementNoCheck(
                assetsYamlElement,
                eolElement,
                yamlGenerator.createEmptySequence()
            )

            val fileManager = VirtualFileManager.getInstance()
            val basePath = "file://${project.basePath}"
            assetsYamlElement.children.forEach {
                val child = it.getChildOfType<YAMLScalar>()
                if (child != null) {
                    val value = child.textValue
                    if (value.startsWith(imagesDirRelPath)) {
                        val virtualFile = fileManager.findFileByUrl("$basePath/$value")
                        if (virtualFile == null || virtualFile.children.isEmpty()) {
                            val nextSibling = it.nextSibling
                            // 删除两个标签之间的间隙
                            if (nextSibling is LeafPsiElement) {
                                val nextSibling2 = nextSibling.nextSibling
                                if (nextSibling2 is LeafPsiElement) {
                                    nextSibling2.delete()
                                }
                                nextSibling.delete()
                            }
                            // 删除不存在的目录
                            it.delete()
                        }
                    }
                }
            }
        }

        YamlUtils.createYAMLSequenceItem(project, "- ${imagesDirRelPath}/")?.also {
            addYamlElement(assetsYamlElement, eolElement, it)
        }

        addImagesDir(project, eolElement, imagesDir, assetsYamlElement, "- ${imagesDirRelPath}/")
        DartPsiUtils.reformatFile(project, pubspecPsiFile)
    }

    /**
     * 增加图片目录到assets节点下
     */
    private fun addImagesDir(
        project: Project,
        eolElement: PsiElement,
        imagesDir: VirtualFile,
        yamlParent: PsiElement,
        parentPath: String
    ) {
        val dirs = mutableListOf<VirtualFile>()
        val variantsDirs = mutableListOf<VirtualFile>()
        imagesDir.children.forEach {
            if (it.isDirectory && it.children.isNotEmpty()) {
                YamlUtils.createYAMLSequenceItem(project, "$parentPath${it.name}/")?.also { element ->
                    addYamlElement(yamlParent, eolElement, element)
                }

                val name = it.name
                if (name == "1.5x" || name == "2.0x" || name == "3.0x" || name == "4.0x") {
                    variantsDirs.add(it)
                } else {
                    dirs.add(it)
                }
            }
        }

        variantsDirs.forEach {
            addImagesDir(project, eolElement, it, yamlParent, "$parentPath${it.name}/")
        }

        dirs.forEach {
            addImagesDir(project, eolElement, it, yamlParent, "$parentPath${it.name}/")
        }
    }


    /**
     * 查找或创建pubspec.yaml文件
     */
    private fun findOrCreatePubspec(project: Project, parent: VirtualFile): VirtualFile {
        var virtualFile = parent.findChild("pubspec.yaml")
        if (virtualFile == null) {
            virtualFile = parent.createChildData(project, "pubspec.yaml")
        }
        return virtualFile
    }

    /**
     * 新增yaml节点，不存在时才添加
     */
    private fun addYamlElement(parent: PsiElement, eolElement: PsiElement, child: PsiElement) {
        var exist = false
        for (child2 in parent.children) {
            if (child2.textMatches(child)) {
                exist = true
                break
            }
        }

        if (!exist) {
            addYamlElementNoCheck(parent, eolElement, child)
        }
    }

    /**
     * 新增yaml节点，不存在时才添加
     */
    private fun addYamlElementNoCheck(parent: PsiElement, eolElement: PsiElement, child: PsiElement): PsiElement {
        val lastChild = parent.lastChild
        val debugName = lastChild.elementType?.debugName
        return if (debugName == "indent" || debugName == "Eol") {
            parent.add(child)
        } else {
            parent.add(eolElement)
            parent.add(child)
        }
    }
}
