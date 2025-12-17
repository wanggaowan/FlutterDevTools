package com.wanggaowan.tools.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val LOG = logger<TranslateUtils>()

/**
 * 语言翻译工具
 *
 * @author Created by wanggaowan on 2024/1/5 08:44
 */
object TranslateUtils {

    private var httpClient: HttpClient? = null
    private var ofString: HttpResponse.BodyHandler<String?>? = null

    fun createHttpClient(): HttpClient {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(10000))
                .build()
            ofString = HttpResponse.BodyHandlers.ofString()
        }
        return httpClient!!
    }

    fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String? {
        val uuid = UUID.randomUUID().toString()
        val dateformat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        dateformat.timeZone = TimeZone.getTimeZone("UTC")
        val time = dateformat.format(Date())

        var accessKeyId = "TFRBSTV0UnFrbzY3QThVeFZDOGt4dHNu"
        accessKeyId = String(mapValue(accessKeyId))
        val queryMap = mutableMapOf<String, String>()
        queryMap["AccessKeyId"] = accessKeyId
        queryMap["Action"] = "TranslateGeneral"
        queryMap["Format"] = "JSON"
        queryMap["FormatType"] = "text"
        queryMap["RegionId"] = "cn-hangzhou"
        queryMap["Scene"] = "general"
        queryMap["SignatureVersion"] = "1.0"
        queryMap["SignatureMethod"] = "HMAC-SHA1"
        queryMap["Status"] = "Available"
        queryMap["SignatureNonce"] = uuid
        queryMap["SourceLanguage"] = sourceLanguage
        queryMap["SourceText"] = text
        queryMap["TargetLanguage"] = targetLanguage
        queryMap["Timestamp"] = time
        queryMap["Version"] = "2018-10-12"
        var queryString = getCanonicalizedQueryString(queryMap, queryMap.keys.toTypedArray())

        val stringToSign = "GET" + "&" + encodeURI("/") + "&" + encodeURI(queryString)
        val signature = encodeURI(Base64.getEncoder().encodeToString(signatureMethod(stringToSign)))
        queryString += "&Signature=$signature"
        try {
            val request: HttpRequest? = HttpRequest.newBuilder()
                .uri(URI.create("https://mt.cn-hangzhou.aliyuncs.com/?$queryString"))
                .GET()
                .build()

            val response: HttpResponse<String?> = createHttpClient().send(request, ofString)
            val body = response.body() ?: ""
            if (body.isEmpty()) {
                return null
            }

            // {"RequestId":"A721413A-7DCD-51B0-8AEE-FCE433CEACA2","Data":{"WordCount":"4","Translated":"Test Translation"},"Code":"200"}
            val jsonObject = Gson().fromJson(body, JsonObject::class.java)
            val code = jsonObject.getAsJsonPrimitive("Code").asString
            if (code != "200") {
                LOG.error("阿里翻译失败,响应结果：$body，模版语言：${sourceLanguage},目标语言：${targetLanguage}, 翻译失败文本：$text")
                return null
            }

            val data = jsonObject.getAsJsonObject("Data") ?: return null
            return data.getAsJsonPrimitive("Translated").asString
        } catch (e: Exception) {
            LOG.error("阿里翻译失败,异常内容：${e.message}，模版语言：${sourceLanguage},目标语言：${targetLanguage}, 翻译失败文本：$text")
            return null
        }
    }

    @Throws(java.lang.Exception::class)
    private fun signatureMethod(stringToSign: String?): ByteArray? {
        val secret = "V3FWRGI3c210UW9rOGJUOXF2VHhENnYzbmF1bjU1Jg=="
        if (stringToSign == null) {
            return null
        }
        val sha256Hmac = Mac.getInstance("HmacSHA1")
        val secretKey = SecretKeySpec(mapValue(secret), "HmacSHA1")
        sha256Hmac.init(secretKey)
        return sha256Hmac.doFinal(stringToSign.toByteArray())
    }

    @Throws(java.lang.Exception::class)
    private fun getCanonicalizedQueryString(
        query: Map<String, String?>,
        keys: Array<String>
    ): String {
        if (query.isEmpty()) {
            return ""
        }
        if (keys.isEmpty()) {
            return ""
        }

        Arrays.sort(keys)

        var key: String?
        var value: String?
        val sb = StringBuilder()
        for (i in keys.indices) {
            key = keys[i]
            sb.append(encodeURI(key))
            value = query[key]
            sb.append("=")
            if (!value.isNullOrEmpty()) {
                sb.append(encodeURI(value))
            }
            sb.append("&")
        }
        return sb.deleteCharAt(sb.length - 1).toString()
    }

    private fun mapValue(value: String): ByteArray {
        return Base64.getDecoder().decode(value)
    }

    private fun encodeURI(content: String): String {
        // 字符 A~Z、a~z、0~9 以及字符-、_、.、~不编码
        // 空格编码成%20，而不是加号（+）
        return try {
            URLEncoder.encode(content, StandardCharsets.UTF_8.name()).replace("+", "%20")
                .replace("%7E", "~")
                .replace("*", "%2A")
        } catch (_: UnsupportedEncodingException) {
            content
        }
    }

    fun mapStrToKey(str: String?, isFormat: Boolean): String? {
        var value = fixNewLineFormatError(str)?.replace("\\n", "_")
        if (value.isNullOrEmpty()) {
            return null
        }

        // \pP：中的小写p是property的意思，表示Unicode属性，用于Unicode正则表达式的前缀。
        //
        // P：标点字符
        //
        // L：字母；
        //
        // M：标记符号（一般不会单独出现）；
        //
        // Z：分隔符（比如空格、换行等）；
        //
        // S：符号（比如数学符号、货币符号等）；
        //
        // N：数字（比如阿拉伯数字、罗马数字等）；
        //
        // C：其他字符
        value = value.lowercase().replace(Regex("[\\pP\\pS]"), "_")
            .replace(" ", "_")
        if (isFormat) {
            value += "_format"
        }

        value = value.replace("_____", "_")
            .replace("____", "_")
            .replace("___", "_")
            .replace("__", "_")

        if (value.startsWith("_")) {
            value = value.substring(1, value.length)
        }

        if (value.endsWith("_")) {
            value = value.dropLast(1)
        }

        return value
    }

    /**
     * 修复翻译错误，如占位符为大写，\n，%s翻译后被分开成 \ n,% s等错误
     *
     * [useEscaping] arb文件是否启用转义字符
     * [isByTemplate] 是否根据模板翻译
     */
    fun fixTranslateError(
        translate: String?,
        useEscaping: Boolean = false,
        isByTemplate: Boolean = false,
    ): String? {
        var translateStr = fixTranslatePlaceHolderStr(translate, useEscaping, isByTemplate)
        translateStr = fixNewLineFormatError(translateStr)
        if (translateStr != null) {
            // 处理单引号多余的转义斜杠。以下正则匹配单引号前面有奇数个反斜杠
            // (?<!\\\\) - 负向后瞻，确保当前位置前面不是单个反斜杠
            // (?:\\\\\\\\)* - 非捕获组，匹配零个或多个连续的两个反斜杠（即偶数个反斜杠）
            // \\\\' - 匹配\'
            // var regex = Regex("(?<!\\\\)(?:\\\\\\\\)*\\\\'")

            // 处理单引号多余的转义斜杠。以下正则匹配单引号前面的反斜杠
            var regex = Regex("[\\\\\\s]*'")
            translateStr = fixEscapeFormatError(regex, translateStr,false)
            // 处理双引号缺失的转义斜杠。以下正则匹配双引号前面的反斜杠
            regex = Regex("[\\\\\\s]*\"")
            translateStr = fixEscapeFormatError(regex, translateStr)
        }
        return translateStr
    }

    /**
     * 修复因翻译，导致占位符被翻译为大写的问题
     *
     * [useEscaping] 是否启用转义字符
     * [isByTemplate] 是否根据模板翻译
     */
    private fun fixTranslatePlaceHolderStr(
        translate: String?,
        useEscaping: Boolean = false,
        isByTemplate: Boolean = false,
    ): String? {
        if (translate.isNullOrEmpty()) {
            return null
        }

        val regex = if (isByTemplate) {
            if (useEscaping) {
                Regex("(('+\\s*\\{\\s*[Pp]aram[0-9]*\\s*\\}\\s*'+)|(\\{\\s*[Pp]aram[0-9]*\\s*\\}))")
            } else {
                Regex("\\{\\s*[Pp]aram[0-9]*\\s*\\}")
            }
        } else if (useEscaping) {
            Regex("<\\s*[Pp]aram[0-9]*\\s*>")
        } else {
            Regex("\\{\\s*[Pp]aram[0-9]*\\s*\\}")
        }

        var text = fixWhiteFormatError(regex, translate, useEscaping)
        if (useEscaping) {
            if (isByTemplate) {
                var offset = 0
                // 查找单引号，但前后不能是{或}
                val regex = Regex("(?<![{}]\\s*)('+[\\s']*)(?!\\s*[{}])")
                do {
                    val matchResult = regex.find(text, offset)
                    if (matchResult != null) {
                        var placeHolder = text.substring(matchResult.range)
                        val placeHolder2 = placeHolder.replace("'", "")
                        if (placeHolder.length - placeHolder2.length == 1) {
                            // 仅处理单引号只有一个的数据，存在连续多个单引号不处理
                            placeHolder = placeHolder.replace("'", "''")
                            text = text.replaceRange(matchResult.range, placeHolder)
                            // matchResult.range.last为正则匹配最后一个字符下标，如果像上面仅查找一个字符，
                            // 那 matchResult.range.first和matchResult.range.last相等，下次需要跳过
                            // 本次查找的字符，需要matchResult.range.last + 1，再+1是上面一个单引号改为两个单引号
                            // 追加的字符长度， 否则下次匹配刚好就是上面追加的单引号，会陷入死循环
                            offset = matchResult.range.last + 1 + 1
                        } else {
                            offset = matchResult.range.last + 1
                        }
                    }
                } while (matchResult != null)
            } else {
                // 不是根据模板翻译且启用转义的情况下，如果文本中出现单引号，需要再追加一个单引号进行转义
                text = text.replace("'", "''")
                // 需要对出现的{进行转义
                text = text.replace("{", "'{'")
                // 需要对出现的}进行转义
                text = text.replace("}", "'}'")
                text = replacePlaceHolder(Regex("<param[0-9]*>"), text)
            }

            // 如果{前面存在单引号且不止一个，则需要在离{最近的单引号前加空格，否则此转义单引号会被当做普通单引号字符处理
            var regex = Regex("\\s*'\\s*\\{")
            var offset = 0
            do {
                val matchResult = regex.find(text, offset)
                if (matchResult != null) {
                    var placeHolder = text.substring(matchResult.range)
                    var addCount = 0
                    if (!placeHolder.startsWith(" ")) {
                        val start = matchResult.range.first
                        if (start > 0) {
                            val str = text.substring(start - 1, start)
                            if (str == "'") {
                                placeHolder = " $placeHolder"
                                text = text.replaceRange(matchResult.range, placeHolder)
                                addCount = 1
                            }
                        }
                    }
                    offset = matchResult.range.last + 1 + addCount
                }
            } while (matchResult != null)

            // 如果}后面存在单引号且不止一个，则需要在离}最近的单引号后加空格，否则此转义单引号会被当做普通单引号字符处理
            regex = Regex("}\\s*'\\s*")
            offset = 0
            do {
                val matchResult = regex.find(text, offset)
                if (matchResult != null) {
                    var placeHolder = text.substring(matchResult.range)
                    var addCount = 0
                    if (!placeHolder.endsWith(" ")) {
                        val end = matchResult.range.last
                        if (end < text.length) {
                            val str = text.substring(end + 1, end + 2)
                            if (str == "'") {
                                placeHolder = "$placeHolder "
                                text = text.replaceRange(matchResult.range, placeHolder)
                                addCount = 1
                            }
                        }
                    }
                    offset = matchResult.range.last + 1 + addCount
                }
            } while (matchResult != null)
        }
        return text
    }

    /**
     * 修复多了空格、大小写错误，比如%s翻译后是%S，\n翻译后是\N，或者中间有空格如% s，\ n等
     *
     * [text]为需要修复的文本
     * [regex]为查找错误格式文本的正则表达式
     */
    private tailrec fun fixWhiteFormatError(
        regex: Regex,
        text: String,
        useEscaping: Boolean = false,
        isByTemplate: Boolean = false,
        offset: Int? = null,
    ): String {
        if (text.isEmpty()) {
            return text
        }

        val matchResult = regex.find(text, offset ?: 0) ?: return text
        var placeHolder = text.substring(matchResult.range)
        var needReplace = true
        if (isByTemplate && useEscaping) {
            val start = placeHolder.indexOf("{")
            val end = placeHolder.indexOf("}")
            // 起始单引号数量
            val startSymbolCount = placeHolder.take(start).replace(" ", "").length
            // 尾部单引号数量
            val endSymbolCount = placeHolder.substring(end + 1).replace(" ", "").length
            if (startSymbolCount != endSymbolCount || startSymbolCount % 2 != 0) {
                // 使用了转义字符包裹占位符，保持原样
                // startSymbolCount != endSymbolCount,此时dart执行gen-l10n命令时会报错，启用转义时，单引号要成对出现，因此不处理
                // startSymbolCount % 2 != 0,单引号为奇数个，存在一对单引号用来转义{}占位符
                needReplace = false
            }
        }

        var end = matchResult.range.last + 1
        val mapText = if (needReplace) {
            val oldLength = placeHolder.length
            placeHolder = placeHolder.replace(" ", "").lowercase()
            end -= oldLength - placeHolder.length
            text.replaceRange(matchResult.range, placeHolder)
        } else {
            text
        }

        return fixWhiteFormatError(
            regex,
            mapText,
            useEscaping,
            isByTemplate,
            end
        )
    }

    /**
     * 修复转义错误，比如'，"未加反斜杠或反斜杠数量多了
     *
     * [text] 为需要修复的文本
     * [regex] 为查找错误格式文本的正则表达式
     * [isAdd] 表示是添加还是去除反斜杠
     */
    tailrec fun fixEscapeFormatError(
        regex: Regex,
        text: String,
        isAdd: Boolean = true,
        offset: Int? = null,
    ): String {
        if (text.isEmpty()) {
            return text
        }

        val matchResult = regex.find(text, offset ?: 0) ?: return text
        var placeHolder = text.substring(matchResult.range)
        val oldLength = placeHolder.length
        placeHolder = placeHolder.replace(" ", "")

        val count = placeHolder.count { it.toString() == "\\" }
        val end = if (isAdd) {
            if (count % 2 == 0) {
                // 新增转义字符时，只有之前存在偶数个时才处理
                placeHolder = "\\$placeHolder"
                matchResult.range.last + 1 + (placeHolder.length - oldLength)
            } else {
                matchResult.range.last + 1 + (placeHolder.length - oldLength)
            }
        } else if (count % 2 != 0) {
            // 移除转义字符时，只有之前存在奇数个时才处理
            placeHolder = placeHolder.substring(1, placeHolder.length)
            matchResult.range.last + (placeHolder.length - oldLength)
        } else {
            matchResult.range.last + 1 + (placeHolder.length - oldLength)
        }

        return fixEscapeFormatError(
            regex,
            text.replaceRange(matchResult.range, placeHolder),
            isAdd,
            end
        )
    }

    /**
     * 替换占位符，当不是根据模版翻译时，需要将<param0>替换为{param0}
     */
    private tailrec fun replacePlaceHolder(
        regex: Regex,
        text: String,
        offset: Int? = null,
    ): String {
        if (text.isEmpty()) {
            return text
        }

        val matchResult = regex.find(text, offset ?: 0) ?: return text
        val placeHolder = text.substring(matchResult.range).replace("<", "{").replace(">", "}")
        return replacePlaceHolder(
            regex,
            text.replaceRange(matchResult.range, placeHolder),
            matchResult.range.last + 1
        )
    }

    // 修复格式错误，如\n,翻译成 \ n
    private fun fixNewLineFormatError(text: String?): String? {
        if (text.isNullOrEmpty()) {
            return text
        }

        val regex = Regex("\\s*\\\\\\s*[nN]\\s*")
        return text.replace(regex, "\\\\n")
    }
}
