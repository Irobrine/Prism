package com.prism.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.prism.music.data.lyrics.LyricLine
import com.prism.music.data.lyrics.RubySpan
import com.prism.music.data.lyrics.hasKanji
import com.prism.music.data.lyrics.roman.Script
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import kotlin.math.roundToInt

/** How big a reading is against the line it sits over, and how tall its row is. */
private const val RUBY_SCALE = 0.45f
private const val RUBY_LINE = 0.85f
/** How far above the line's baseline a reading's centre sits, as a share of the line's size. Its kanji stay just below. */
private const val RUBY_CENTER = 1.12f
/** How much of its kanji's width a reading may use, leaving room between it and the readings beside it. */
private const val RUBY_FIT = 0.92f

/**
 * Each line's kanji and its hiragana reading, line for line, when furigana is on and this is a Japanese song with a
 * Japanese dictionary on the phone (Sudachi or IPADIC); null otherwise.
 */
@Composable
fun rememberFurigana(state: LyricsState): List<List<RubySpan>>? {
    val c = LocalContainer.current
    val settings = LocalAppSettings.current
    val lines = state.lines
    if (!settings.lyricsFurigana || lines.isEmpty()) return null
    // A song is Japanese as the romanizer decides it: any kana in its title or words (a line of only kanji alone looks
    // Chinese).
    val japanese = remember(state.song.id, state.song.title, lines) {
        Script.KANA in Script.foreignIn(state.song.title) || lines.any { Script.KANA in Script.foreignIn(it.text) }
    }
    if (!japanese) return null
    // A line with only a number and a counter (20分, 20ふん) still has a reading worth showing.
    if (lines.none { hasKanji(it.text) || it.text.any { c -> c.isDigit() } }) return null
    val modelStates by c.models.states.collectAsState()
    val ready = remember(modelStates, settings) { c.romanizer.furiganaReady }
    val ruby by produceState<List<List<RubySpan>>?>(null, state.song.id, lines, ready, settings) {
        value = if (!ready) null else runCatching { c.romanizer.furigana(lines) }
            .onFailure { android.util.Log.w("PrismFurigana", "Couldn't read the kanji: $it") }
            .getOrNull()
    }
    return ruby
}

/** A reading's style, measured and drawn the same way. */
private fun TextStyle.rubyStyle(size: TextUnit): TextStyle = copy(
    fontSize = size, lineHeight = size * RUBY_LINE, fontWeight = FontWeight.Normal,
    letterSpacing = 0.sp, textAlign = TextAlign.Start, textDirection = TextDirection.Ltr,
    // The reading's own font padding isn't wanted here: [rememberRubyRoom] measures what it needs.
    lineHeightStyle = null,
)

/**
 * How much room (in sp) the tallest of [ruby]'s readings needs above the text, or null when there are none. Every row of
 * a wrapped line keeps this same room, so a later row's reading can't sit down over the row above it.
 */
@Composable
internal fun rememberRubyRoom(ruby: List<RubySpan>?, style: TextStyle): Float? {
    if (ruby.isNullOrEmpty()) return null
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val rubyStyle = remember(style) { style.rubyStyle(style.fontSize * RUBY_SCALE) }
    return remember(ruby, rubyStyle, density) {
        val tallest = ruby.maxOf { measurer.measure(AnnotatedString(it.reading), rubyStyle, maxLines = 1).size.height }
        with(density) { tallest.toSp().value }
    }
}

/** [style] with [room] sp kept above each of its rows for the readings (wrapped lines need it too). */
internal fun TextStyle.withRubyRoom(room: Float): TextStyle {
    if (!fontSize.isSp || !lineHeight.isSp) return this
    return copy(
        lineHeight = (lineHeight.value + room).sp,
        // The extra room goes below each row, as the gap the next row's readings sit in.
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.LastLineBottom),
    )
}

