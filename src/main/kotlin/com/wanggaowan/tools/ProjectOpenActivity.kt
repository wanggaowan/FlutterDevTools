package com.wanggaowan.tools

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * 项目打开监听
 *
 * @author Created by wanggaowan on 2023/3/6 22:03
 */
class ProjectOpenActivity : StartupActivity, DumbAware {
    override fun runActivity(project: Project) {
        // 项目打开
    }
}
