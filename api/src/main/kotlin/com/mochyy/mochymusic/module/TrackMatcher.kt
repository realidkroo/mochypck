// multi-signal recording matcher shared across all modules and packs: diacritic folding, fuzzy edit/bigram title matching, multi-artist splits, album boost, and gaussian duration scoring

package com.mochyy.mochymusic.module

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** a song in a service's catalog, as its search returns it */
class CatalogTrack(
    val id: String,
    val title: String,
    /** every credited artist, as one line ("benny blanco, Halsey & Khalid") */
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
)

/** finds the catalog recording that matches a track query across diacritics, featuring tags, punctuation, and version edits */
object TrackMatcher {

    // words that mark a genuinely different recording rather than catalog decoration
    private val versionWords = listOf(
        "remix", "live", "acoustic", "instrumental", "karaoke", "sped up", "speed up", "slowed",
        "nightcore", "demo", "cover", "edit", "mix", "version", "unplugged", "orchestral",
        "piano", "acapella", "a cappella", "mashup", "reverb", "8d", "lofi", "lo-fi", "stripped",
        "commentary", "interview", "intro", "outro", "skit", "extended", "radio edit",
    )
    private val versionPatterns = versionWords.map { Regex("""\b${Regex.escape(it)}\b""") }

    // subtle release-edition words carry a much smaller penalty than a full remix/live mismatch
    private val editionWords = listOf("clean", "explicit", "censored", "single", "deluxe")
    private val editionPatterns = editionWords.map { Regex("""\b${Regex.escape(it)}\b""") }

    private val combiningMarks = Regex("""\p{InCombiningDiacriticalMarks}+""")
    private val artistSplitRegex = Regex("""(?i)\s*(?:,|&|\+|/|×|\bx\b|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b|\band\b)\s*""")
    private val featExtractRegex = Regex("""(?i)[(\[](?:feat|ft|featuring|with)\.?\s+([^)\]]+)[)\]]|\b(?:feat|ft|featuring)\.?\s+(.+)$""")
    private val stopWords = setOf("the", "and", "a", "an")

    class Scored(val track: CatalogTrack, val score: Float)

    fun best(query: TrackQuery, candidates: List<CatalogTrack>): Scored? = ranked(query, candidates).firstOrNull()

    /** every acceptable match, best first, so callers can fall through near-equal editions if one lacks a playable stream */
    fun ranked(query: TrackQuery, candidates: List<CatalogTrack>): List<Scored> {
        val isrc = query.isrc?.trim()?.takeIf { it.isNotEmpty() }
        val scored = candidates.map { song ->
            val matchedScore = if (isrc != null && song.isrc?.trim().equals(isrc, ignoreCase = true)) {
                1f
            } else {
                score(query, song)
            }
            Scored(song, matchedScore)
        }
        return scored.filter { it.score >= 0.68f }.sortedByDescending { it.score }
    }

    fun score(query: TrackQuery, song: CatalogTrack): Float {
        val qTitle = core(query.title)
        val sTitle = core(song.title)
        val title = titleSimilarity(qTitle, sTitle)
        if (title < 0.52f) return 0f

        val queryArtists = extractArtists(query.artists, query.title)
        val songArtists = extractArtists(listOf(song.artist), song.title)
        val artist = artistSimilarity(queryArtists, songArtists)
        if (artist < 0.45f) return 0f

        val duration = durationScore(query.durationMs, song.durationMs)
        val albumBonus = albumBonus(query.album, song.album)

        val qRaw = foldText(query.title)
        val sRaw = foldText(song.title)
        val versionMismatch = versionPatterns.count { it.containsMatchIn(qRaw) != it.containsMatchIn(sRaw) }
        val editionMismatch = editionPatterns.count { it.containsMatchIn(qRaw) != it.containsMatchIn(sRaw) }
        val penalty = 0.24f * versionMismatch + 0.04f * editionMismatch

        val base = 0.48f * title + 0.30f * artist + 0.18f * duration + 0.04f * albumBonus
        return (base - penalty).coerceIn(0f, 1f)
    }

    /** normalizes accents, smart quotes, fullwidth forms, and catalog decorations down to the song's real title */
    fun core(title: String): String {
        var t = foldText(title)
        t = t.replace(Regex("""[(\[](?:feat|ft|featuring|with|prod)\.?\s[^)\]]*[)\]]"""), " ")
        t = t.replace(Regex("""\s[-–—]\s.*\b(?:remaster(?:ed)?|official|video|audio|lyrics?|visuali[sz]er|mv|m/v|hd|4k|hq|topic)\b.*$"""), " ")
        t = t.replace(Regex("""[(\[][^)\]]*\b(?:remaster(?:ed)?|official|video|audio|lyrics?|visuali[sz]er|mv|m/v|explicit|clean|hd|4k|hq|bonus\s+track|deluxe(?:\s+edition)?)\b[^)\]]*[)\]]"""), " ")
        t = t.replace(Regex("""\b(?:feat|ft|featuring)\.?\s.*$"""), " ")
        return words(t).joinToString(" ")
    }

    /** strips accents, converts fullwidth chars, and folds case so international artist and title names match reliably */
    fun foldText(text: String): String {
        val nfkd = Normalizer.normalize(text, Normalizer.Form.NFKD)
        val noMarks = combiningMarks.replace(nfkd, "")
        return noMarks
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u00A0', ' ')
            .lowercase()
            .trim()
    }

