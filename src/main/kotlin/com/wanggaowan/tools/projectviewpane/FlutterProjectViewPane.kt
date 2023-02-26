package com.wanggaowan.tools.projectviewpane

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.ide.SelectInTarget
import com.intellij.ide.StandardTargetWeights
import com.intellij.ide.impl.ProjectViewSelectInTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase
import com.intellij.ide.projectView.impl.ProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.nodes.*
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleDescription
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.isFlutterProject
import icons.FlutterIcons
import icons.SdkIcons
import javax.swing.Icon

/**
 * Flutter项目结构，仅展示开发RN时关注的文件
 *
 * @author Created by wanggaowan on 2023/2/3 09:48
 */
class FlutterProjectViewPane(private val project: Project) : ProjectViewPane(project) {
    private val id = "Flutter Pane"

    override fun getTitle(): String {
        return "Flutter"
    }

    /**
     * 判断项目视图是否展示
     */
    override fun isInitiallyVisible(): Boolean {
        return project.isFlutterProject
    }

    override fun getIcon(): Icon {
        return FlutterIcons.Flutter
    }

    override fun getId(): String {
        return id
    }

    override fun getComponentName(): String {
        return id
    }

    override fun getWeight(): Int {
        return 10
    }

    override fun createStructure(): ProjectAbstractTreeStructureBase {
        return ProjectViewPaneTreeStructure(project, id)
    }

    override fun createSelectInTarget(): SelectInTarget {
        return ProjectPaneSelectInTarget(project, id)
    }
}

private class ProjectViewPaneTreeStructure(val project: Project, val id: String) :
    ProjectTreeStructure(project, id), ProjectViewSettings {
    override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> {
        return InnerProjectViewProjectNode(project, settings)
    }

    override fun isShowExcludedFiles(): Boolean {
        return ProjectView.getInstance(project).isShowExcludedFiles(id)
    }

    override fun isShowLibraryContents(): Boolean {
        return true
    }

    override fun isShowVisibilityIcons(): Boolean {
        return ProjectView.getInstance(project).isShowVisibilityIcons(id)
    }

    override fun isUseFileNestingRules(): Boolean {
        return ProjectView.getInstance(project).isUseFileNestingRules(id)
    }

    override fun isToBuildChildrenInBackground(element: Any): Boolean {
        return Registry.`is`("ide.projectView.ProjectViewPaneTreeStructure.BuildChildrenInBackground")
    }
}

private class InnerProjectViewProjectNode(project: Project, viewSettings: ViewSettings) :
    ProjectViewProjectNode(project, viewSettings) {
    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        // 获取项目所有模块
        val project = myProject
        if (project == null || project.isDisposed || project.isDefault) {
            return emptyList()
        }

        val topLevelContentRoots = ProjectViewDirectoryHelper.getInstance(project).topLevelRoots
        val modules: MutableSet<ModuleDescription> = LinkedHashSet(topLevelContentRoots.size)
        for (root in topLevelContentRoots) {
            val module = ModuleUtilCore.findModuleForFile(root!!, project)
            if (module != null) {
                modules.add(LoadedModuleDescriptionImpl(module))
            }
        }

        return ArrayList(modulesAndGroups(modules))
    }

    override fun createModuleGroup(module: Module): AbstractTreeNode<*> {
        // 获取模块节点
        val first = ModuleRootManager.getInstance(module).contentRoots.first()
        if (first != null) {
            val psi = PsiManager.getInstance(myProject).findDirectory(first)
            if (psi != null) {
                return PsiDirectoryNode(myProject, psi, settings, PsiFileSystemItemFilter { item ->
                    // 过滤模块下文件，将不需要展示的文件剔除
                    val file: VirtualFile? = item.virtualFile
                    if (file != null) {
                        val name = file.name
                        if (name.endsWith(".lock")
                            || name.endsWith(".log")
                            || name.endsWith(".iml")
                            || name.startsWith(".")
                            || name.startsWith("_")
                        ) {
                            return@PsiFileSystemItemFilter false
                        }

                        if (file.isDirectory
                            && (name == "android"
                                || name == "ios"
                                || name == "web"
                                || name == "build")
                        ) {
                            return@PsiFileSystemItemFilter false
                        }

                        if (name == "") {
                            return@PsiFileSystemItemFilter false
                        }
                    }
                    true
                })
            }
        }

        return ProjectViewModuleNode(project, module, settings)
    }
}

private class ProjectPaneSelectInTarget(project: Project, val id: String) : ProjectViewSelectInTarget(project),
    DumbAware {
    override fun toString(): String {
        return SelectInManager.getProject()
    }

    override fun isSubIdSelectable(subId: String, context: SelectInContext): Boolean {
        return canSelect(context)
    }

    override fun getMinorViewId(): String {
        return id
    }

    override fun getWeight(): Float {
        return StandardTargetWeights.PROJECT_WEIGHT
    }
}
