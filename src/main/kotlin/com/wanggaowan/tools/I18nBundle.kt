package com.wanggaowan.tools

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier


/**
 * 工具多语言
 *
 * @author Created by wanggaowan on 2024/9/5 下午1:38
 */
class I18nBundle : DynamicBundle(BUNDLE) {
    companion object {
        private const val BUNDLE = "messages.I18nBundle"
        private val INSTANCE: I18nBundle = I18nBundle()

        fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
            return INSTANCE.getMessage(key, *params)
        }

        fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<String> {
            return INSTANCE.getLazyMessage(key, *params)
        }
    }
}
