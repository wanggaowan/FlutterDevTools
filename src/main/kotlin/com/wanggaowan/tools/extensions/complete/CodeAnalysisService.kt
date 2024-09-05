package com.wanggaowan.tools.extensions.complete

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.lang.dart.psi.*
import com.jetbrains.lang.dart.psi.impl.DartMixinDeclarationImpl
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.ex.basePath
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.ex.rootDir
import com.wanggaowan.tools.utils.flutter.YamlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.dartlang.analysis.server.protocol.ElementKind
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import kotlin.collections.set

/**
 * 代码分析
 *
 * @author Created by wanggaowan on 2023/12/29 11:22
 */
@Service(Service.Level.PROJECT)
class CodeAnalysisService(val project: Project) {
    private val parsedLib = mutableMapOf<String, MutableList<String>>()
    private val topElement = mutableMapOf<String, MutableSet<Suggestion>>()

    val isAvailable
        get() = topElement.isNotEmpty() && (PluginSettings.getCodeCompleteTypeDirectDev(project)
            || PluginSettings.getCodeCompleteTypeDirectDev(project))

    fun startAnalysis(module: Module) {
        if (project.isDisposed) {
            return
        }

        val parseDev = PluginSettings.getCodeCompleteTypeDirectDev(project)
        val parseTransitive = PluginSettings.getCodeCompleteTypeTransitive(project)
        if (!parseDev && !parseTransitive) {
            return
        }

        val basePath = module.basePath ?: return
        val pubspecLock = module.rootDir?.findChild("pubspec.lock") ?: return
        ApplicationManager.getApplication().runReadAction {
            if (project.isDisposed) {
                return@runReadAction
            }

            val psiFile = pubspecLock.toPsiFile(project) ?: return@runReadAction
            val pubspecLockPsi = if (psiFile !is YAMLFile) {
                // IDE可能会把.lock解析为普通文本，因此主动创建虚拟YAMLFile
                YamlUtils.createDummyFile(project, psiFile.text)
            } else {
                psiFile
            }

            val packages = YamlUtils.findElement(pubspecLockPsi, "packages") ?: return@runReadAction
            ModuleRootManager.getInstance(module).orderEntries().classesRoots.forEach {
                val libPath = it.path
                var list = parsedLib[basePath]
                if (list != null && list.indexOf(libPath) != -1) {
                    // 此库已经解析
                    return@forEach
                }

                val pubspec = it.findChild("pubspec.yaml")?.toPsiFile(project) ?: return@forEach
                val libNameElement = YamlUtils.findElement(pubspec, "name") ?: return@forEach
                if (libNameElement !is YAMLKeyValue) {
                    return@forEach
                }

                val libName = libNameElement.valueText
                val lib = YamlUtils.findElement(packages, libName) ?: return@forEach
                val dependency = YamlUtils.findElement(lib, "dependency") ?: return@forEach
                if (dependency !is YAMLKeyValue) {
                    return@forEach
                }

                val dependencyValue = dependency.valueText
                var couldParse = false
                if (parseDev && dependencyValue == Suggestion.LIB_TYPE_DEV) {
                    couldParse = true
                } else if (parseTransitive && dependencyValue == Suggestion.LIB_TYPE_TRANSITIVE) {
                    couldParse = true
                }

                if (!couldParse) {
                    // 不是dev_dependencies和传递性依赖不处理，Dart官方插件会处理
                    return@forEach
                }

                val libDir = it.findChild("lib") ?: return@forEach

                if (list == null) {
                    list = mutableListOf()
                    parsedLib[basePath] = list
                }
                list.add(libPath)

                parseDir(basePath, libDir, libDir.path, libName, dependencyValue)
            }
        }
    }

    @Suppress("UnsafeVfsRecursion")
    private fun parseDir(
        modulePath: String,
        file: VirtualFile,
        rootDirPath: String,
        libName: String,
        libType: String
    ) {
        if (project.isDisposed) {
            return
        }

        file.children?.forEach { child2 ->
            if (child2.isDirectory) {
                parseDir(modulePath, child2, rootDirPath, libName, libType)
            } else {
                val file2 = child2.toPsiFile(project)
                if (file2 is DartFile) {
                    if (file2.name == "field_convert.dart") {
                        file2.children.forEach { element ->
                            addElement(modulePath, child2, element, rootDirPath, libName, libType)
                        }
                    } else {
                        file2.children.forEach { element ->
                            addElement(modulePath, child2, element, rootDirPath, libName, libType)
                        }
                    }
                }
            }
        }
    }

