package com.wanggaowan.tools.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.wanggaowan.tools.ui.PluginSettingsView
import com.wanggaowan.tools.utils.ex.isFlutterProject
import javax.swing.JComponent

/**
 * 项目插件设置界面配置
 *
 * @author Created by wanggaowan on 2023/3/5 16:17
 */
class ProjectPluginSettingsConfigurable(val project: Project) : Configurable {
    private var mSettingsView: PluginSettingsView? = null

    override fun getDisplayName(): String {
        return "FlutterDevTools"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return mSettingsView!!.preferredFocusedComponent
    }

    override fun createComponent(): JComponent {
        mSettingsView = PluginSettingsView()
        return mSettingsView!!.panel
    }

    override fun isModified(): Boolean {
        return PluginSettings.getImagesFileDir(getProjectWrapper()) != mSettingsView?.getImagesDir()
            || PluginSettings.getImagesDartFileGeneratePath(getProjectWrapper()) != mSettingsView?.getImagesDartFileGeneratePath()
    }

    override fun apply() {
        PluginSettings.setImagesFileDir(getProjectWrapper(), mSettingsView?.getImagesDir() ?: "")
        PluginSettings.setImagesDartFileGeneratePath(getProjectWrapper(), mSettingsView?.getImagesDartFileGeneratePath() ?: "")
    }

    override fun reset() {
        mSettingsView?.setImagesDir(PluginSettings.getImagesFileDir(getProjectWrapper()))
        mSettingsView?.setImagesDartFileGeneratePath(PluginSettings.getImagesDartFileGeneratePath(getProjectWrapper()))
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
