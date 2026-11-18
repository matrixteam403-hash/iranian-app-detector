package ir.irandetector.app.core

/**
 * A very small, dependency-free JSON reader/writer.
 *
 * Why hand-rolled instead of kotlinx.serialization / Gson / Moshi?
 *  - The detection database format is tiny and fully under our control.
 *  - It removes ~300 KB of runtime libraries from the APK and one annotation
 *    processor from the build (APK size is a hard requirement for this project).
 *  - Being pure Kotlin/JVM it is directly unit-testable without Android.
 *
 * Supported: objects, arrays, strings, numbers, booleans, null. Comments are not
 * supported (strict JSON), which keeps parsing predictable for remote payloads.
 */
object Json {

    sealed interface Value {
        data class Obj(val fields: Map<String, Value>) : Value
        data class Arr(val items: List<Value>) : Value
        data class Str(val value: String) : Value
        data class Num(val value: Double) : Value
        data class Bool(val value: Boolean) : Value
        data object Null : Value
    }

    class JsonException(message: String) : Exception(message)

    fun parse(text: String): Value {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonException("Trailing characters at ${parser.position}")
        return value
    }

    /** Convenience accessors used across the data layer. */
    fun Value.stringOrNull(key: String): String? =
        (this as? Value.Obj)?.fields?.get(key).let { (it as? Value.Str)?.value }

    fun Value.string(key: String, default: String = ""): String = stringOrNull(key) ?: default

    fun Value.longOrNull(key: String): Long? = (this as? Value.Obj)?.fields?.get(key).let {
        when (it) {
            is Value.Num -> it.value.toLong()
            is Value.Str -> it.value.toLongOrNull()
            else -> null
        }
    }

    fun Value.intOrNull(key: String): Int? = longOrNull(key)?.toInt()

    fun Value.doubleOrNull(key: String): Double? = (this as? Value.Obj)?.fields?.get(key).let {
        when (it) {
            is Value.Num -> it.value
            is Value.Str -> it.value.toDoubleOrNull()
            else -> null
        }
    }

    fun Value.boolOrNull(key: String): Boolean? = when (
        val v = (this as? Value.Obj)?.fields?.get(key)
    ) {
        is Value.Bool -> v.value
        is Value.Str -> when (v.value.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
        else -> null
    }

    fun Value.arrayOrEmpty(key: String): List<Value> =
        (this as? Value.Obj)?.fields?.get(key)?.let { (it as? Value.Arr)?.items } ?: emptyList()

    fun Value.stringList(key: String): List<String> =
        arrayOrEmpty(key).mapNotNull { (it as? Value.Str)?.value?.takeIf(String::isNotBlank) }

    private class Parser(private val src: String) {
        var position = 0
            private set

        fun atEnd(): Boolean = position >= src.length

        fun skipWhitespace() {
            while (position < src.length && src[position].isWhitespace()) position++
        }

        fun parseValue(): Value {
            skipWhitespace()
            if (atEnd()) throw JsonException("Unexpected end of input")
            return when (val c = src[position]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> Value.Str(parseString())
                't' -> literal("true", Value.Bool(true))
                'f' -> literal("false", Value.Bool(false))
                'n' -> literal("null", Value.Null)
                else -> if (c == '-' || c.isDigit()) parseNumber()
                else throw JsonException("Unexpected character '$c' at $position")
            }
        }

        private fun literal(text: String, value: Value): Value {
            if (src.startsWith(text, position)) {
                position += text.length
                return value
            }
            throw JsonException("Expected '$text' at $position")
        }

        private fun parseObject(): Value {
            position++ // '{'
            val fields = LinkedHashMap<String, Value>()
            skipWhitespace()
            if (!atEnd() && src[position] == '}') { position++; return Value.Obj(fields) }
            while (true) {
                skipWhitespace()
                if (atEnd() || src[position] != '"') throw JsonException("Expected key at $position")
                val key = parseString()
                skipWhitespace()
                if (atEnd() || src[position] != ':') throw JsonException("Expected ':' at $position")
                position++
                fields[key] = parseValue()
                skipWhitespace()
                if (atEnd()) throw JsonException("Unterminated object")
                when (src[position]) {
                    ',' -> position++
                    '}' -> { position++; return Value.Obj(fields) }
                    else -> throw JsonException("Expected ',' or '}' at $position")
                }
            }
        }

        private fun parseArray(): Value {
            position++ // '['
            val items = ArrayList<Value>()
            skipWhitespace()
            if (!atEnd() && src[position] == ']') { position++; return Value.Arr(items) }
            while (true) {
                items.add(parseValue())
                skipWhitespace()
                if (atEnd()) throw JsonException("Unterminated array")
                when (src[position]) {
                    ',' -> position++
                    ']' -> { position++; return Value.Arr(items) }
                    else -> throw JsonException("Expected ',' or ']' at $position")
                }
            }
        }

        private fun parseString(): String {
            position++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("Unterminated string")
                when (val c = src[position]) {
                    '"' -> { position++; return sb.toString() }
                    '\\' -> {
                        position++
                        if (atEnd()) throw JsonException("Unterminated escape")
                        when (val e = src[position]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (position + 4 >= src.length) throw JsonException("Bad unicode escape")
                                val hex = src.substring(position + 1, position + 5)
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: throw JsonException("Bad unicode escape"))
                                position += 4
                            }
                            else -> throw JsonException("Bad escape '\\$e'")
                        }
                        position++
                    }
                    else -> { sb.append(c); position++ }
                }
            }
        }

        private fun parseNumber(): Value {
            val start = position
            if (src[position] == '-') position++
            while (!atEnd() && (src[position].isDigit() || src[position] in ".eE+-")) position++
            val raw = src.substring(start, position)
            val value = raw.toDoubleOrNull() ?: throw JsonException("Bad number '$raw'")
            return Value.Num(value)
        }
    }

    // ---------------------------------------------------------------- writing

    fun escape(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    fun stringArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { "\"${escape(it)}\"" }
}
