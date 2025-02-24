package com.wanggaowan.tools.ui

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.TreeFileChooser
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ArrayUtil
import com.intellij.util.Function
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

/**
 * 文件选择器，通过[fileType]指定要选择的文件类型,[initialFile]指定默认选中的文件,[filter]则为过滤器，用于筛选哪些内容需要显示或隐藏
 *
 * @author Created by wanggaowan on 2023/2/13 15:02
 */
class FileChooseDialog(
    val project: Project,
    val fileType: Int = CHOSE_FILE,
    private val initialFile: VirtualFile? = null,
    private val filter: TreeFileChooser.PsiFileFilter? = null
) : DialogWrapper(project, false) {

    private var mBuilder: StructureTreeModel<ProjectAbstractTreeStructureBase>? = null
    private lateinit var myTree: Tree

    /**
     * 选中的文件
     */
    var selectedFile: VirtualFile? = null
        private set

    init {
        init()
        okAction.isEnabled = false
        if (initialFile != null) {
            // dialog does not exist yet
            SwingUtilities.invokeLater { selectedFile(initialFile) }
        }
    }

    override fun createCenterPanel(): JComponent {
        return createFileChoosePanel()
    }

    /**
     * 构建文件选择面板
     */
    private fun createFileChoosePanel(): JComponent {
        val treeStructure: ProjectAbstractTreeStructureBase = object : AbstractProjectTreeStructure(project) {
            override fun isHideEmptyMiddlePackages(): Boolean {
                return true
            }

            override fun getChildElements(element: Any): Array<Any> {
                return filterFiles(super.getChildElements(element))
            }

            override fun isShowLibraryContents(): Boolean {
                return false
            }

            override fun isShowModules(): Boolean {
                return false
            }
        }

        val model = StructureTreeModel(treeStructure, disposable)
        model.setComparator(AlphaComparator.getInstance())
        mBuilder = model
        myTree = Tree(AsyncTreeModel(model, disposable))
        myTree.isRootVisible = false
        myTree.expandRow(0)
        myTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        myTree.cellRenderer = NodeRenderer()
        val scrollPane = ScrollPaneFactory.createScrollPane(myTree)
        scrollPane.preferredSize = JBUI.size(600, 300)
        myTree.addTreeSelectionListener { handleSelectionChanged() }
        return scrollPane
    }

    private fun handleSelectionChanged() {
        selectedFile = isChosenFile()
        myOKAction.isEnabled = selectedFile != null
    }

    private fun isChosenFile(): VirtualFile? {
        val path = myTree.selectionPath ?: return null
        val node = path.lastPathComponent as DefaultMutableTreeNode
        val userObject = node.userObject as? ProjectViewNode<*> ?: return null
        return userObject.virtualFile
    }

    private fun filterFiles(list: Array<*>): Array<Any> {
        val condition = Condition { psiFile: PsiFile ->
            if (fileType == CHOSE_FILE && psiFile.isDirectory) {
                return@Condition false
            } else if (fileType == CHOSE_DIR && !psiFile.isDirectory) {
                return@Condition false
            } else if (filter != null && !filter.accept(psiFile)) {
                return@Condition false
            }

            true
        }

        val result: MutableList<Any?> = ArrayList(list.size)
        for (obj in list) {
            val psiFile: PsiFile? = when (obj) {
                is PsiFile -> {
                    obj
                }

                is PsiFileNode -> {
                    obj.value
                }

                else -> {
                    null
                }
            }
            if (psiFile != null && !condition.value(psiFile)) {
                continue
            } else if (obj is ProjectViewNode<*>) {
                if (obj.value !is PsiDirectory) {
                    // 只接收APP模块
                    continue
                }
            }
            result.add(obj)
        }
        return ArrayUtil.toObjectArray(result)
    }

    /**
     * 选中文件
     */
    fun selectedFile(file: VirtualFile) {
        // Select element in the tree
        ApplicationManager.getApplication().invokeLater({
            selectedFileInner(file)
        }, ModalityState.stateForComponent(window))
    }

    override fun doCancelAction() {
        selectedFile = null
        super.doCancelAction()
    }

    private fun selectedFileInner(file: VirtualFile) {
        TreeUtil.promiseSelect(myTree,
            object : TreeVisitor.ByComponent<VirtualFile, DefaultMutableTreeNode>(file, Function {
                if (it !is DefaultMutableTreeNode) {
                    return@Function null
                }

                return@Function it
            }) {

                override fun visit(node: DefaultMutableTreeNode?): TreeVisitor.Action {
                    if (node == null) {
                        return TreeVisitor.Action.SKIP_CHILDREN
                    }

                    val userObject = node.userObject ?: return TreeVisitor.Action.SKIP_CHILDREN
                    if (userObject is ProjectViewProjectNode) {
                        return TreeVisitor.Action.CONTINUE
                    }

                    if (userObject is PsiDirectoryNode) {
                        val file2 = userObject.virtualFile ?: return TreeVisitor.Action.SKIP_CHILDREN
                        val path = file.path
                        val path2 = file2.path
                        if (path2 == path) {
                            return TreeVisitor.Action.INTERRUPT
                        }

                        if (path.startsWith(path2)) {
                            return TreeVisitor.Action.CONTINUE
                        }

                        return TreeVisitor.Action.SKIP_CHILDREN
                    }

                    return TreeVisitor.Action.SKIP_CHILDREN
                }

                override fun contains(pathComponent: DefaultMutableTreeNode, thisComponent: VirtualFile): Boolean {
                    return false
                }
            }).onProcessed {

        }
    }

    companion object {
        /**
         * 选中文件
         */
        const val CHOSE_FILE = 0

        /**
         * 选择目录
         */
        const val CHOSE_DIR = 1

        /**
         * 选择文件和目录
         */
        const val CHOSE_FILE_AND_DIR = 2
    }
}
