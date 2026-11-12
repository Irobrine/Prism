package com.prism.music.data.lyrics.roman

import com.prism.music.data.lyrics.isKanji

/**
 * How Japanese says a number with the counter after it: 20分 is にじゅっぷん, 3本 is さんぼん, 8歳 is はっさい.
 *
 * A reader tokenizes kanji numerals one at a time (二, 十, 分) and gives each its dictionary reading, so a counter never
 * sees the number before it: 20分 comes out にふん and 1本 as いちほん. This object reads the number itself and puts the
 * counter through the sound changes Japanese gives it. It is independent of the reader: Kuromoji's reader and Sudachi's
 * both ask it, and it only deals in strings.
 */
internal object JapaneseCounters {
    /**
     * A number and its counter, split so a reading can sit above each part: for 20分, ニジュッ above 20 and プン above
     * 分. [number] is empty when the counter's reading already contains the number (2人 is フタリ, 20日 is ハツカ).
     */
    data class Reading(val number: String, val counter: String) {
        val all: String get() = number + counter
    }

    /** A token of a line: where its surface starts, and the surface itself (the reader's own token, in its own words). */
    data class Token(val start: Int, val surface: String)

    /** A number and its counter as they sit in a line, and the reading they share. */
    data class Run(val start: Int, val end: Int, val reading: Reading)

    /**
     * Every number followed by a counter in [text], as [tokens] put them: 20分, 一ヶ月, 二十歳. A reader may keep the
     * number and counter as one word (四月, 二分) or split them, and a dictionary word may start inside a longer number
     * (十二人 was 十 + 二人), so both spellings are followed.
     */
    fun runs(text: String, tokens: List<Token>): List<Run> {
        val runs = ArrayList<Run>()
        var i = 0
        while (i < tokens.size) {
            val first = tokens[i]
            val at = first.start
            if (at < 0 || at + first.surface.length > text.length) { i++; continue }
            // A numeral that follows another numeral (1,000, 3.5) is the middle of a number, not one of its own.
            if (at > 0) {
                val before = text[at - 1]
                if (isNumber(before) || isNumeral(before) || before in ",，.．") { i++; continue }
            }
            val prefix = numeralPrefix(first.surface)
            if (prefix == 0) { i++; continue }
            var number = first.surface.substring(0, prefix)
            var counter: String? = null
            var end = at + prefix
            var next = i + 1
            if (prefix == first.surface.length) {
                // A numeral of its own: take the numerals after it, and the counter they end with.
                while (next < tokens.size) {
                    val t = tokens[next]
                    if (t.start != end) break
                    val tPrefix = numeralPrefix(t.surface)
                    if (tPrefix == 0) break
                    if (tPrefix == t.surface.length) {
                        number += t.surface
                        end = t.start + t.surface.length
                        next++
                        continue
                    }
                    // A dictionary word that starts with a numeral and ends with the counter (二人, 四月).
                    val matched = exact(t.surface.substring(tPrefix))
                    if (matched != null) {
                        number += t.surface.substring(0, tPrefix)
                        counter = matched
                        end = t.start + t.surface.length
                        next++
                    }
                    break
                }
                if (counter == null) {
                    val t = tokens.getOrNull(next)
                    if (t != null && t.start == end) {
                        val matched = exact(t.surface)
                        if (matched != null) {
                            counter = matched
                            end = t.start + t.surface.length
                            next++
                        }
                    }
                }
            } else {
                // One word holds the number and its counter: 四分 is ヨン + フン, 四月 is シ + ガツ.
                val matched = exact(first.surface.substring(prefix))
                if (matched != null) {
                    counter = matched
                    end = at + first.surface.length
                }
            }
            // 間 may be a word of its own (一ヶ月 + 間), so the counter's longer form is looked for too.
            if (counter != null) {
                val t = tokens.getOrNull(next)
                if (t != null && t.start == end && t.surface in listOf("間", "の")) {
                    exact(counter + t.surface)?.let {
                        counter = it
                        end = t.start + t.surface.length
                        next++
                    }
                }
            }
            // 一人称 and 一人前 read 人 as にん, not ひとり.
            val after = text.getOrNull(end)
            val compoundPerson = counter == "人" && (after == '称' || after == '前')
            val reading = counter?.let { c -> number(number)?.let { n -> reading(c, n, compoundPerson) } }
            if (reading != null) {
                runs += Run(at, end, reading)
                i = next
            } else {
                i++
            }
        }
        return runs
    }

