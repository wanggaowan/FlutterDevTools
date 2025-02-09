package com.wanggaowan.tools.extensions.refactoring

import com.intellij.ide.TitledHandler
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameHandler
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.ide.refactoring.DartServerRenameHandler
import com.wanggaowan.tools.actions.image.RenameImageHandel
import com.wanggaowan.tools.settings.PluginSettings
import com.wanggaowan.tools.utils.XUtils
import com.wanggaowan.tools.utils.ex.basePath

/**
 * 对[DartServerRenameHandler]的扩展，兼容dart文件重命名及更新引用位置内容
 *
 * @author Created by wanggaowan on 2024/3/5 15:06
 */
class ExtensionDartServerRenameHandler : RenameHandler, TitledHandler {

    private var isAllDartFile = true
    private var isAllImageFile = true

    // 由于DartServerRenameHandler改为了final，因此ExtensionDartServerRenameHandler直接实现DartServerRenameHandler实现的基类
    // 然后通过dartServerRenameHandler调用相关方法实现之前通过继承DartServerRenameHandler而执行的逻辑
    private val dartServerRenameHandler = DartServerRenameHandler()

    override fun getActionTitle(): String {
        return DartBundle.message("action.title.dart.rename.refactoring", *arrayOfNulls(0))
    }

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        isAllDartFile = true
        isAllImageFile = true
        if (editor == null) {
            val files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext) ?: return false
            for (file2 in files) {
                if (file2.isDirectory) {
                    isAllDartFile = false
                    isAllImageFile = false
                    break
                }

                val name = file2.name.lowercase()
                if (name.endsWith(".dart")) {
                    isAllImageFile = false
                } else {
                    isAllDartFile = false
                }

                if (XUtils.isImage(name)) {
                    isAllDartFile = false
                } else {
                    isAllImageFile = false
                }
            }

            if (isAllDartFile) {
                return files.size == 1
            }

            if (!isAllImageFile) {
                return false
            }

            val module = LangDataKeys.MODULE.getData(dataContext)
            if (module == null) {
                isAllImageFile = false
                return false
            }

            val basePath = module.basePath
            val imageDir = PluginSettings.getImagesFileDir(module.project)
            val file = files[0]
            return !(!file.path.startsWith("$basePath/$imageDir") && !file.path.startsWith("$basePath/example/$imageDir"))
        }

        return dartServerRenameHandler.isAvailableOnDataContext(dataContext)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, context: DataContext?) {
        if (elements.isEmpty()) {
            return
        }

        if (isAllDartFile) {
            val element = elements[0]
            DartFileRenameDialog(project, element as PsiFile).show()
            return
        }

        if (isAllImageFile) {
            RenameImageHandel(project).rename(elements.map { (it as PsiFile).virtualFile }.toTypedArray())
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile?, context: DataContext?) {
        dartServerRenameHandler.invoke(project, editor, file, context)
    }
}