/**
 * How the readings over a sung line move with it: [kind] is the line's animation ([SungKind]), [line] and [clock] give
 * which word is being sung, [dim] is where they're still to come, and the weights are the grow animation's ends.
 */
internal data class RubyMotion(
    val kind: SungKind,
    val line: LyricLine,
    val clock: State<Long>,
    val dim: Color = Color.Unspecified,
    val dimAlpha: Float = 1f,
    val lightWeight: Int = 300,
    val sungWeight: Int = 500,
)

/**
 * Draws [ruby] above the text [content] draws, using [layout] (that text's own layout) to line each reading up over its
 * kanji. The room above comes from a matching top padding, so nothing else moves. [motion] makes the readings follow the
 * line's animation: wiped through with its sweep, brightening as its words are sung, or appearing with its typewriter.
 */
@Composable
internal fun RubyOverlay(
    ruby: List<RubySpan>?,
    style: TextStyle,
    room: Float?,
    layout: TextLayoutResult?,
    modifier: Modifier,
    color: Color,
    fill: Brush? = null,
    motion: RubyMotion? = null,
    content: @Composable (Modifier) -> Unit,
) {
    if (ruby.isNullOrEmpty() || room == null) { content(modifier); return }
    val density = LocalDensity.current
    val rubySize = style.fontSize * RUBY_SCALE
    val fontPx = with(density) { if (style.fontSize.isSp) style.fontSize.toPx() else 0f }
    val pad = with(density) { room.sp.toDp() }
    val measurer = rememberTextMeasurer()
    val rubyStyle = remember(style, rubySize) { style.rubyStyle(rubySize) }
    // Each reading, split where its kanji wrap onto another row. One that's long for the kanji under it (a single kanji
    // read as こころ, say) shrinks to fit their width, so the readings of consecutive kanji don't run into each other.
    // A growing line's reading is measured at each weight step too, so it thickens with the word it's over.
    val pieces = remember(layout, ruby) { layout?.let { rubyPieces(it, ruby) }.orEmpty() }
    val growSteps = if (motion?.kind == SungKind.GROW) ((motion.sungWeight - motion.lightWeight) / WEIGHT_STEP).coerceAtLeast(0) else 0
    val measured = remember(pieces, rubyStyle, layout, motion?.kind, motion?.lightWeight, motion?.sungWeight, growSteps) {
        layout?.let { l ->
            pieces.map { piece ->
                val span = (l.getBoundingBox(piece.end).right - l.getBoundingBox(piece.start).left).coerceAtLeast(0f)
                val probeWeight = if (growSteps > 0) FontWeight(motion?.sungWeight ?: 0) else FontWeight.Normal
                val probe = measurer.measure(AnnotatedString(piece.reading), rubyStyle.copy(fontWeight = probeWeight), maxLines = 1)
                val fit = if (span > 0f && probe.size.width > 0f) (span * RUBY_FIT / probe.size.width).coerceIn(0f, 1f) else 1f
                val fitted = rubyStyle.copy(fontSize = rubySize * fit, lineHeight = rubySize * fit * RUBY_LINE)
                val readings = if (growSteps == 0) listOf(measurer.measure(AnnotatedString(piece.reading), fitted, maxLines = 1))
                else (0..growSteps).map { k ->
                    val weight = FontWeight((motion?.lightWeight ?: 0) + k * WEIGHT_STEP)
                    measurer.measure(AnnotatedString(piece.reading), fitted.copy(fontWeight = weight), maxLines = 1)
                }
                piece to readings
            }
        }.orEmpty()
    }
    // Each word's span in the line, for timing a reading to the word it's over.
    val offsets = remember(motion?.line) { motion?.line?.wordOffsets().orEmpty() }
    Box(modifier, propagateMinConstraints = true) {
        content(modifier.padding(top = pad))
        val l = layout ?: return@Box
        Canvas(Modifier.matchParentSize()) {
            val roomPx = pad.toPx()
            val now = motion?.clock?.value ?: 0L
            val sung = if (motion != null && (motion.kind == SungKind.SWEEP || motion.kind == SungKind.GROW)) {
                sungChars(motion.line, offsets, now)
            } else 0f
            for ((piece, readings) in measured) drawRuby(l, piece, readings, roomPx, fontPx, color, fill, l.size.width.toFloat(), motion, offsets, now, sung)
        }
    }
}