    /**
     * Puts [runs]' readings back into [out], replacing whatever the reader's word-by-word reading put there. The whole
     * number and counter are spelled out, as the furigana above them is: 20分 is nijuppun, not "20pun", and 二人 is
     * futari, not "2futari".
     */
    fun apply(out: MutableList<String>, text: String, runs: List<Run>) {
        for (run in runs) {
            // What the run's first word had in front of it: the space between it and the word before. A number a reader
            // saw as digits never got one, so it's added when a word comes right before it.
            var leading = out[run.start].takeWhile { it.isWhitespace() }
            if (leading.isEmpty() && run.start > 0 && out[run.start - 1].lastOrNull()?.isWhitespace() != true) {
                val before = text[run.start - 1]
                if (isJapanese(before) && !isNumeral(before)) leading = " "
            }
            for (i in run.start until run.end) out[i] = ""
            out[run.start] = leading + Kana.romanize(run.reading.all).lowercase()
        }
    }

    /** How many characters at the start of [surface] are Japanese numerals. */
    private fun numeralPrefix(surface: String): Int {
        var n = 0
        while (n < surface.length && isNumeral(surface[n])) n++
        return n
    }

    private fun isJapanese(c: Char) = Kana.isKana(c) || isKanji(c) || c == 'ー' || c == '々'

    private fun isNumber(c: Char) = c in '0'..'9' || c in '０'..'９'

    private val COUNTERS: Map<String, (Long) -> Reading?> = buildMap {
        fun putCounter(surface: String, rule: (Long) -> Reading?) {
            put(surface, rule)
        }

        /** A counter whose reading is just the number plus [base]. */
        fun plain(surface: String, base: String) {
            putCounter(surface) { n -> Reading(numberReading(n), base) }
        }

        // Time, dates and the counters songs use most.
        putCounter("分") { n -> funReading(n) }
        putCounter("日") { n -> dayReading(n) }
        putCounter("月") { n -> monthReading(n) }
        putCounter("時") { n -> Reading(timeNumber(n), "ジ") }
        putCounter("人") { n -> personReading(n) }
        putCounter("個") { n -> sokuonReading("コ", n) }
        putCounter("歳") { n -> if (n == 20L) Reading("", "ハタチ") else sokuonReading("サイ", n, six = false) }
        putCounter("年") { n -> Reading(yearNumber(n), "ネン") }
        putCounter("円") { n -> Reading(replaceTail(numberReading(n), "ヨン", "ヨ"), "エン") }
        putCounter("秒") { n -> Reading(numberReading(n), "ビョウ") }
        putCounter("本") { n -> sokuonReading("ホン", n, p = true, voice3 = true) }
        putCounter("杯") { n -> sokuonReading("ハイ", n, p = true, voice3 = true) }
        putCounter("匹") { n -> sokuonReading("ヒキ", n, p = true, voice3 = true) }
        putCounter("回") { n -> sokuonReading("カイ", n) }
        putCounter("階") { n -> sokuonReading("カイ", n, voice3 = true) }
        putCounter("軒") { n -> sokuonReading("ケン", n, voice3 = true) }
        putCounter("件") { n -> sokuonReading("ケン", n) }
        putCounter("曲") { n -> sokuonReading("キョク", n) }
        putCounter("冊") { n -> sokuonReading("サツ", n, six = false) }
        putCounter("足") { n -> sokuonReading("ソク", n, voice3 = true, six = false) }
        putCounter("頭") { n -> sokuonReading("トウ", n) }
        putCounter("点") { n -> sokuonReading("テン", n, six = false) }
        putCounter("着") { n -> sokuonReading("チャク", n, six = false) }
        putCounter("通") { n -> sokuonReading("ツウ", n, six = false) }
        putCounter("発") { n -> sokuonReading("ハツ", n, p = true, p3 = true) }
        putCounter("泊") { n -> sokuonReading("ハク", n, p = true, p3 = true) }
        putCounter("週") { n -> sokuonReading("シュウ", n, six = false) }
        putCounter("社") { n -> sokuonReading("シャ", n, six = false) }
        putCounter("国") { n -> sokuonReading("コク", n) }
        putCounter("校") { n -> sokuonReading("コウ", n) }
        putCounter("店") { n -> sokuonReading("テン", n, six = false) }
        putCounter("票") { n -> sokuonReading("ヒョウ", n, p = true, p3 = true) }
        putCounter("つ") { n -> tsuReading(n) }
        putCounter("ヶ月") { n -> sokuonReading("カゲツ", n) }

        // A counter that is a plain reading, with no sound change from the number.
        plain("枚", "マイ")
        plain("台", "ダイ")
        plain("度", "ド")
        plain("倍", "バイ")
        plain("番", "バン")
        plain("割", "ワリ")
        plain("名", "メイ")

        // The same counters with 間 after them (二十分間, 二日間, 一週間, 一時間).
        for (surface in listOf("分", "日", "時", "年", "週", "ヶ月")) {
            val rule = get(surface) ?: continue
            put("${surface}間") { n -> rule(n)?.let { Reading(it.number, it.counter + "カン") } }
        }
        // 20分の1: the counter is followed by の.
        get("分")?.let { rule ->
            put("分の") { n -> rule(n)?.let { Reading(it.number, it.counter + "ノ") } }
        }

        // ヶ月 is also written with other small kana, or as か月 / カ月.
        val months = get("ヶ月")
        if (months != null) {
            for (surface in listOf("か月", "カ月", "ヵ月", "ケ月", "箇月")) {
                put(surface, months)
                put("${surface}間") { n -> months(n)?.let { Reading(it.number, it.counter + "カン") } }
            }
        }
    }

