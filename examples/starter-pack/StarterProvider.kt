// minimal example showing how to implement a custom provider for a .mochypck pack

package com.example.starterpack

import com.mochyy.mochymusic.module.CoverModule
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.LyricsModule
import com.mochyy.mochymusic.module.MODULE_API_VERSION
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.module.catalog.CoverCandidate

class StarterProvider : LyricsModule, CoverModule {

    override val info = ModuleInfo(
        id = "example.starter_lyrics",
        name = "Starter Lyrics & Covers",
        version = "1.0.0",
        description = "Minimal custom provider built with mochypck SDK.",
        apiVersion = MODULE_API_VERSION,
        author = "your-name",
    )

    override val adds = setOf(ModuleFeature.Lyrics, ModuleFeature.Cover)

    private var host: ModuleHost? = null

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override suspend fun lyrics(track: TrackQuery): String? {
        val h = host ?: return null
        val artist = track.artists.firstOrNull().orEmpty()
        // replace with your own lyrics api endpoint
        val res = h.http(HttpRequest("https://example.invalid/lyrics?title=${track.title}&artist=$artist"))
        return if (res.code == 200) res.body.takeIf { it.isNotBlank() } else null
    }

    override suspend fun searchCovers(track: TrackQuery): List<CoverCandidate> {
        // return ranked cover candidates (higher resolution first)
        return emptyList()
    }
}