/** A plain line of text with [ruby] above it (for lines drawn without [SungLine]'s animations). */
@Composable
internal fun TextWithFurigana(
    text: String,
    ruby: List<RubySpan>?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val room = rememberRubyRoom(ruby, style)
    val st = if (room != null) style.withRubyRoom(room) else style
    RubyOverlay(ruby, st, room, layout, modifier, color) { m ->
        Text(text, m, style = st, color = color, maxLines = maxLines, overflow = overflow, onTextLayout = { layout = it })
    }
}

/** A translation of one [RubySpan] for one row of a wrapped line. */
private data class RubyPiece(val reading: String, val start: Int, val end: Int)

private fun rubyPieces(layout: TextLayoutResult, ruby: List<RubySpan>): List<RubyPiece> {
    val textLength = layout.layoutInput.text.length
    if (textLength <= 0) return emptyList()
    val out = ArrayList<RubyPiece>(ruby.size)
    for (span in ruby) {
        if (span.reading.isEmpty()) continue
        val start = span.start.coerceIn(0, textLength - 1)
        val end = (span.end - 1).coerceIn(start, textLength - 1)
        val first = layout.getLineForOffset(start)
        val last = layout.getLineForOffset(end)
        if (first == last) {
            out += RubyPiece(span.reading, start, end)
            continue
        }
        // A long run wrapped over more than one row: its reading is shared out by the kanji on each row.
        val total = end - start + 1
        for (row in first..last) {
            val from = maxOf(start, layout.getLineStart(row))
            val to = minOf(end, layout.getLineEnd(row, visibleEnd = true) - 1)
            if (from > to) continue
            val a = span.reading.length * (from - start) / total
            val b = span.reading.length * (to - start + 1) / total
            if (b <= a) continue
            out += RubyPiece(span.reading.substring(a, b), from, to)
        }
    }
    return out
}

/** Each word's span in [this] line's text: where it starts, and just past where it ends. */
private fun LyricLine.wordOffsets(): List<Pair<Int, Int>> {
    var acc = 0
    return words.map { w -> val start = acc; acc += w.text.length; start to acc }
}

/** How much of [piece]'s kanji has been sung, from 0 (none) to 1 (all). */
private fun sungFraction(piece: RubyPiece, sung: Float): Float =
    ((sung - piece.start) / (piece.end - piece.start + 1).toFloat()).coerceIn(0f, 1f)

/** How visible the word at [offset] is in the typewriter animation at [now], from 0 to 1 (in tenths, as it types). */
private fun typewriterAlpha(line: LyricLine, offsets: List<Pair<Int, Int>>, now: Long, offset: Int): Float {
    val i = line.words.indices.firstOrNull { offset >= offsets[it].first && offset < offsets[it].second } ?: return 1f
    val w = line.words[i]
    val start = w.startAt(offset - offsets[i].first)
    val shown = ((now - start).toFloat() / TYPE_IN_MS).coerceIn(0f, 1f)
    return (shown * 10).roundToInt() / 10f
}

/**
 * [fill] across the line's [width] px, moved so the reading drawn at [x] samples the same run of color as the kanji
 * under it. [fadeFrom] and [fadeTo] (px) fade it out past the sweep's edge; left unset, the whole run is drawn.
 */
