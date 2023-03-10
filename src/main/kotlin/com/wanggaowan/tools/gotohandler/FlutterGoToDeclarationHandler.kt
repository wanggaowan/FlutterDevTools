package com.wanggaowan.tools.gotohandler

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.wanggaowan.tools.utils.ex.isFlutterProject

/**
 * 资源定位
 *
 * @author Created by wanggaowan on 2023/3/4 22:53
 */
class FlutterGoToDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?, offset: Int, editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) {
            return null
        }

        val project = sourceElement.project
        if (!project.isFlutterProject) {
            return null
        }

        var targets = I18nGoToDeclarationHandler.getGotoDeclarationTargets(project, sourceElement, offset, editor)
        if (targets != null) {
            return targets
        }

        targets = ImagesGoToDeclarationHandler.getGotoDeclarationTargets(project, sourceElement, offset, editor)
        if (targets != null) {
            return targets
        }

        return null
    }
}