    private fun addElement(
        modulePath: String,
        parent: VirtualFile,
        element: PsiElement,
        rootDirPath: String,
        libName: String,
        libType: String,
        isExtension: Boolean = false,
    ) {

        var name: String? = null
        var kind: String = ElementKind.UNKNOWN
        var isAbstract = false
        var returnType: String? = null
        var params: String? = null
        when (element) {
            is DartExtensionDeclaration -> {
                val body = element.classBody.getChildOfType<DartClassMembers>()
                body?.children?.forEach { element2 ->
                    addElement(modulePath, parent, element2, rootDirPath, libName, libType, true)
                }
            }

            is DartMixinDeclarationImpl -> {
                name = element.name
                kind = ElementKind.MIXIN
            }

            is DartEnumDefinition -> {
                name = element.name
                kind = ElementKind.ENUM
            }

            is DartClass -> {
                name = element.name
                kind = ElementKind.CLASS
                if (name == null || name.startsWith("_")) {
                    return
                }
                isAbstract = element.getChildrenOfType<LeafPsiElement>().find { it.text == "abstract" } != null
            }

            is DartFunctionTypeAlias -> {
                name = element.name
                if (name == null || name.startsWith("_")) {
                    return
                }

                kind = ElementKind.FUNCTION_TYPE_ALIAS
                val type = element.getChildOfType<DartType>()
                    ?.getChildOfType<DartTypedFunctionType>()
                    ?.getChildOfType<DartSimpleType>()
                returnType = if (type == null) {
                    "dynamic"
                } else {
                    type.text
                }
            }

            is DartGetterDeclaration -> {
                name = element.name
                kind = ElementKind.GETTER
                if (name == null || name.startsWith("_")) {
                    return
                }

                val type = element.getChildOfType<DartReturnType>()
                returnType = if (type == null) {
                    "dynamic"
                } else {
                    type.text
                }
            }

            is DartSetterDeclaration -> {
                name = element.name
                kind = ElementKind.SETTER
                if (name == null || name.startsWith("_")) {
                    return
                }

                params = element.getChildOfType<DartFormalParameterList>()?.text
            }

            is DartVarDeclarationList -> {
                val declare = element.getChildOfType<DartVarAccessDeclaration>() ?: return
                name = declare.getChildOfType<DartComponentName>()?.text
                kind = ElementKind.TOP_LEVEL_VARIABLE
                if (name == null || name.startsWith("_")) {
                    return
                }

                returnType = declare.getChildOfType<DartType>()?.text
            }

            is DartFunctionDeclarationWithBody -> {
                name = element.name
                kind = ElementKind.FUNCTION
                if (name == null || name.startsWith("_")) {
                    return
                }

                val type = element.getChildOfType<DartReturnType>()
                returnType = if (type == null) {
                    "dynamic"
                } else {
                    type.text
                }
                params = element.getChildOfType<DartFormalParameterList>()?.text
            }

            is DartFunctionDeclarationWithBodyOrNative -> {
                name = element.name
                kind = ElementKind.FUNCTION
                if (name == null || name.startsWith("_")) {
                    return
                }

                val type = element.getChildOfType<DartReturnType>()
                returnType = if (type == null) {
                    "dynamic"
                } else {
                    type.text
                }
                params = element.getChildOfType<DartFormalParameterList>()?.text
            }

            is DartMethodDeclaration -> {
                name = element.name
                kind = ElementKind.FUNCTION
                if (name == null || name.startsWith("_")) {
                    return
                }

                val type = element.getChildOfType<DartReturnType>()
                returnType = if (type == null) {
                    "dynamic"
                } else {
                    type.text
                }
                params = element.getChildOfType<DartFormalParameterList>()?.text
            }
        }

        if (name == null) {
            return
        }

        if (name.startsWith("_")) {
            return
        }

        var libraryUriToImport = parent.path.replace(rootDirPath, "")
        libraryUriToImport = "package:$libName$libraryUriToImport"

        var list: MutableSet<Suggestion>? = topElement[modulePath]
        if (list == null) {
            list = mutableSetOf()
            topElement[modulePath] = list
        }
        val suggestion = Suggestion(
            name,
            parent,
            libraryUriToImport,
            kind,
            isAbstract,
            !isExtension,
            libType,
            isExtension,
            returnType,
            params
        )
        list.add(suggestion)
    }

    fun getSuggestions(module: Module): Set<Suggestion>? {
        val path = module.basePath ?: return null
        return topElement[path]
    }

    companion object {
        fun getInstance(project: Project): CodeAnalysisService {
            return project.getService(CodeAnalysisService::class.java)
        }

        fun startAnalysisModules(project: Project, modules: List<Module>) {
            if (project.isDisposed) {
                return
            }

            CoroutineScope(Dispatchers.Default).launch {
                val codeAnalysisService = getInstance(project)
                for (module in modules) {
                    val isFlutterModule = module.isFlutterProject
                    if (isFlutterModule) {
                        codeAnalysisService.startAnalysis(module)
                    }
                }
            }
        }
    }
}

class Suggestion(
    val name: String,
    @Transient
    val parent: VirtualFile,
    val libraryUriToImport: String?,
    val kind: String,
    // 是否是抽象类
    val isAbstract: Boolean = false,
    // 是否是静态方法，静态字段等
    val isStatic: Boolean = false,
    val libType: String,
    val isExtension: Boolean = false,
    // 方法的返回类型
    val returnType: String? = null,
    // set方法参数内容
    val params: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Suggestion) return false

        if (name != other.name) return false
        if (parent != other.parent) return false
        if (libraryUriToImport != other.libraryUriToImport) return false
        if (kind != other.kind) return false
        if (isAbstract != other.isAbstract) return false
        if (isStatic != other.isStatic) return false
        if (libType != other.libType) return false
        if (isExtension != other.isExtension) return false
        if (returnType != other.returnType) return false
        if (params != other.params) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + parent.hashCode()
        result = 31 * result + (libraryUriToImport?.hashCode() ?: 0)
        result = 31 * result + kind.hashCode()
        result = 31 * result + isAbstract.hashCode()
        result = 31 * result + isStatic.hashCode()
        result = 31 * result + libType.hashCode()
        result = 31 * result + isExtension.hashCode()
        result = 31 * result + returnType.hashCode()
        result = 31 * result + params.hashCode()
        return result
    }

    companion object {
        const val LIB_TYPE_DEV = "direct dev"
        const val LIB_TYPE_TRANSITIVE = "transitive"
    }
}
