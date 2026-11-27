package com.mochyy.mochymusic.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {

    @Test
    fun matchesAccentedAndDecoratedTitles() {
        val query = TrackQuery(
            title = "Déjà Vu (Official Music Video)",
            artists = listOf("Beyoncé", "JAY-Z"),
            album = "B'Day",
            durationMs = 240_000L,
        )
        val studio = CatalogTrack(
            id = "studio",
            title = "Deja Vu (feat. Jay-Z)",
            artist = "Beyonce",
            album = "B'Day",
            durationMs = 239_000L,
        )
        val remix = CatalogTrack(
            id = "remix",
            title = "Deja Vu (Club Remix)",
            artist = "Beyonce",
            durationMs = 310_000L,
        )
        val best = TrackMatcher.best(query, listOf(remix, studio))
        assertNotNull(best)
        assertEquals("studio", best!!.track.id)
        assertTrue(best.score > 0.85f)
    }

    @Test
    fun prefersIsrcExactMatch() {
        val query = TrackQuery(title = "Different Title", artists = listOf("Artist"), isrc = "USUM71703861")
        val target = CatalogTrack(id = "exact", title = "Completely Localized Name", artist = "Other", isrc = "usum71703861")
        val best = TrackMatcher.best(query, listOf(target))
        assertNotNull(best)
        assertEquals("exact", best!!.track.id)
        assertEquals(1f, best.score, 0.001f)
    }

    @Test
    fun rejectsLiveAndSlowedForStudioQuery() {
        val query = TrackQuery(title = "Blinding Lights", artists = listOf("The Weeknd"), durationMs = 200_000L)
        val slowed = CatalogTrack(id = "slow", title = "Blinding Lights (Slowed + Reverb)", artist = "The Weeknd", durationMs = 245_000L)
        val score = TrackMatcher.score(query, slowed)
        assertTrue("Expected low score for slowed+reverb, got $score", score < 0.65f)
    }
}
