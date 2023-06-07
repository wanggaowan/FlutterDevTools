package com.wanggaowan.tools.extensions.gotohandler

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.wanggaowan.tools.utils.ex.findModule
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

        val module = sourceElement.findModule() ?: return null
        if (!module.isFlutterProject) {
            return null
        }

        var targets = I18nGoToDeclarationHandler.getGotoDeclarationTargets(module, sourceElement)
        if (targets != null) {
            return targets
        }

        targets = ImagesGoToDeclarationHandler.getGotoDeclarationTargets(module, sourceElement)
        if (targets != null) {
            return targets
        }

        targets = RouterGoToDeclarationHandler.getGotoDeclarationTargets(module, sourceElement)
        if (targets != null) {
            return targets
        }

        return null
    }
}