    /**
     * The counter's reading for [number], or null when this isn't a counter this table knows. [compound] is for words
     * where 人 is read にん rather than ひとり/ふたり: 一人称 いちにんしょう, 二人前 ににんまえ.
     */
    fun reading(counter: String, number: Long, compound: Boolean = false): Reading? =
        if (compound && counter == "人") Reading(personNumber(number), "ニン") else COUNTERS[counter]?.invoke(number)

    /** The counter [surface] itself, when it is one this table knows. */
    fun exact(surface: String): String? = if (surface in COUNTERS) surface else null

    /** The number a run of kanji numerals (二十, 二〇, 百二十三) says. */
    fun number(text: String): Long? {
        if (text.isEmpty()) return null
        if (text.all { digitValue(it) != null }) return positional(text)
        var rest = text
        var total = 0L
        for ((unit, value) in listOf('兆' to 1_000_000_000_000L, '億' to 100_000_000L, '万' to 10_000L)) {
            val at = rest.indexOf(unit)
            if (at < 0) continue
            val head = rest.substring(0, at)
            val section = if (head.isEmpty()) 1L else smallNumber(head) ?: return null
            total += section * value
            rest = rest.substring(at + 1)
        }
        return total + (smallNumber(rest) ?: return null)
    }

    /** Whether [c] is one of the characters a Japanese number is written with. */
    fun isNumeral(c: Char): Boolean = digitValue(c) != null || c in "十百千万億兆"

    private fun funReading(n: Long): Reading {
        val number = numberReading(n)
        val counter = when (n % 10) {
            // 1分 いっぷん, 6分 ろっぷん, 8分 はっぷん.
            1L, 6L, 8L -> "プン"
            // 3分 さんぷん, 4分 よんぷん.
            3L, 4L -> "プン"
            // 10分 じゅっぷん, 20分 にじゅっぷん, 100分 ひゃっぷん.
            0L -> if (n >= 10) "プン" else "フン"
            // 2分 にふん, 5分 ごふん, 7分 ななふん, 9分 きゅうふん.
            else -> "フン"
        }
        val geminated = if (counter == "プン") geminateOrNull(number) else null
        return Reading(geminated ?: number, counter)
    }

    private fun personReading(n: Long): Reading = when (n) {
        1L -> Reading("", "ヒトリ")
        2L -> Reading("", "フタリ")
        else -> Reading(personNumber(n), "ニン")
    }

    /** 4人 is よにん, 7人 しちにん, 19人 じゅうくにん, but 9人 is きゅうにん. */
    private fun personNumber(n: Long): String {
        var number = if (n % 10 == 4L) replaceTail(numberReading(n), "ヨン", "ヨ") else numberReading(n)
        if (n % 10 == 7L) number = replaceTail(number, "ナナ", "シチ")
        if (n >= 10 && n % 10 == 9L) number = replaceTail(number, "キュウ", "ク")
        return number
    }

    private fun dayReading(n: Long): Reading = when (n) {
        1L -> Reading("イチ", "ニチ")
        2L -> Reading("", "フツカ")
        3L -> Reading("", "ミッカ")
        4L -> Reading("", "ヨッカ")
        5L -> Reading("", "イツカ")
        6L -> Reading("", "ムイカ")
        7L -> Reading("", "ナノカ")
        8L -> Reading("", "ヨウカ")
        9L -> Reading("", "ココノカ")
        10L -> Reading("", "トオカ")
        14L -> Reading("ジュウ", "ヨッカ")
        20L -> Reading("", "ハツカ")
        24L -> Reading("ニジュウ", "ヨッカ")
        // 17日 じゅうしちにち, 19日 じゅうくにち, 27日 にじゅうしちにち, 29日 にじゅうくにち.
        else -> Reading(dayNumber(n), "ニチ")
    }

