package com.leslie.tvhome

/** OpenCC s2twp dictionaries with longest phrase matching and Taiwan vocabulary. */
object TaiwanText {
    private class Dictionary(files: List<String>) {
        private val replacements = HashMap<String, String>()
        private var longest = 1

        init {
            for (file in files) {
                val stream = checkNotNull(TaiwanText::class.java.getResourceAsStream("/opencc/$file")) {
                    "Missing OpenCC dictionary $file"
                }
                stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        val separator = line.indexOf('\t')
                        val source = line.substring(0, separator)
                        val target = line.substring(separator + 1).substringBefore(' ')
                        replacements.putIfAbsent(source, target)
                        longest = maxOf(longest, source.length)
                    }
                }
            }
        }

        fun convert(text: String): String = buildString(text.length) {
            var offset = 0
            while (offset < text.length) {
                var length = minOf(longest, text.length - offset)
                var replacement: String? = null
                while (length > 0) {
                    replacement = replacements[text.substring(offset, offset + length)]
                    if (replacement != null) break
                    length--
                }
                if (replacement == null) {
                    append(text[offset])
                    offset++
                } else {
                    append(replacement)
                    offset += length
                }
            }
        }
    }

    private val dictionaries by lazy {
        listOf(
            Dictionary(listOf("STPhrases.txt", "STCharacters.txt")),
            Dictionary(listOf("TWPhrases.txt")),
            Dictionary(listOf("TWVariants.txt")),
        )
    }

    fun convert(text: String): String {
        if (text.codePoints().noneMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }) return text
        return dictionaries.fold(text) { result, dictionary -> dictionary.convert(result) }
    }
}
