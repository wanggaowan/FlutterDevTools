package com.wanggaowan.tools.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.wanggaowan.tools.utils.ex.isFlutterProject
import com.wanggaowan.tools.utils.ex.rootDir
import io.flutter.pub.PubRoot
import javax.swing.JComponent

/**
 * 项目插件设置界面配置
 *
 * @author Created by wanggaowan on 2023/3/5 16:17
 */
class ProjectPluginSettingsConfigurable(val project: Project) : Configurable {
    private var mSettingsView: PluginSettingsView? = null
    private val pubRoot: PubRoot? = PubRoot.forDirectory(project.rootDir)

    override fun getDisplayName(): String {
        return "FlutterDevTools"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return mSettingsView!!.preferredFocusedComponent
    }

    override fun createComponent(): JComponent {
        mSettingsView = PluginSettingsView(pubRoot)
        return mSettingsView!!.panel
    }

    override fun isModified(): Boolean {
        if (PluginSettings.getCopyAndroidStrUseSimpleMode(getProjectWrapper()) != mSettingsView?.copyAndroidStrUseSimpleMode?.isSelected) {
            return true
        }

        if (isExtractStr2L10nModified()) {
            return true
        }

        return isCreateResourceModified()
    }

    private fun isCreateResourceModified(): Boolean {
        val modified = PluginSettings.getImagesFileDir(getProjectWrapper()) != mSettingsView?.imagesDir?.text
            || PluginSettings.getImagesRefFilePath(getProjectWrapper()) != mSettingsView?.imagesRefFilePath?.text
            || PluginSettings.getImagesRefFileName(getProjectWrapper()) != mSettingsView?.imagesRefFileName?.text
            || PluginSettings.getImagesRefClassName(getProjectWrapper()) != mSettingsView?.imagesRefClassName?.text
        if (modified) {
            return true
        }

        if (pubRoot?.exampleDir == null) {
            return false
        }

        return PluginSettings.getExampleImagesFileDir(getProjectWrapper()) != mSettingsView?.exampleImagesDir?.text
            || PluginSettings.getExampleImagesRefFilePath(getProjectWrapper()) != mSettingsView?.exampleImagesRefFilePath?.text
            || PluginSettings.getExampleImagesRefFileName(getProjectWrapper()) != mSettingsView?.exampleImagesRefFileName?.text
            || PluginSettings.getExampleImagesRefClassName(getProjectWrapper()) != mSettingsView?.exampleImagesRefClassName?.text
    }

    private fun isExtractStr2L10nModified(): Boolean {
        if (PluginSettings.getExtractStr2L10nShowRenameDialog(getProjectWrapper()) != mSettingsView?.extractStr2L10nShowRenameDialog?.isSelected) {
            return true
        }

        if (PluginSettings.getExtractStr2L10nTranslateOther(getProjectWrapper()) != mSettingsView?.extractStr2L10nTranslateOther?.isSelected) {
            return true
        }

        return false
    }

    override fun apply() {
        applyCreateResourceSet()
        applyExtractStr2L10n()
        PluginSettings.setCopyAndroidStrUseSimpleMode(
            getProjectWrapper(),
            mSettingsView?.copyAndroidStrUseSimpleMode?.isSelected ?: true
        )

    }

    private fun applyCreateResourceSet() {
        PluginSettings.setImagesFileDir(
            getProjectWrapper(),
            mSettingsView?.imagesDir?.text ?: PluginSettings.DEFAULT_IMAGE_DIR
        )
        PluginSettings.setImagesRefFilePath(
            getProjectWrapper(),
            mSettingsView?.imagesRefFilePath?.text ?: PluginSettings.DEFAULT_IMAGES_REF_FILE_PATH
        )
        PluginSettings.setImagesRefFileName(
            getProjectWrapper(),
            mSettingsView?.imagesRefFileName?.text ?: PluginSettings.DEFAULT_IMAGES_REF_FILE_NAME
        )
        PluginSettings.setImagesRefClassName(
            getProjectWrapper(),
            mSettingsView?.imagesRefClassName?.text ?: PluginSettings.DEFAULT_IMAGES_REF_CLASS_NAME
        )

        if (pubRoot?.exampleDir == null) {
            return
        }

        PluginSettings.setExampleImagesFileDir(
            getProjectWrapper(),
            mSettingsView?.exampleImagesDir?.text ?: PluginSettings.DEFAULT_IMAGE_DIR
        )
        PluginSettings.setExampleImagesRefFilePath(
            getProjectWrapper(),
            mSettingsView?.exampleImagesRefFilePath?.text
                ?: PluginSettings.DEFAULT_IMAGES_REF_FILE_PATH
        )
        PluginSettings.setExampleImagesRefFileName(
            getProjectWrapper(),
            mSettingsView?.exampleImagesRefFileName?.text
                ?: PluginSettings.DEFAULT_IMAGES_REF_FILE_NAME
        )
        PluginSettings.setExampleImagesRefClassName(
            getProjectWrapper(),
            mSettingsView?.exampleImagesRefClassName?.text
                ?: PluginSettings.DEFAULT_IMAGES_REF_CLASS_NAME
        )
    }

    private fun applyExtractStr2L10n() {
        PluginSettings.setExtractStr2L10nShowRenameDialog(
            getProjectWrapper(),
            mSettingsView?.extractStr2L10nShowRenameDialog?.isSelected ?: true
        )
        PluginSettings.setExtractStr2L10nTranslateOther(
            getProjectWrapper(),
            mSettingsView?.extractStr2L10nTranslateOther?.isSelected ?: true
        )
    }

    override fun reset() {
        resetCreateResource()
        resetExtractStr2L10n()
        mSettingsView?.copyAndroidStrUseSimpleMode?.isSelected =
            PluginSettings.getCopyAndroidStrUseSimpleMode(getProjectWrapper())
    }

    private fun resetCreateResource() {
        mSettingsView?.imagesDir?.text = PluginSettings.getImagesFileDir(getProjectWrapper())
        mSettingsView?.imagesRefFilePath?.text = PluginSettings.getImagesRefFilePath(getProjectWrapper())
        mSettingsView?.imagesRefFileName?.text = PluginSettings.getImagesRefFileName(getProjectWrapper())
        mSettingsView?.imagesRefClassName?.text = PluginSettings.getImagesRefClassName(getProjectWrapper())

        if (pubRoot?.exampleDir == null) {
            return
        }

        mSettingsView?.exampleImagesDir?.text = PluginSettings.getExampleImagesFileDir(getProjectWrapper())
        mSettingsView?.exampleImagesRefFilePath?.text = PluginSettings.getExampleImagesRefFilePath(getProjectWrapper())
        mSettingsView?.exampleImagesRefFileName?.text = PluginSettings.getExampleImagesRefFileName(getProjectWrapper())
        mSettingsView?.exampleImagesRefClassName?.text =
            PluginSettings.getExampleImagesRefClassName(getProjectWrapper())
    }

    private fun resetExtractStr2L10n() {
        mSettingsView?.extractStr2L10nShowRenameDialog?.isSelected =
            PluginSettings.getExtractStr2L10nShowRenameDialog(getProjectWrapper())
        mSettingsView?.extractStr2L10nTranslateOther?.isSelected =
            PluginSettings.getExtractStr2L10nTranslateOther(getProjectWrapper())
    }

    override fun disposeUIResources() {
        mSettingsView = null
    }

    private fun getProjectWrapper(): Project? {
        if (project.isDefault || !project.isFlutterProject) {
            return null
        }
        return project
    }
}
