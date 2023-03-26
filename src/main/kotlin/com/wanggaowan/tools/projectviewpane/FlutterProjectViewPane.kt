package com.wanggaowan.tools.projectviewpane

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.ide.SelectInTarget
import com.intellij.ide.StandardTargetWeights
import com.intellij.ide.impl.ProjectViewSelectInTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase
import com.intellij.ide.projectView.impl.ProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.nodes.*
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.wanggaowan.tools.utils.ex.isFlutterProject
import icons.FlutterIcons
import javax.swing.Icon

/**
 * Flutter项目结构，仅展示项目中的Flutter模块及开发Flutter时关注的文件
 *
 * @author Created by wanggaowan on 2023/2/3 09:48
 */
class FlutterProjectViewPane(private val project: Project) : ProjectViewPane(project) {
    private val id = "FlutterDevTools.FlutterPane"

    override fun getTitle(): String {
        return "Flutter"
    }

    /**
     * 判断项目视图是否展示
     */
    override fun isInitiallyVisible(): Boolean {
        val modules = project.modules
        if (modules.isEmpty()) {
            return false
        }

        for (module in modules) {
            if (module.isFlutterProject) {
                return true
            }
        }

        return false
    }

    override fun getIcon(): Icon {
        return FlutterIcons.Flutter
    }

    override fun getId(): String {
        return id
    }

    override fun getComponentName(): String {
        return "Flutter"
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

/**
 * 项目文件结构
 */
private class ProjectViewPaneTreeStructure(val project: Project, val id: String) :
    ProjectTreeStructure(project, id), ProjectViewSettings {

    /**
     * 创建项目视图树根节点
     */
    override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> {
        return InnerProjectViewProjectNode(project, settings)
    }

    /**
     * 是否展示排除的文件
     */
    override fun isShowExcludedFiles(): Boolean {
        return false
    }

    override fun isShowLibraryContents(): Boolean {
        return false
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

/**
 * 项目视图树节点
 */
private class InnerProjectViewProjectNode(project: Project, viewSettings: ViewSettings) :
    ProjectViewProjectNode(project, viewSettings) {

    /**
     * 获取需要展示的模块
     */
    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val project = myProject
        if (project == null || project.isDisposed || project.isDefault) {
            return emptyList()
        }

        val topLevelContentRoots = ProjectViewDirectoryHelper.getInstance(project).topLevelRoots
        val modules: MutableSet<LoadedModuleDescriptionImpl> = LinkedHashSet(topLevelContentRoots.size)
        for (root in topLevelContentRoots) {
            if (root.isFlutterProject) {
                val module = ModuleUtilCore.findModuleForFile(root!!, project)
                if (module != null) {
                    modules.add(LoadedModuleDescriptionImpl(module))
                }
            }
        }

        @Suppress("UnstableApiUsage")
        return ArrayList(modulesAndGroups(modules))
    }

    /**
     * 创建每个模块的文件树
     */
    override fun createModuleGroup(module: Module): AbstractTreeNode<*> {
        return createModuleNodeInner(myProject, module, settings)
    }

    override fun createModuleGroupNode(moduleGroup: ModuleGroup): AbstractTreeNode<*> {
        return InnerProjectViewModuleGroupNode(project, moduleGroup, settings)
    }
}

/**
 * 多个模块分组视图树节点
 */
private class InnerProjectViewModuleGroupNode(project: Project, value: ModuleGroup, viewSettings: ViewSettings) :
    ProjectViewModuleGroupNode(project, value, viewSettings) {

    override fun getChildren(): MutableCollection<AbstractTreeNode<*>> {
        val childGroups = value.childGroups(project)
        val result: MutableList<AbstractTreeNode<*>> = java.util.ArrayList()
        for (childGroup in childGroups) {
            result.add(createModuleGroupNode(childGroup!!))
        }

        val modules = value.modulesInGroup(project)
        try {
            for (module in modules) {
                if (module.isFlutterProject) {
                    result.add(createModuleNode(module))
                }
            }
        } catch (e: ReflectiveOperationException) {
            LOG.error(e)
        }

        return result
    }

    override fun createModuleGroupNode(moduleGroup: ModuleGroup): ModuleGroupNode {
        return InnerProjectViewModuleGroupNode(project, moduleGroup, settings)
    }

    override fun createModuleNode(module: Module): AbstractTreeNode<*> {
        return createModuleNodeInner(myProject, module, settings)
    }
}

/**
 * 模块视图树节点
 */
private class InnerProjectViewModuleNode(project: Project, module: Module, viewSettings: ViewSettings) :
    ProjectViewModuleNode(project, module, viewSettings) {
    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val module = value
        if (module == null || module.isDisposed || !module.isFlutterProject) {  // module has been disposed
            return emptyList()
        }

        return super.getChildren()
    }

    override fun contains(file: VirtualFile): Boolean {
        return fileCouldShow(file)
    }
}

/**
 * 创建模块节点树
 */
private fun createModuleNodeInner(project: Project, module: Module, viewSettings: ViewSettings): AbstractTreeNode<*> {
    // 获取模块节点
    val first = ModuleRootManager.getInstance(module).contentRoots.first()
    if (first != null) {
        val psi = PsiManager.getInstance(project).findDirectory(first)
        if (psi != null) {
            return PsiDirectoryNode(project, psi, viewSettings) { item ->
                // 过滤模块下文件，将不需要展示的文件剔除
                fileCouldShow(item.virtualFile)
            }
        }
    }

    return InnerProjectViewModuleNode(project, module, viewSettings)
}

/**
 * 判断文件是否需要展示
 */
private fun fileCouldShow(file: VirtualFile?): Boolean {
    if (file != null) {
        val name = file.name
        if (name.endsWith(".lock")
            || name.endsWith(".log")
            || name.endsWith(".iml")
            || name.startsWith(".")
            || name.startsWith("_")
        ) {
            return false
        }

        if (file.isDirectory
            && (name == "android"
                || name == "ios"
                || name == "web"
                || name == "build")
        ) {
            return false
        }

        if (name == "") {
            return false
        }
    }
    return true
}

/**
 * 处理文件选中操作
 */
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