    private fun dayNumber(n: Long): String {
        var number = numberReading(n)
        number = replaceTail(number, "ナナ", "シチ")
        number = replaceTail(number, "キュウ", "ク")
        return number
    }

    /** 2024年 is にせんにじゅうよねん, 2017年 にせんじゅうしちねん, 2019年 にせんじゅうくねん. */
    private fun yearNumber(n: Long): String {
        var number = replaceTail(numberReading(n), "ヨン", "ヨ")
        if (n >= 10 && n % 10 == 7L) number = replaceTail(number, "ナナ", "シチ")
        if (n >= 10 && n % 10 == 9L) number = replaceTail(number, "キュウ", "ク")
        return number
    }

    private fun monthReading(n: Long): Reading = when (n) {
        1L -> Reading("イチ", "ガツ")
        2L -> Reading("ニ", "ガツ")
        3L -> Reading("サン", "ガツ")
        4L -> Reading("", "シガツ")
        5L -> Reading("ゴ", "ガツ")
        6L -> Reading("ロク", "ガツ")
        7L -> Reading("", "シチガツ")
        8L -> Reading("ハチ", "ガツ")
        9L -> Reading("", "クガツ")
        10L -> Reading("ジュウ", "ガツ")
        11L -> Reading("ジュウイチ", "ガツ")
        12L -> Reading("ジュウニ", "ガツ")
        else -> Reading(numberReading(n), "ガツ")
    }

    private fun timeNumber(n: Long): String {
        val number = numberReading(n)
        return when (n % 10) {
            4L -> replaceTail(number, "ヨン", "ヨ") // 4時 よじ
            7L -> replaceTail(number, "ナナ", "シチ") // 7時 しちじ
            9L -> replaceTail(number, "キュウ", "ク") // 9時 くじ
            else -> number
        }
    }

    private fun tsuReading(n: Long): Reading? = when (n) {
        1L -> Reading("", "ヒトツ")
        2L -> Reading("", "フタツ")
        3L -> Reading("", "ミッツ")
        4L -> Reading("", "ヨッツ")
        5L -> Reading("", "イツツ")
        6L -> Reading("", "ムッツ")
        7L -> Reading("", "ナナツ")
        8L -> Reading("", "ヤッツ")
        9L -> Reading("", "ココノツ")
        10L -> Reading("", "トオ")
        else -> null
    }

    private fun sokuonReading(
        base: String,
        n: Long,
        p: Boolean = false,
        p3: Boolean = false,
        voice3: Boolean = false,
        six: Boolean = true,
    ): Reading {
        val number = numberReading(n)
        val geminated = geminateOrNull(number)?.takeIf { six || !number.endsWith("ロク") }
        val counter = when {
            geminated != null -> if (p) pForm(base) else base
            p3 && n % 10 == 3L -> pForm(base)
            voice3 && n % 10 == 3L -> voiced(base)
            else -> base
        }
        return Reading(geminated ?: number, counter)
    }

    /** The plain reading of [n], in katakana: 20 → ニジュウ, 2024 → ニセンニジュウヨン. */
    private fun numberReading(n: Long): String {
        if (n == 0L) return "ゼロ"
        var rest = n
        val out = StringBuilder()
        for ((value, name) in listOf(1_000_000_000_000L to "チョウ", 100_000_000L to "オク", 10_000L to "マン")) {
            val group = rest / value
            if (group == 0L) continue
            val groupName = numberReading(group)
            out.append(
                when (value) {
                    // 1兆 いっちょう, 8兆 はっちょう.
                    1_000_000_000_000L -> geminateOrNull(groupName) ?: groupName
                    else -> if (group == 1L) "イチ" else groupName
                },
            )
            out.append(name)
            rest %= value
        }
        if (rest > 0) out.append(smallNumberReading(rest))
        return out.toString()
    }

