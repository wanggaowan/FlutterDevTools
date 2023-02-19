package com.wanggaowan.tools.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.wanggaowan.tools.ui.PluginSettingsView
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.PluginSetting
import javax.swing.JComponent

/**
 * 插件设置界面配置
 */
class PluginSettingsConfigurable : Configurable {
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
        return PluginSettings.imagesFileDir != mSettingsView?.getImagesDir()
            || PluginSettings.imagesDartFileGeneratePath != mSettingsView?.getImagesDartFileGeneratePath()
    }

    override fun apply() {
        PluginSettings.imagesFileDir = mSettingsView?.getImagesDir()?:""
        PluginSettings.imagesDartFileGeneratePath = mSettingsView?.getImagesDartFileGeneratePath()?:""
    }

    override fun reset() {
        mSettingsView?.setImagesDir(PluginSettings.imagesFileDir)
        mSettingsView?.setImagesDartFileGeneratePath(PluginSettings.imagesDartFileGeneratePath)
    }

    override fun disposeUIResources() {
        mSettingsView = null
    }
}
