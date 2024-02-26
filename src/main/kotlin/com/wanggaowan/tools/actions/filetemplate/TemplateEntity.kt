package com.wanggaowan.tools.actions.filetemplate


abstract class BaseTemplate {
    var name: String? = null
    var children: MutableList<TemplateChildEntity>? = null
}

/**
 * 模版实体类
 *
 * @author Created by wanggaowan on 2023/9/7 11:30
 */
class TemplateEntity : BaseTemplate() {
    var createFolder: Boolean = false
}

class TemplateChildEntity : BaseTemplate() {
    var content: String? = null
    var isFolder: Boolean = false

    @Transient
    var tempContent: String? = null
}
