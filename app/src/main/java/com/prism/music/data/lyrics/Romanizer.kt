package com.prism.music.data.lyrics

import com.prism.music.R
import com.prism.music.tr
import android.os.Build
import com.prism.music.data.ModelStore
import com.prism.music.data.db.RomanizedDao
import com.prism.music.data.db.RomanizedEntity
import com.prism.music.data.lyrics.roman.Cyrillic
import com.prism.music.data.lyrics.roman.Greek
import com.prism.music.data.lyrics.roman.Hangul
import com.prism.music.data.lyrics.roman.Japanese
import com.prism.music.data.lyrics.roman.JapaneseEngine
import com.prism.music.data.lyrics.roman.JapaneseKanji
import com.prism.music.data.lyrics.roman.JapaneseReader
import com.prism.music.data.lyrics.roman.Kana
import com.prism.music.data.lyrics.roman.Script
import com.prism.music.data.lyrics.roman.SudachiJapanese
import com.prism.music.data.lyrics.roman.plainLatin
import com.prism.music.data.lyrics.RubySpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Lyrics in Latin letters, so a song in a script the listener can't read can still be sung along to.
 *
 * Each line goes to the best engine for its script: on the phone for Korean (Revised Romanization), Japanese (Kuromoji's
 * or Sudachi's readings, whichever dictionary is downloaded and chosen; kana alone without one), Cyrillic and Greek, which
 * keep a karaoke line's word timings; Google Translate's romanization (the endpoint [LyricsTranslator] uses) for Chinese
 * (pinyin), Arabic, Hebrew, the Indian scripts and the rest, spread over the line's timing; and Android's own
 * transliterator when that can't be reached. Results are kept per song, so they work offline after the first time.
 *
 * Google's romanization is line-level and has no ruby, so a line the local reader can read uses that reader for both
 * the romanization and the furigana. Google stays the fallback when no local dictionary can read the line.
 */
class Romanizer(
    private val http: OkHttpClient,
    private val dao: RomanizedDao,
    private val models: ModelStore,
    private val scope: CoroutineScope,
    /** Songs the listener turned romanization on (true) or off (false) for, from the player's ⋯ menu. */
    private val songPrefs: android.content.SharedPreferences? = null,
    /** The Japanese reader to use without the downloaded dictionary (tests read from Kuromoji's own). */
    private val japaneseForTests: Japanese? = null,
    /** Furigana's number+counter setting: true puts the number and the counter on their own spans/readings. */
    private val furiganaParts: () -> Boolean = { false },
) {
    private val _overrides = MutableStateFlow(
        songPrefs?.all?.mapNotNull { (k, v) -> (v as? Boolean)?.let { k to it } }?.toMap().orEmpty()
    )
    /** Per-song choices: song id → romanized (even in the phone's own script) or not. */
    val overrides: StateFlow<Map<String, Boolean>> = _overrides

    fun setOverride(songId: String, on: Boolean?) {
        songPrefs?.edit()?.apply { if (on == null) remove(songId) else putBoolean(songId, on) }?.apply()
        _overrides.value = if (on == null) _overrides.value - songId else _overrides.value + (songId to on)
    }

    /**
     * A title in Latin letters, for finding it by typing (1.8.3, #363: "tori no uta" finds とりのうた): the phone's own
     * engines only (kana, Hangul, Cyrillic, Greek; kanji only once the Japanese dictionary is downloaded), never the
     * network. Null when it needs none, or no engine here can spell all of it.
     */
    suspend fun spellTitle(text: String): String? = withContext(Dispatchers.Default) {
        val scripts = Script.foreignIn(text)
        if (scripts.isEmpty()) return@withContext null
        val japanese = Script.KANA in scripts || (Script.HAN in scripts && japaneseReady)
        runCatching { localSpelling(LyricLine(0, text), scripts, japanese) }.getOrNull()?.first?.takeIf { it.isNotBlank() && it != text }
    }

    /** The dictionary that reads Japanese now: Sudachi whenever it's on the phone, else IPADIC. */
    private fun localEngine(): JapaneseEngine? = when {
        japaneseForTests != null -> null
        models.ready(ModelStore.Model.SUDACHI) -> JapaneseEngine.SUDACHI
        models.ready(ModelStore.Model.JAPANESE) -> JapaneseEngine.IPADIC
        else -> null
    }

    /** The engine and its dictionary's version, for caches: a new download reads the songs again. */
    val readingKey: String
        get() = localEngine()?.let { "${it.name}:${it.model.sha256}" } ?: if (japaneseForTests != null) "test" else "none"

    /** Whether kanji can be read on the phone (Sudachi or IPADIC is downloaded). */
    val japaneseReady: Boolean get() = japaneseForTests != null || localEngine() != null

    /** Furigana uses the same reader as the romanization, so the two always agree. */
    val furiganaReady: Boolean get() = japaneseReady

    @Serializable
    private data class Stored(val t: String, val w: List<String>? = null, val b: String? = null, val bw: List<String>? = null)

    private val json = Json { ignoreUnknownKeys = true }
    private val memory = object : LinkedHashMap<String, List<LyricLine?>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine?>>?) = size > 24
    }

    private val _needsJapanese = MutableStateFlow(false)
    /** A Japanese song was shown without the reading data: kanji were spelled out by Google instead (less well). */
    val needsJapanese: StateFlow<Boolean> = _needsJapanese

    // Kuromoji takes tens of MB, Sudachi maps 330 MB of dictionary: loaded when a Japanese song needs it, let go a few
    // minutes after the last (Sudachi's is closed then, not just dropped).
    private val jaLock = Mutex()
    @Volatile private var japanese: Pair<JapaneseEngine, JapaneseReader>? = null
    private var releaseJob: Job? = null

    private suspend fun japaneseReader(): JapaneseReader? {
        japaneseForTests?.let { return it }
        val engine = localEngine() ?: return null
        val reader = jaLock.withLock {
            val held = japanese
            if (held?.first == engine) held.second
            else {
                // Another dictionary than the one loaded (the listener switched): let that one go first.
                held?.second?.let { runCatching { it.close() } }
                runCatching { withContext(Dispatchers.Default) { loadJapanese(engine) } }
                    .onFailure { android.util.Log.w("PrismRoman", "Couldn't load the Japanese dictionary: $it") }
                    .getOrNull()?.also { japanese = engine to it }
            }
        }
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(RELEASE_AFTER_MS)
            jaLock.withLock {
                japanese?.second?.let { runCatching { it.close() } }
                japanese = null
            }
        }
        return reader
    }

    /** Opens the chosen dictionary, unpacked by [ModelStore]. */
    private fun loadJapanese(engine: JapaneseEngine): JapaneseReader = when (engine) {
        JapaneseEngine.IPADIC -> Japanese.load(models.dir(engine.model))
        JapaneseEngine.SUDACHI -> SudachiJapanese.load(models.dir(engine.model))
    }

    /**
     * [lines] in Latin letters, line for line (null where a line needs none: it's already Latin, or in one of [skip],
     * the scripts the listener reads). Word-timed lines come back word-timed. [network] allows Google's romanization.
     *
     * Whether it's Japanese is decided for the whole song, by any kana in its lyrics or its [title]: a line of only kanji
     * looks Chinese on its own (#370).
     */
    suspend fun romanize(songId: String, lines: List<LyricLine>, skip: Set<Script>, network: Boolean = true, title: String = ""): List<LyricLine?> =
        withContext(Dispatchers.Default) {
            val japaneseSong = Script.KANA in Script.foreignIn(title) || lines.any { Script.KANA in Script.foreignIn(it.text) }
            val jaReady = japaneseReady
            val key = "$songId|$VERSION|$readingKey|${if (furiganaParts()) "parts" else "whole"}|${if (japaneseSong) "j" else ""}|${skip.sorted().joinToString(",")}|" +
                lines.joinToString("\n") { it.text + "|" + it.backgroundLine?.text.orEmpty() }.hashCode()
            synchronized(memory) { memory[key] }?.let { return@withContext it }
            runCatching { dao.get(key) }.getOrNull()?.let { saved ->
                runCatching { json.decodeFromString<List<Stored?>>(saved.content) }.getOrNull()?.takeIf { it.size == lines.size }?.let { stored ->
                    val rebuilt = lines.mapIndexed { i, l -> stored[i]?.let { rebuild(l, it) } }
                    synchronized(memory) { memory[key] = rebuilt }
                    return@withContext rebuilt
                }
            }

            fun wanted(text: String) = (Script.foreignIn(text) - skip)
            val out = arrayOfNulls<Stored>(lines.size)
            val online = ArrayList<Int>() // lines (or their background) for Google
            var complete = true
            var usedGoogleForJapanese = false
            for ((i, line) in lines.withIndex()) {
                if (line.isGap) continue
                val bg = line.backgroundLine
                val main = wanted(line.text)
                val back = bg?.let { wanted(it.text) }.orEmpty()
                if (main.isEmpty() && back.isEmpty()) continue
                val localMain = if (main.isEmpty()) null else localPieces(line, main, japaneseSong, network)
                val localBack = if (back.isEmpty() || bg == null) null else localPieces(bg, back, japaneseSong, network)
                if ((main.isEmpty() || localMain != null) && (back.isEmpty() || localBack != null)) {
                    out[i] = Stored(
                        t = localMain?.first ?: line.text, w = localMain?.second,
                        b = localBack?.first ?: bg?.text, bw = localBack?.second,
                    )
                } else {
                    online += i
                    if (japaneseSong && (Script.HAN in main || Script.HAN in back)) usedGoogleForJapanese = true
                }
            }
            if (usedGoogleForJapanese && !jaReady) _needsJapanese.value = true

            if (online.isNotEmpty()) {
                val texts = online.flatMap { i -> listOf(lines[i].text, lines[i].backgroundLine?.text.orEmpty()) }
                val fromGoogle = if (network) runCatching { google(texts, japaneseSong) }.onFailure {
                    android.util.Log.w("PrismRoman", "Google's romanization failed: $it")
                }.getOrNull() else null
                // Android's own transliterator for what Google didn't spell out, but never in a Japanese song: it reads kanji
                // as Chinese and leaves kana as they are (#256, "jūn の zhōng" under a RADWIMPS song). Such a line stays as
                // it is and is asked for again next time.
                val romanized = texts.mapIndexed { k, t ->
                    fromGoogle?.getOrNull(k)?.takeIf { it.isNotBlank() } ?: if (t.isBlank() || japaneseSong) "" else device(t)
                }
                for ((k, i) in online.withIndex()) {
                    val main = romanized.getOrNull(2 * k)?.takeIf { it.isNotBlank() }
                    val back = romanized.getOrNull(2 * k + 1)?.takeIf { it.isNotBlank() }
                    if (main == null && wanted(lines[i].text).isNotEmpty()) { complete = false; continue }
                    out[i] = Stored(t = main ?: lines[i].text, b = back ?: lines[i].backgroundLine?.text)
                }
            }

            val result = lines.mapIndexed { i, l -> out[i]?.let { rebuild(l, it) } }
            synchronized(memory) { memory[key] = result }
            // Saved only when every line got its spelling (a failed request is tried again next time).
            if (complete) runCatching {
                dao.put(RomanizedEntity(key, json.encodeToString(out.toList()), System.currentTimeMillis()))
            }
            result
        }

    /** A line's romanization pieces and its furigana, worked out by the same reader and split setting. */
    private data class LineReading(val pieces: List<String>, val spans: List<RubySpan>)

    private val readingMemory = object : LinkedHashMap<String, LineReading>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LineReading>?) = size > 128
    }

    /**
     * Each line's kanji with the hiragana above it ([RubySpan]), line for line; null while no Japanese dictionary is on
     * the phone. A number+counter run is read even in a line with no kanji, so 20分 shows にじゅっぷん in both views.
     */
    suspend fun furigana(lines: List<LyricLine>): List<List<RubySpan>>? {
        if (lines.none { hasKanji(it.text) || it.text.any { c -> c.isDigit() } }) return emptyList()
        val reader = japaneseReader() ?: return null
        val split = furiganaParts()
        return withContext(Dispatchers.Default) {
            lines.map { line ->
                // Raw lines (from other callers) still carry their readings in parentheses; the player passes cleaned
                // lines with [LyricLine.sourceRuby] instead.
                val raw = if (line.sourceRuby.isEmpty()) SourceFurigana.of(line.text) else null
                if (raw != null) {
                    raw.merge(reader.furigana(JapaneseKanji.fromChinese(raw.clean), split))
                } else {
                    readingOf(line, reader, split).spans
                }
            }
        }
    }

    /**
     * A line's pieces and furigana, from the same [reader] and split setting, kept by dictionary version and the
     * source's own readings. The romanization and the furigana of one line can't then drift apart.
     */
    private fun readingOf(line: LyricLine, reader: JapaneseReader, splitCounters: Boolean): LineReading {
        val key = "$readingKey|${if (splitCounters) "parts" else "whole"}|${line.text}|" +
            line.sourceRuby.joinToString(";") { "${it.start}-${it.end}:${it.reading}" }
        synchronized(readingMemory) { readingMemory[key] }?.let { return it }
        val text = JapaneseKanji.fromChinese(line.text)
        val pieces = reader.pieces(text, splitCounters).toMutableList()
        val dictionary = reader.furigana(text, splitCounters)
        val spans = if (line.sourceRuby.isEmpty()) dictionary else mergeFurigana(line.sourceRuby, dictionary)
        if (line.sourceRuby.isNotEmpty()) applySourceRuby(pieces, line.sourceRuby, dictionary)
        val reading = LineReading(pieces, spans)
        synchronized(readingMemory) { readingMemory[key] = reading }
        return reading
    }

    /**
     * Source readings win over the dictionary's: the romanization of the kanji run is replaced by the source's reading,
     * keeping the okurigana the dictionary read around it (食べる with 食(しょく) becomes shokuberu, not taberu).
     */
    private fun applySourceRuby(pieces: MutableList<String>, source: List<RubySpan>, dictionary: List<RubySpan>) {
        for (s in source) {
            val to = Kana.romanize(s.reading).lowercase()
            if (to.isEmpty() || pieces.isEmpty()) continue
            val d = dictionary.firstOrNull { it.start == s.start && it.end == s.end }
                ?: dictionary.firstOrNull { it.start < s.end && s.start < it.end }
            val from = d?.reading?.let { Kana.romanize(it).lowercase() }
            var i = s.start.coerceIn(0, pieces.lastIndex)
            while (i >= 0 && pieces[i].isEmpty()) i--
            if (i < 0) i = s.start.coerceIn(0, pieces.lastIndex)
            val piece = pieces[i]
            pieces[i] = if (from != null && from.isNotEmpty() && from in piece) piece.replaceFirst(from, to) else to
        }
    }

    /**
     * The line spelled out on the phone, if every script it needs has an engine here: its text, and each word's
     * spelling (lined up with [LyricLine.words]) for a word-timed line.
     */
    private suspend fun localPieces(line: LyricLine, scripts: Set<Script>, japaneseSong: Boolean, network: Boolean): Pair<String, List<String>?>? {
        val spelled = localSpelling(line, scripts, japaneseSong) ?: return null
        // A word the Japanese dictionary doesn't know keeps its kanji (#256: 臓 on its own): Google spells that line out
        // whole when it can be asked.
        if (network && japaneseSong && (Script.HAN in Script.foreignIn(spelled.first) || Script.KANA in Script.foreignIn(spelled.first))) return null
        return spelled
    }

    private suspend fun localSpelling(line: LyricLine, scripts: Set<Script>, japaneseSong: Boolean): Pair<String, List<String>?>? {
        // Google's romanization endpoint returns no ruby, so it can't be preferred for both views without making them
        // disagree. The local reader (Sudachi, else IPADIC) reads both whenever it can; Google stays the fallback.
        val japanese = if (japaneseSong && (Script.KANA in scripts || Script.HAN in scripts)) japaneseReader() else null
        val split = furiganaParts()
        val engine: ((String) -> List<String>)? = when {
            // The same reading gives the furigana, so a source's own reading is spelled here too.
            japanese != null && scripts.all { it == Script.KANA || it == Script.HAN } -> { text ->
                val readingLine = if (text == line.text) line else line.copy(text = text)
                readingOf(readingLine, japanese, split).pieces
            }
            Script.HAN in scripts -> null
            scripts.all { it == Script.KANA } -> Kana::pieces
            scripts.all { it == Script.HANGUL } -> Hangul::pieces
            scripts.all { it == Script.CYRILLIC } -> Cyrillic::pieces
            scripts.all { it == Script.GREEK } -> Greek::pieces
            else -> null
        }
        engine ?: return null
        // A Japanese song's kanji in their Chinese forms (#370) are read as the Japanese ones.
        fun read(text: String) = engine(if (japaneseSong) JapaneseKanji.fromChinese(text) else text)
        if (line.words.isEmpty()) return read(line.text).joinToString("").trim().squeeze() to null
        val joined = line.words.joinToString("") { it.text }
        val pieces = read(joined)
        val words = ArrayList<String>(line.words.size)
        var at = 0
        for (w in line.words) {
            words += pieces.subList(at, (at + w.text.length).coerceAtMost(pieces.size)).joinToString("")
            at += w.text.length
        }
        // A word whose spelling starts with the space that separates it from the one before: the space goes on that one.
        for (k in 1 until words.size) if (words[k].startsWith(" ")) {
            words[k] = words[k].trimStart()
            if (!words[k - 1].endsWith(" ")) words[k - 1] = words[k - 1] + " "
        }
        return words.joinToString("").trim().squeeze() to words
    }

    /** Builds the romanized line from what's stored, with the original's timings. */
    private fun rebuild(line: LyricLine, s: Stored): LyricLine {
        val words = when {
            line.words.isEmpty() -> emptyList()
            s.w != null && s.w.size == line.words.size -> line.words.mapIndexed { k, w -> w.copy(text = s.w[k]) }.filter { it.text.isNotEmpty() }
            else -> spread(s.t, line.words)
        }
        val bgWords = when {
            line.background.isEmpty() || s.b == null -> emptyList()
            s.bw != null && s.bw.size == line.background.size -> line.background.mapIndexed { k, w -> w.copy(text = s.bw[k]) }.filter { it.text.isNotEmpty() }
            else -> spread(s.b, line.background)
        }
        return line.copy(text = s.t, words = words, background = bgWords)
    }

    /**
     * Google's romanization of [texts] (blank ones stay blank), in as few requests as it takes. In a [japaneseSong] the
     * kana and kanji lines go together as Japanese: left to itself, Google takes a line of only kanji for Chinese (#370).
     */
    private suspend fun google(texts: List<String>, japaneseSong: Boolean): List<String> = withContext(Dispatchers.IO) {
        val out = Array(texts.size) { "" }
        // One request per script: Google detects one language per request, and a Hindi line sent with Chinese ones
        // came back in a scholarly spelling.
        val groups = texts.withIndex().filter { it.value.isNotBlank() }.groupBy { v ->
            Script.foreignIn(v.value).minOrNull().let { if (japaneseSong && it == Script.HAN) Script.KANA else it }
        }
        var single = 0
        for ((script, wanted) in groups) {
            val japanese = japaneseSong && script == Script.KANA
            val asked = wanted.map { (if (japanese) JapaneseKanji.fromChinese(it.value) else it.value).trim() }
            val language = if (japanese) "ja" else "auto"
            val batched = runCatching { request(asked, language) }.getOrNull()
            if (batched != null && batched.size == wanted.size) {
                wanted.forEachIndexed { k, v -> out[v.index] = clean(v.value, batched[k]) }
            } else {
                // The lines came back run together (Google does that with some languages): one at a time instead.
                for ((k, v) in wanted.withIndex()) {
                    if (single++ >= MAX_SINGLE) break
                    // One line refused (Google resting after many requests, or no romanization in its answer) no longer
                    // sinks the whole song (#256): that line is left for next time.
                    out[v.index] = runCatching { clean(v.value, request(listOf(asked[k]), language).joinToString(" ")) }.getOrDefault("")
                }
            }
        }
        out.toList()
    }

    private fun request(texts: List<String>, language: String): List<String> {
        val req = Request.Builder()
            // With a translation asked for too, Google spells Hindi and the like the everyday way ("mere sapnon ki
            // rani"); asked for the romanization alone, it gives the scholarly one ("mērē sapanōṁ kī rānī").
            .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$language&tl=en&dt=t&dt=rm")
            .post(FormBody.Builder().add("q", texts.joinToString("\n")).build())
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException(tr(R.string.translate_returned_http, resp.code))
            val root = Json.parseToJsonElement(resp.body.string()).jsonArray
            // The romanization of what was sent is the 4th field of an entry with no translation in it.
            val text = (root.getOrNull(0) as? JsonArray).orEmpty().mapNotNull { seg ->
                (seg as? JsonArray)?.takeIf { it.getOrNull(0) !is JsonPrimitive || (it[0] as JsonPrimitive).contentOrNull == null }
                    ?.getOrNull(3)?.let { (it as? JsonPrimitive)?.contentOrNull }
            }.joinToString("")
            if (text.isBlank()) throw IOException(tr(R.string.no_romanization_in_the_answer))
            return text.trimEnd('\n').split("\n")
        }
    }

    /** Google's spelling, tidied: Thai's ISO marks taken off (they make it unreadable), spaces evened out. */
    private fun clean(original: String, roman: String): String {
        val thai = Script.THAI in Script.foreignIn(original)
        return (if (thai) plainLatin(roman) else roman).trim().squeeze()
    }

    /** Android's own transliterator (Android 10+), for when Google can't be reached. Empty when there's none. */
    private fun device(text: String): String {
        if (Build.VERSION.SDK_INT < 29) return ""
        val han = Script.HAN in Script.foreignIn(text)
        return runCatching {
            // Pinyin keeps its tone marks; the scholarly marks of the other scripts come off.
            val id = if (han) "Han-Latin" else "Any-Latin; Latin-ASCII"
            android.icu.text.Transliterator.getInstance(id).transliterate(text)
        }.getOrDefault("").trim().squeeze()
    }

    private fun String.squeeze() = replace(Regex("\\s{2,}"), " ")

    companion object {
        /** Bumped when an engine changes, so songs romanized the old way are romanized again. */
        // 3 (1.7.9.1, #256): Japanese songs spelled by Android's transliterator as Chinese are spelled again.
        // 4 (1.8.2.1, #370): Japanese songs with kanji read as Chinese are spelled again.
        // 5: Arabic numbers are read as Japanese numerals, so counters and dates come out right (2人 is ふたり, 4月 is
        // しがつ): those songs are spelled again.
        // 6: counters change with the number before them (20分 is にじゅっぷん, 3本 さんぼん) and the romanization spells
        // the whole number and counter (futari, not "2futari"): those songs are spelled again.
        // 7: Sudachi reads both romanization and furigana when it's on the phone, source ruby reaches both, and the
        // number+counter setting changes their pieces: those songs are spelled again.
        private const val VERSION = 7
        private const val RELEASE_AFTER_MS = 3 * 60_000L
        /** Line-by-line requests are only made for this many lines (a long song's worth). */
        private const val MAX_SINGLE = 80

        /**
         * Spreads a romanized line ([text], from Google) over [words]' timing: each romanized word is sung at the same
         * share of the line as the letters it came from.
         */
        fun spread(text: String, words: List<LyricWord>): List<LyricWord> {
            val tokens = text.split(' ').filter { it.isNotEmpty() }
            if (tokens.isEmpty() || words.isEmpty()) return emptyList()
            val total = words.sumOf { it.text.length }.coerceAtLeast(1)
            fun timeAt(fraction: Double): Long {
                var pos = fraction * total
                for (w in words) {
                    val len = w.text.length
                    if (pos <= len || w === words.last()) {
                        val f = if (len == 0) 0.0 else (pos / len).coerceIn(0.0, 1.0)
                        return w.startMs + ((w.endMs - w.startMs) * f).toLong()
                    }
                    pos -= len
                }
                return words.last().endMs
            }
            // Letters plus the spaces between words.
            val romanTotal = (tokens.sumOf { it.length } + tokens.size - 1).toDouble().coerceAtLeast(1.0)
            var at = 0.0
            return tokens.mapIndexed { k, t ->
                val start = timeAt(at / romanTotal)
                at += t.length
                val end = timeAt(at / romanTotal)
                at += 1
                LyricWord(start, end, if (k < tokens.lastIndex) "$t " else t)
            }
        }
    }
}
