package com.wanggaowan.tools.actions.filetemplate

/**
 * 模版实体类
 *
 * @author Created by wanggaowan on 2023/9/7 11:30
 */
class TemplateEntity {
    var name: String? = null
    var children: MutableList<TemplateChildEntity>? = null
}

class TemplateChildEntity {
    var name: String? = null
    var content: String? = null


    @Transient
    var tempContent: String? = null
}
