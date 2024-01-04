package com.wanggaowan.tools.extensions.complete

import com.intellij.openapi.application.ApplicationManager
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
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * 代码分析
 *
 * @author Created by wanggaowan on 2023/12/29 11:22
 */
class CodeAnalysisService(val project: Project) {
    private val topElement = mutableMapOf<String, MutableSet<Suggestion>>()

    val isAvailable
        get() = topElement.isNotEmpty()

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

            val pubspecLockPsi = pubspecLock.toPsiFile(project) ?: return@runReadAction
            val packages = YamlUtils.findElement(pubspecLockPsi, "packages") ?: return@runReadAction
            ModuleRootManager.getInstance(module).orderEntries().classesRoots.forEach {
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
                if (parseDev && dependencyValue == "direct dev") {
                    couldParse = true
                } else if (parseTransitive && dependencyValue == "transitive") {
                    couldParse = true
                }

                if (!couldParse) {
                    // 不是dev_dependencies和传递性依赖不处理，Dart官方插件会处理
                    return@forEach
                }

                val libDir = it.findChild("lib") ?: return@forEach
                parseDir(basePath, libDir, libDir.path, libName)
            }
        }
    }

    private fun parseDir(key: String, file: VirtualFile, rootDirPath: String, libName: String) {
        if (project.isDisposed) {
            return
        }

        file.children?.forEach { child2 ->
            if (child2.isDirectory) {
                parseDir(key, child2, rootDirPath, libName)
            } else {
                val file2 = child2.toPsiFile(project)
                if (file2 is DartFile) {
                    file2.children.forEach { element ->
                        addElement(key, child2, element, rootDirPath, libName)
                    }
                }
            }
        }
    }

    private fun addElement(
        key: String,
        parent: VirtualFile,
        element: PsiElement,
        rootDirPath: String,
        libName: String
    ) {

        var name: String? = null
        var kind: String = ElementKind.UNKNOWN
        var isAbstract = false
        when (element) {
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
                isAbstract = element.getChildrenOfType<LeafPsiElement>().find { it.text == "abstract" } != null
            }

            is DartFunctionTypeAlias -> {
                name = element.name
                kind = ElementKind.FUNCTION_TYPE_ALIAS
            }

            is DartGetterDeclaration -> {
                name = element.name
                kind = ElementKind.GETTER
            }

            is DartSetterDeclaration -> {
                name = element.name
                kind = ElementKind.SETTER
            }

            is DartVarDeclarationList -> {
                name = element.name
                kind = ElementKind.TOP_LEVEL_VARIABLE
            }

            is DartFunctionDeclarationWithBody -> {
                name = element.name
                kind = ElementKind.FUNCTION
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

        var list: MutableSet<Suggestion>? = topElement[key]
        if (list == null) {
            list = mutableSetOf()
            topElement[key] = list
        }
        val suggestion = Suggestion(
            name,
            parent,
            element,
            libraryUriToImport,
            kind,
            isAbstract
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
    val parent: VirtualFile,
    val psiElement: PsiElement,
    val libraryUriToImport: String?,
    val kind: String,
    val isAbstract: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Suggestion) return false

        if (name != other.name) return false
        if (parent != other.parent) return false
        if (psiElement != other.psiElement) return false
        if (libraryUriToImport != other.libraryUriToImport) return false
        if (kind != other.kind) return false
        if (isAbstract != other.isAbstract) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + parent.hashCode()
        result = 31 * result + psiElement.hashCode()
        result = 31 * result + (libraryUriToImport?.hashCode() ?: 0)
        result = 31 * result + kind.hashCode()
        result = 31 * result + isAbstract.hashCode()
        return result
    }
}
