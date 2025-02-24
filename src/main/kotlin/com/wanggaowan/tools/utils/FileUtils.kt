package com.wanggaowan.tools.utils

import java.io.*

/**
 * 文件处理工具类
 *
 * @author Created by wanggaowan on 2024/6/26 上午11:28
 */
object FileUtils {

    fun write(path: String, content: String): Boolean {
        val file = File(path)
        return write(file, content)
    }

    fun write(file: File, content: String): Boolean {
        var writer: BufferedWriter? = null
        if (file.exists()) {
            try {
                writer = BufferedWriter(FileWriter(file))
                writer.write(content)
                return true
            } catch (_: IOException) {
                return false
            } finally {
                safeClose(writer)
            }
        }
        return false
    }

    fun read(path: String): String {
        return read(File(path))
    }

    fun read(file: File): String {
        val builder = StringBuilder()
        var reader: BufferedReader? = null
        if (file.exists()) {
            try {
                reader = BufferedReader(FileReader(file))
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    builder.append(line)
                }
            } catch (_: IOException) {
                //
            } finally {
                safeClose(reader)
            }
        }
        return builder.toString()
    }

    private fun safeClose(obj: Closeable?) {
        try {
            obj?.close()
        } catch (_: Exception) {
            //
        }
    }
}