    private fun smallNumberReading(n: Long): String {
        val out = StringBuilder()
        val sen = n / 1000
        val hyaku = (n / 100) % 10
        val juu = (n / 10) % 10
        val ichi = n % 10
        if (sen > 0) out.append(
            when (sen) {
                1L -> "セン"
                3L -> "サンゼン"
                8L -> "ハッセン"
                else -> digitReading(sen) + "セン"
            },
        )
        if (hyaku > 0) out.append(
            when (hyaku) {
                1L -> "ヒャク"
                3L -> "サンビャク"
                6L -> "ロッピャク"
                8L -> "ハッピャク"
                else -> digitReading(hyaku) + "ヒャク"
            },
        )
        if (juu > 0) out.append(if (juu == 1L) "ジュウ" else digitReading(juu) + "ジュウ")
        if (ichi > 0) out.append(digitReading(ichi))
        return out.toString()
    }

    private fun digitReading(n: Long): String = when (n) {
        0L -> "ゼロ"
        1L -> "イチ"
        2L -> "ニ"
        3L -> "サン"
        4L -> "ヨン"
        5L -> "ゴ"
        6L -> "ロク"
        7L -> "ナナ"
        8L -> "ハチ"
        9L -> "キュウ"
        else -> n.toString()
    }

    /** [s]'s last unit as the small っ a following counter takes, or null when it doesn't take one. */
    private fun geminateOrNull(s: String): String? = when {
        s.endsWith("イチ") -> s.dropLast(2) + "イッ"
        s.endsWith("ロク") -> s.dropLast(2) + "ロッ"
        s.endsWith("ハチ") -> s.dropLast(2) + "ハッ"
        s.endsWith("ジュウ") -> s.dropLast(3) + "ジュッ"
        s.endsWith("ヒャク") -> s.dropLast(3) + "ヒャッ"
        s.endsWith("ビャク") -> s.dropLast(3) + "ビャッ"
        s.endsWith("ピャク") -> s.dropLast(3) + "ピャッ"
        else -> null
    }

    private fun replaceTail(s: String, tail: String, replacement: String): String =
        if (s.endsWith(tail)) s.dropLast(tail.length) + replacement else s

    /** An h-row counter after the small っ: ホン → ポン, フン → プン. */
    private fun pForm(s: String): String = when (s.firstOrNull()) {
        'ハ' -> "パ" + s.drop(1)
        'ヒ' -> "ピ" + s.drop(1)
        'フ' -> "プ" + s.drop(1)
        'ヘ' -> "ペ" + s.drop(1)
        'ホ' -> "ポ" + s.drop(1)
        else -> s
    }

    /** A counter voiced after 3: ホン → ボン, カイ → ガイ. */
    private fun voiced(s: String): String = when (s.firstOrNull()) {
        'カ' -> "ガ" + s.drop(1)
        'キ' -> "ギ" + s.drop(1)
        'ク' -> "グ" + s.drop(1)
        'ケ' -> "ゲ" + s.drop(1)
        'コ' -> "ゴ" + s.drop(1)
        'サ' -> "ザ" + s.drop(1)
        'シ' -> "ジ" + s.drop(1)
        'ス' -> "ズ" + s.drop(1)
        'セ' -> "ゼ" + s.drop(1)
        'ソ' -> "ゾ" + s.drop(1)
        'ハ' -> "バ" + s.drop(1)
        'ヒ' -> "ビ" + s.drop(1)
        'フ' -> "ブ" + s.drop(1)
        'ヘ' -> "ベ" + s.drop(1)
        'ホ' -> "ボ" + s.drop(1)
        else -> s
    }

    private fun positional(s: String): Long? {
        var n = 0L
        for (c in s) {
            val d = digitValue(c) ?: return null
            n = n * 10 + d
        }
        return n
    }

    private fun smallNumber(s: String): Long? {
        if (s.isEmpty()) return 0
        if (s.none { it in "十百千" }) return positional(s)
        var total = 0L
        var current = 0L
        for (c in s) {
            val d = digitValue(c)
            if (d != null) {
                current = d.toLong()
                continue
            }
            val unit = when (c) {
                '十' -> 10L
                '百' -> 100L
                '千' -> 1000L
                else -> return null
            }
            if (current == 0L) current = 1
            total += current * unit
            current = 0
        }
        return total + current
    }

    private fun digitValue(c: Char): Int? = when (c) {
        '0', '０', '〇', '零' -> 0
        '1', '１', '一', '壱' -> 1
        '2', '２', '二', '弐' -> 2
        '3', '３', '三', '参' -> 3
        '4', '４', '四' -> 4
        '5', '５', '五' -> 5
        '6', '６', '六' -> 6
        '7', '７', '七' -> 7
        '8', '８', '八' -> 8
        '9', '９', '九' -> 9
        else -> null
    }
}