    /** multi-signal title similarity combining token overlap, bigram dice, and edit distance */
    fun titleSimilarity(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        val tokenF1 = tokenF1(a, b)
        val dice = bigramDice(a, b)
        val edit = levenshteinSimilarity(a, b)
        // exact containment when one title has a short subtitle still scores well without beating exact matches
        val containment = if (a.length >= 4 && b.length >= 4 && (a.contains(b) || b.contains(a))) {
            min(a.length, b.length).toFloat() / max(a.length, b.length).toFloat() * 0.94f
        } else {
            0f
        }
        return max(max(0.55f * tokenF1 + 0.25f * dice + 0.20f * edit, containment), 0.7f * dice + 0.3f * edit)
    }

    /** scores artist agreement with extra weight on the lead artist and partial credit for featured credits */
    fun artistSimilarity(queryArtists: List<String>, candidateArtists: List<String>): Float {
        if (queryArtists.isEmpty() || candidateArtists.isEmpty()) return 0f
        val leadQ = queryArtists.first()
        val bestLead = candidateArtists.maxOf { singleArtistSimilarity(leadQ, it) }
        if (queryArtists.size == 1) return bestLead
        val others = queryArtists.drop(1).map { qa -> candidateArtists.maxOf { singleArtistSimilarity(qa, it) } }
        val avgOthers = others.average().toFloat()
        return (0.72f * bestLead + 0.28f * avgOthers).coerceIn(0f, 1f)
    }

    private fun singleArtistSimilarity(a: String, b: String): Float {
        if (a == b) return 1f
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return 0f
        val setA = wa.toSet()
        val setB = wb.toSet()
        val overlap = (setA intersect setB).size
        val recallA = overlap.toFloat() / setA.size
        val recallB = overlap.toFloat() / setB.size
        val f1 = if (recallA + recallB == 0f) 0f else 2f * recallA * recallB / (recallA + recallB)
        val edit = levenshteinSimilarity(wa.joinToString(" "), wb.joinToString(" "))
        return max(f1, edit * 0.92f)
    }

    private fun extractArtists(rawArtists: List<String>, rawTitle: String): List<String> {
        val out = LinkedHashSet<String>()
        for (a in rawArtists) {
            val cleaned = foldText(a).replace(Regex("""\s*-\s*topic$"""), "")
            for (part in cleaned.split(artistSplitRegex)) {
                val norm = words(part).joinToString(" ")
                if (norm.isNotEmpty()) out += norm
            }
        }
        val foldedTitle = foldText(rawTitle)
        featExtractRegex.findAll(foldedTitle).forEach { m ->
            val featured = m.groupValues[1].ifBlank { m.groupValues[2] }
            for (part in featured.split(artistSplitRegex)) {
                val norm = words(part).joinToString(" ")
                if (norm.isNotEmpty()) out += norm
            }
        }
        return out.toList()
    }

    // gaussian-like decay keeps a tight tolerance around 0-3s while smoothly rejecting 20s+ mismatches
    private fun durationScore(wantMs: Long?, haveMs: Long?): Float {
        if (wantMs == null || haveMs == null || wantMs <= 0L || haveMs <= 0L) return 0.65f
        val diffSec = abs(wantMs - haveMs) / 1000.0
        if (diffSec <= 2.5) return 1f
        if (diffSec >= 25.0) return 0f
        val shifted = diffSec - 2.5
        return exp(-(shifted * shifted) / (2.0 * 7.5 * 7.5)).toFloat()
    }

    private fun albumBonus(wantAlbum: String?, haveAlbum: String?): Float {
        if (wantAlbum.isNullOrBlank() || haveAlbum.isNullOrBlank()) return 0.5f
        val a = core(wantAlbum)
        val b = core(haveAlbum)
        if (a.isEmpty() || b.isEmpty()) return 0.5f
        return titleSimilarity(a, b)
    }

    private fun words(text: String): List<String> =
        text.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() && it !in stopWords }

    private fun tokenF1(a: String, b: String): Float {
        val wa = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val wb = b.split(' ').filter { it.isNotEmpty() }.toSet()
        if (wa.isEmpty() || wb.isEmpty()) return 0f
        val shared = (wa intersect wb).size.toFloat()
        return (2f * shared) / (wa.size + wb.size)
    }

    private fun bigramDice(a: String, b: String): Float {
        val ca = a.replace(" ", "")
        val cb = b.replace(" ", "")
        if (ca.length < 2 || cb.length < 2) return if (ca == cb) 1f else 0f
        val ba = HashMap<String, Int>()
        for (i in 0 until ca.length - 1) {
            val g = ca.substring(i, i + 2)
            ba[g] = (ba[g] ?: 0) + 1
        }
        var shared = 0
        var totalB = 0
        for (i in 0 until cb.length - 1) {
            val g = cb.substring(i, i + 2)
            totalB++
            val left = ba[g] ?: 0
            if (left > 0) {
                shared++
                ba[g] = left - 1
            }
        }
        val totalA = ca.length - 1
        return (2f * shared) / (totalA + totalB)
    }

    private fun levenshteinSimilarity(a: String, b: String): Float {
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1f
        if (maxLen > 120) return 0f
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            val ca = a[i - 1]
            for (j in 1..b.length) {
                val cost = if (ca == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return (1f - prev[b.length].toFloat() / maxLen).coerceIn(0f, 1f)
    }
}
