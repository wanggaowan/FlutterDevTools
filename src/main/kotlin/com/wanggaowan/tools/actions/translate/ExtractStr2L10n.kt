package com.wanggaowan.tools.actions.translate

import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * 提取文本为多语言
 *
 * @author Created by wanggaowan on 2023/10/7 10:56
 */
class ExtractStr2L10n : ExtractStr2L10nAndTranslate() {
    override fun actionPerformed(event: AnActionEvent) {
        ExtractUtils.extract(event, selectedPsiFile, selectedPsiElement)
    }
}

