package com.wanggaowan.tools.entity

/**
 * 需要更新的属性内容
 *
 * @author Created by wanggaowan on 2022/5/4 15:45
 */
data class Property(val key: String, val value: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Property) return false

        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }
}