private class LineFill(
    private val fill: ShaderBrush,
    private val width: Float,
    private val x: Float,
    private val fadeFrom: Float = Float.NEGATIVE_INFINITY,
    private val fadeTo: Float = Float.POSITIVE_INFINITY,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val shader = fill.createShader(Size(width, size.height))
        // The reading is drawn from its own left edge; move the line's run so this reading sits where its kanji does.
        shader.setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-x, 0f) })
        if (fadeFrom == Float.NEGATIVE_INFINITY) return shader
        val mask = android.graphics.LinearGradient(
            fadeFrom - x, 0f, fadeTo - x, 0f, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP,
        )
        return android.graphics.ComposeShader(shader, mask, android.graphics.PorterDuff.Mode.DST_IN)
    }
}

private fun DrawScope.drawRuby(
    layout: TextLayoutResult, piece: RubyPiece, readings: List<TextLayoutResult>, room: Float, fontPx: Float, color: Color,
    fill: Brush?, lineWidth: Float, motion: RubyMotion?, offsets: List<Pair<Int, Int>>, now: Long, sung: Float,
) {
    val first = layout.getBoundingBox(piece.start)
    val last = layout.getBoundingBox(piece.end)
    val fraction = if (motion != null && (motion.kind == SungKind.GROW || motion.kind == SungKind.SWEEP)) {
        sungFraction(piece, sung)
    } else 0f
    // A growing reading has a layout for each weight step; the others have just the one.
    val reading = if (readings.size > 1) readings[(fraction * readings.lastIndex).roundToInt().coerceIn(0, readings.lastIndex)] else readings.first()
    val center = (first.left + last.right) / 2f
    val w = reading.size.width
    val x = (center - w / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    // Above the row's letters. The readings' centres share one line, so a shrunk reading sits at the same height as its
    // neighbours, and the whole row sits a little clear of the kanji and clear of the row above.
    val baseline = layout.getLineBaseline(layout.getLineForOffset(piece.start))
    val y = (room + baseline - fontPx * RUBY_CENTER - reading.size.height / 2f).coerceAtLeast(0f)
    val at = Offset(x, y)
    // The sung part in the look's fill: a Color fills' gradient runs across the whole line, as it does under the kanji.
    fun drawLit(alpha: Float = 1f, fadeFrom: Float = Float.NEGATIVE_INFINITY, fadeTo: Float = Float.POSITIVE_INFINITY) {
        when {
            fill is ShaderBrush -> drawText(reading, brush = LineFill(fill, lineWidth, x, fadeFrom, fadeTo), alpha = alpha, topLeft = at)
            fadeFrom == Float.NEGATIVE_INFINITY -> drawText(reading, color = color, alpha = alpha, topLeft = at)
            else -> {
                val lit = Brush.horizontalGradient(0f to color, 1f to color.copy(alpha = 0f), startX = fadeFrom, endX = fadeTo)
                drawText(reading, brush = lit, alpha = alpha, topLeft = at)
            }
        }
    }
    when (motion?.kind) {
        null, SungKind.PLAIN -> drawLit()
        // Word by word: the reading appears as the word it's over does.
        SungKind.TYPEWRITER -> {
            val a = typewriterAlpha(motion.line, offsets, now, piece.start)
            if (a > 0f) drawLit(alpha = a)
        }
        // Words grow from dim to bright: the reading thickens and brightens with the kanji it's over.
        SungKind.GROW -> {
            val lit = (fraction * 10).roundToInt()
            drawLit(alpha = motion.dimAlpha + (1f - motion.dimAlpha) * lit / 10f)
        }
        // The light wipes through the line: it wipes through the reading the same way.
        SungKind.SWEEP -> {
            if (fraction < 1f && motion.dim.alpha > 0f) drawText(reading, color = motion.dim, topLeft = at)
            if (fraction > 0f) {
                if (fraction >= 1f) drawLit()
                else {
                    // A soft edge where the light is.
                    val edge = x + w * fraction
                    val feather = reading.size.height * 0.55f
                    drawLit(fadeFrom = edge - feather, fadeTo = edge + feather)
                }
            }
        }
    }
}
