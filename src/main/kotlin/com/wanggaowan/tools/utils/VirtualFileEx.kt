/*
 * VirtualFile类扩展
 *
 * Created by wanggaowan on 2022/4/16 20:23
 */

package com.wanggaowan.tools.utils

import com.intellij.openapi.vfs.VirtualFile

/**
 * 从指定文件查找子类文件,[findDir]表示是否仅查询目录文件，如果[findDir]为null，则直接返回文件名称与
 * 指定[name]一致的文件
 */
fun VirtualFile?.findChild(name: String, findDir: Boolean?): VirtualFile? {
    if (this == null) {
        return null
    }

    val child = findChild(name) ?: return null
    if (findDir == null) {
        return child
    }

    if (findDir && child.isDirectory) {
        return child
    }

    if (!findDir && child.isDirectory) {
        return child
    }

    return null
}
