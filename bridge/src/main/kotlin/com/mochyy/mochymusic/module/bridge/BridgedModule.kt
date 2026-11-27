// mochy's side of the bridge: wraps any code module or .mochypck provider so every call crosses safely as json

package com.mochyy.mochymusic.module.bridge

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.CanvasModule
import com.mochyy.mochymusic.module.CatalogModule
import com.mochyy.mochymusic.module.CatalogSessionConfig
import com.mochyy.mochymusic.module.CoverModule
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.ExtendedMetadataModule
import com.mochyy.mochymusic.module.ItemListModule
import com.mochyy.mochymusic.module.ItemsAdded
import com.mochyy.mochymusic.module.LicenseServer
import com.mochyy.mochymusic.module.LookModule
import com.mochyy.mochymusic.module.LyricsModule
import com.mochyy.mochymusic.module.MODULE_API_VERSION
import com.mochyy.mochymusic.module.ModuleArt
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.module.ModuleFields
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleItem
import com.mochyy.mochymusic.module.ModuleServer
import com.mochyy.mochymusic.module.MultiLyricsModule
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.ServerListModule
import com.mochyy.mochymusic.module.ServerProblem
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.TrackMetadata
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.module.bridge.shared.BridgeReply
import com.mochyy.mochymusic.module.bridge.shared.BridgeResponse
import com.mochyy.mochymusic.module.bridge.shared.HostBridge
import com.mochyy.mochymusic.module.bridge.shared.ModuleBridge
import com.mochyy.mochymusic.module.bridge.shared.SurfaceBridge
import com.mochyy.mochymusic.module.catalog.AccountInfo
import com.mochyy.mochymusic.module.catalog.AlbumItem
import com.mochyy.mochymusic.module.catalog.AlbumPage
import com.mochyy.mochymusic.module.catalog.ArtistEditorial
import com.mochyy.mochymusic.module.catalog.ArtistItemsContinuationPage
import com.mochyy.mochymusic.module.catalog.ArtistItemsPage
import com.mochyy.mochymusic.module.catalog.ArtistPage
import com.mochyy.mochymusic.module.catalog.BackendAudioStream
import com.mochyy.mochymusic.module.catalog.BackendVideoStreams
import com.mochyy.mochymusic.module.catalog.BrowseEndpoint
import com.mochyy.mochymusic.module.catalog.BrowseResult
import com.mochyy.mochymusic.module.catalog.CanvasArtwork
import com.mochyy.mochymusic.module.catalog.CaptionCue
import com.mochyy.mochymusic.module.catalog.CaptionTrackInfo
import com.mochyy.mochymusic.module.catalog.ChartsPage
import com.mochyy.mochymusic.module.catalog.CoverCandidate
import com.mochyy.mochymusic.module.catalog.ExplorePage
import com.mochyy.mochymusic.module.catalog.HistoryPage
import com.mochyy.mochymusic.module.catalog.HomePage
import com.mochyy.mochymusic.module.catalog.LibraryContinuationPage
import com.mochyy.mochymusic.module.catalog.LibraryPage
import com.mochyy.mochymusic.module.catalog.LyricsProviderDescriptor
import com.mochyy.mochymusic.module.catalog.MediaInfo
import com.mochyy.mochymusic.module.catalog.MoodAndGenres
import com.mochyy.mochymusic.module.catalog.NextResult
import com.mochyy.mochymusic.module.catalog.PlaylistContinuationPage
import com.mochyy.mochymusic.module.catalog.PlaylistPage
import com.mochyy.mochymusic.module.catalog.RelatedPage
import com.mochyy.mochymusic.module.catalog.SearchFilter
import com.mochyy.mochymusic.module.catalog.SearchResult
import com.mochyy.mochymusic.module.catalog.SearchSuggestions
import com.mochyy.mochymusic.module.catalog.SearchSummaryPage
import com.mochyy.mochymusic.module.catalog.SongDetailBundle
import com.mochyy.mochymusic.module.catalog.SongItem
import com.mochyy.mochymusic.module.catalog.StreamTrackingInfo
import com.mochyy.mochymusic.module.catalog.VideoCaptionsInfo
import com.mochyy.mochymusic.module.catalog.WatchEndpoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64

// core features plus byos pack capabilities (catalog, multi-lyrics, cover, canvas, extended metadata) are gated by adds and about flags
open class BridgedModule private constructor(private val bridge: ModuleBridge, about: Wire.About) :
    SourceModule,
    LyricsModule,
    MultiLyricsModule,
    LookModule,
    ExtendedMetadataModule,
    CatalogModule,
    CoverModule,
    CanvasModule,
    ModuleArt,
    ModuleFields {

    override val info = about.info
    override val adds = about.adds
    override val signIn = about.signIn
    override val icon = about.icon
    override val banner = about.banner
    val itemsLabel: String = about.itemsLabel
    val itemsAbout: String = about.itemsAbout

    val supportsCatalog: Boolean = about.isCatalog
    val supportsMultiLyrics: Boolean = about.isMultiLyrics
    val supportsCover: Boolean = about.isCover
    val supportsCanvas: Boolean = about.isCanvas
    val supportsExtendedMetadata: Boolean = about.isExtendedMetadata

    /** live framebuffer surface when the zip's code implements SurfaceModule; null for older or non-surface zips */
    val surface: SurfaceBridge? = (bridge as? SurfaceBridge)?.takeIf { runCatching { it.hasSurface() }.getOrDefault(false) }

    private val callIds = AtomicLong()

    override fun attach(host: ModuleHost) = bridge.attach(HostEnd(host))

    override fun accountState(): AccountState = Wire.accountFrom(ask("accountState"))

    override fun onSignInHeader(name: String, value: String, url: String): Boolean =
        (ask("onSignInHeader", buildJsonObject {
            put("name", name)
            put("value", value)
            put("url", url)
        }) as? JsonPrimitive)?.contentOrNull == "true"

    override suspend fun verifyAccount(): AccountState = Wire.accountFrom(call("verifyAccount"))

    override fun signOut() {
        ask("signOut")
    }

    override val choices: List<ModuleChoice> get() = Wire.choicesFrom(ask("choices"))

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        Wire.resultFrom(call("resolve", songArgs(track, picks)), ::license)

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult =
        Wire.resultFrom(call("diagnose", songArgs(track, picks)) { report(Wire.stepFrom(Wire.objectOf(it))) }, ::license)

    override suspend fun lyrics(track: TrackQuery): String? =
        (call("lyrics", buildJsonObject { put("track", Wire.trackToJson(track)) }) as? JsonPrimitive)?.contentOrNull

    override val lyricsProviders: List<LyricsProviderDescriptor>
        get() = if (!supportsMultiLyrics) emptyList() else runCatching {
            Wire.decodeOrDefault<List<LyricsProviderDescriptor>>(ask("lyricsProviders"), emptyList())
        }.getOrDefault(emptyList())

    override suspend fun lyricsFrom(providerId: String, track: TrackQuery): String? =
        (call("lyricsFrom", buildJsonObject {
            put("providerId", providerId)
            put("track", Wire.trackToJson(track))
        }) as? JsonPrimitive)?.contentOrNull

    override suspend fun metadata(track: TrackQuery): TrackMetadata? =
        Wire.metadataFrom(call("metadata", buildJsonObject { put("track", Wire.trackToJson(track)) }))

    override suspend fun songDetailBundle(track: TrackQuery): SongDetailBundle? =
        if (!supportsExtendedMetadata) null else Wire.decodeOrNull(call("songDetailBundle", buildJsonObject { put("track", Wire.trackToJson(track)) }))

    override suspend fun albumAbout(albumTitle: String, artistName: String?): String? =
        if (!supportsExtendedMetadata) null else (call("albumAbout", buildJsonObject {
            put("albumTitle", albumTitle)
            put("artistName", artistName)
        }) as? JsonPrimitive)?.contentOrNull

    override suspend fun artistGenre(artistName: String): String? =
        if (!supportsExtendedMetadata) null else (call("artistGenre", buildJsonObject { put("artistName", artistName) }) as? JsonPrimitive)?.contentOrNull

    override val look: Map<String, String> get() = Wire.mapFrom(ask("look"))

    override val fields: List<ModuleField> get() = runCatching { Wire.fieldsFrom(ask("fields")) }.getOrDefault(emptyList())

    override fun onFields(values: Map<String, String>) {
        runCatching { ask("onFields", buildJsonObject { put("values", Wire.mapToJson(values)) }) }
    }

    // ---- cover and canvas ----

    override suspend fun searchCovers(track: TrackQuery): List<CoverCandidate> =
        if (!supportsCover) emptyList() else Wire.decodeOrDefault(call("searchCovers", buildJsonObject { put("track", Wire.trackToJson(track)) }), emptyList())

    override suspend fun resolveArtistImage(artistName: String, artistId: String?): String? =
        if (!supportsCover) null else (call("resolveArtistImage", buildJsonObject {
            put("artistName", artistName)
            put("artistId", artistId)
        }) as? JsonPrimitive)?.contentOrNull

    override suspend fun canvasForTrack(
        track: TrackQuery,
        preferredOrder: List<String>,
        allowSpotify: Boolean,
        storefront: String,
    ): CanvasArtwork? = if (!supportsCanvas) null else Wire.decodeOrNull(
        call("canvasForTrack", buildJsonObject {
            put("track", Wire.trackToJson(track))
            put("preferredOrder", Wire.strings(preferredOrder))
            put("allowSpotify", allowSpotify)
            put("storefront", storefront)
        }),
    )

    override suspend fun artistEditorial(artistName: String): ArtistEditorial? =
        if (!supportsCanvas) null else Wire.decodeOrNull(call("artistEditorial", buildJsonObject { put("artistName", artistName) }))

    override suspend fun artistVideoCanvas(track: TrackQuery): CanvasArtwork? =
        if (!supportsCanvas) null else Wire.decodeOrNull(call("artistVideoCanvas", buildJsonObject { put("track", Wire.trackToJson(track)) }))

    override fun warmUpCanvas() {
        if (supportsCanvas) runCatching { ask("warmUpCanvas") }
    }

    override fun forgetCanvasSession() {
        if (supportsCanvas) runCatching { ask("forgetCanvasSession") }
    }

    override fun hasSpotifyCanvasSession(): Boolean =
        supportsCanvas && runCatching { (ask("hasSpotifyCanvasSession") as? JsonPrimitive)?.booleanOrNull == true }.getOrDefault(false)

    override fun trimCanvasMemory() {
        if (supportsCanvas) runCatching { ask("trimCanvasMemory") }
    }

    // ---- catalog backend ----

    override fun configureSession(config: CatalogSessionConfig) {
        if (supportsCatalog) runCatching { ask("configureSession", buildJsonObject { put("config", Wire.encode(config)) }) }
    }

    override fun currentVisitorData(): String? =
        if (!supportsCatalog) null else runCatching { (ask("currentVisitorData") as? JsonPrimitive)?.contentOrNull }.getOrNull()

    override fun hasSessionToken(): Boolean =
        supportsCatalog && runCatching { (ask("hasSessionToken") as? JsonPrimitive)?.booleanOrNull == true }.getOrDefault(false)

    override suspend fun refreshVisitorData(): String? =
        if (!supportsCatalog) null else (call("refreshVisitorData") as? JsonPrimitive)?.contentOrNull

    override fun clearGuestSession() {
        if (supportsCatalog) runCatching { ask("clearGuestSession") }
    }

    override suspend fun prewarm() {
        if (supportsCatalog) runCatching { call("prewarm") }
    }

    override fun trimMemory() {
        if (supportsCatalog) runCatching { ask("trimMemory") }
    }

    override suspend fun releaseWebViews() {
        if (supportsCatalog) runCatching { call("releaseWebViews") }
    }

    override fun forceRefreshStream(itemId: String) {
        if (supportsCatalog) runCatching { ask("forceRefreshStream", buildJsonObject { put("itemId", itemId) }) }
    }

    override suspend fun searchSuggestions(query: String): SearchSuggestions =
        Wire.decodeOrDefault(call("searchSuggestions", buildJsonObject { put("query", query) }), SearchSuggestions())

    override suspend fun searchSummary(query: String): SearchSummaryPage =
        Wire.decodeOrDefault(call("searchSummary", buildJsonObject { put("query", query) }), SearchSummaryPage())

    override suspend fun search(query: String, filter: SearchFilter): SearchResult =
        Wire.decodeOrDefault(call("search", buildJsonObject { put("query", query); put("filter", filter.value) }), SearchResult())

    override suspend fun searchContinuation(continuation: String): SearchResult =
        Wire.decodeOrDefault(call("searchContinuation", buildJsonObject { put("continuation", continuation) }), SearchResult())

    override suspend fun home(continuation: String?, params: String?): HomePage =
        Wire.decodeOrDefault(call("home", buildJsonObject { put("continuation", continuation); put("params", params) }), HomePage())

    override suspend fun explore(): ExplorePage =
        Wire.decodeOrDefault(call("explore"), ExplorePage())

    override suspend fun charts(continuation: String?): ChartsPage =
        Wire.decodeOrDefault(call("charts", buildJsonObject { put("continuation", continuation) }), ChartsPage())

    override suspend fun moodAndGenres(): List<MoodAndGenres> =
        Wire.decodeOrDefault(call("moodAndGenres"), emptyList())

    override suspend fun newReleaseAlbums(): List<AlbumItem> =
        Wire.decodeOrDefault(call("newReleaseAlbums"), emptyList())

    override suspend fun browse(browseId: String, params: String?): BrowseResult =
        Wire.decodeOrDefault(call("browse", buildJsonObject { put("browseId", browseId); put("params", params) }), BrowseResult())

    override suspend fun artist(browseId: String): ArtistPage? =
        Wire.decodeOrNull(call("artist", buildJsonObject { put("browseId", browseId) }))

    override suspend fun artistItems(endpoint: BrowseEndpoint): ArtistItemsPage? =
        Wire.decodeOrNull(call("artistItems", buildJsonObject { put("endpoint", Wire.encode(endpoint)) }))

    override suspend fun artistItemsContinuation(continuation: String): ArtistItemsContinuationPage? =
        Wire.decodeOrNull(call("artistItemsContinuation", buildJsonObject { put("continuation", continuation) }))

    override suspend fun album(browseId: String, withSongs: Boolean): AlbumPage? =
        Wire.decodeOrNull(call("album", buildJsonObject { put("browseId", browseId); put("withSongs", withSongs) }))

    override suspend fun albumSongs(playlistId: String): List<SongItem> =
        Wire.decodeOrDefault(call("albumSongs", buildJsonObject { put("playlistId", playlistId) }), emptyList())

    override suspend fun playlist(playlistId: String): PlaylistPage? =
        Wire.decodeOrNull(call("playlist", buildJsonObject { put("playlistId", playlistId) }))

    override suspend fun playlistContinuation(continuation: String): PlaylistContinuationPage? =
        Wire.decodeOrNull(call("playlistContinuation", buildJsonObject { put("continuation", continuation) }))

    override suspend fun mediaInfo(videoId: String): MediaInfo? =
        Wire.decodeOrNull(call("mediaInfo", buildJsonObject { put("videoId", videoId) }))

    override suspend fun channelId(browseId: String): String? =
        (call("channelId", buildJsonObject { put("browseId", browseId) }) as? JsonPrimitive)?.contentOrNull

    override suspend fun next(endpoint: WatchEndpoint, continuation: String?): NextResult? =
        Wire.decodeOrNull(call("next", buildJsonObject { put("endpoint", Wire.encode(endpoint)); put("continuation", continuation) }))

    override suspend fun related(endpoint: BrowseEndpoint): RelatedPage? =
        Wire.decodeOrNull(call("related", buildJsonObject { put("endpoint", Wire.encode(endpoint)) }))

    override suspend fun queue(videoIds: List<String>?, playlistId: String?): List<SongItem> =
        Wire.decodeOrDefault(call("queue", buildJsonObject {
            videoIds?.let { put("videoIds", Wire.strings(it)) }
            put("playlistId", playlistId)
        }), emptyList())

    override suspend fun resolveAudioStream(
        videoId: String,
        playlistId: String?,
        quality: String,
        networkMetered: Boolean,
        preferVideo: Boolean,
        preferStudio: Boolean,
        preferMp4: Boolean,
    ): BackendAudioStream? = Wire.decodeOrNull(
        call("resolveAudioStream", buildJsonObject {
            put("videoId", videoId)
            put("playlistId", playlistId)
            put("quality", quality)
            put("networkMetered", networkMetered)
            put("preferVideo", preferVideo)
            put("preferStudio", preferStudio)
            put("preferMp4", preferMp4)
        }),
    )

    override suspend fun resolveVideoStreams(videoId: String): BackendVideoStreams? =
        Wire.decodeOrNull(call("resolveVideoStreams", buildJsonObject { put("videoId", videoId) }))

    override suspend fun captions(videoId: String): String? =
        (call("captions", buildJsonObject { put("videoId", videoId) }) as? JsonPrimitive)?.contentOrNull

    override suspend fun videoCaptions(videoId: String): VideoCaptionsInfo? =
        Wire.decodeOrNull(call("videoCaptions", buildJsonObject { put("videoId", videoId) }))

    override suspend fun captionCues(captions: VideoCaptionsInfo, track: CaptionTrackInfo): List<CaptionCue> =
        Wire.decodeOrDefault(call("captionCues", buildJsonObject {
            put("captions", Wire.encode(captions))
            put("track", Wire.encode(track))
        }), emptyList())

    override suspend fun transcript(videoId: String): String? =
        (call("transcript", buildJsonObject { put("videoId", videoId) }) as? JsonPrimitive)?.contentOrNull

    override suspend fun accountInfo(): AccountInfo? =
        Wire.decodeOrNull(call("accountInfo"))

    override suspend fun musicHistory(hlOverride: String?, maxRequests: Int): HistoryPage? =
        Wire.decodeOrNull(call("musicHistory", buildJsonObject {
            put("hlOverride", hlOverride)
            put("maxRequests", maxRequests)
        }))

    override suspend fun library(browseId: String, tabIndex: Int): LibraryPage? =
        Wire.decodeOrNull(call("library", buildJsonObject { put("browseId", browseId); put("tabIndex", tabIndex) }))

    override suspend fun libraryContinuation(continuation: String): LibraryContinuationPage? =
        Wire.decodeOrNull(call("libraryContinuation", buildJsonObject { put("continuation", continuation) }))

    override suspend fun likeVideo(videoId: String, like: Boolean): Boolean =
        (call("likeVideo", buildJsonObject { put("videoId", videoId); put("like", like) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun likePlaylist(playlistId: String, like: Boolean): Boolean =
        (call("likePlaylist", buildJsonObject { put("playlistId", playlistId); put("like", like) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun subscribeChannel(channelId: String, subscribe: Boolean): Boolean =
        (call("subscribeChannel", buildJsonObject { put("channelId", channelId); put("subscribe", subscribe) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun toggleSongLibrary(songId: String, addToLibrary: Boolean): Boolean =
        (call("toggleSongLibrary", buildJsonObject { put("songId", songId); put("addToLibrary", addToLibrary) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun feedback(tokens: List<String>): Boolean =
        (call("feedback", buildJsonObject { put("tokens", Wire.strings(tokens)) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun createPlaylist(title: String): String? =
        (call("createPlaylist", buildJsonObject { put("title", title) }) as? JsonPrimitive)?.contentOrNull

    override suspend fun renamePlaylist(playlistId: String, name: String): Boolean =
        (call("renamePlaylist", buildJsonObject { put("playlistId", playlistId); put("name", name) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun deletePlaylist(playlistId: String): Boolean =
        (call("deletePlaylist", buildJsonObject { put("playlistId", playlistId) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun addToPlaylist(playlistId: String, videoId: String): Boolean =
        (call("addToPlaylist", buildJsonObject { put("playlistId", playlistId); put("videoId", videoId) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun addVideosToPlaylist(playlistId: String, videoIds: List<String>): Boolean =
        (call("addVideosToPlaylist", buildJsonObject { put("playlistId", playlistId); put("videoIds", Wire.strings(videoIds)) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun addVideosToPlaylistWithSetVideoIds(playlistId: String, videoIds: List<String>): List<Pair<String, String>> =
        Wire.decodeOrDefault<List<List<String>>>(
            call("addVideosToPlaylistWithSetVideoIds", buildJsonObject { put("playlistId", playlistId); put("videoIds", Wire.strings(videoIds)) }),
            emptyList(),
        ).mapNotNull { pair -> if (pair.size >= 2) pair[0] to pair[1] else null }

    override suspend fun addPlaylistToPlaylist(playlistId: String, addPlaylistId: String): Boolean =
        (call("addPlaylistToPlaylist", buildJsonObject { put("playlistId", playlistId); put("addPlaylistId", addPlaylistId) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String): Boolean =
        (call("removeFromPlaylist", buildJsonObject { put("playlistId", playlistId); put("videoId", videoId); put("setVideoId", setVideoId) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun moveSongPlaylist(playlistId: String, setVideoId: String, successorSetVideoId: String?): Boolean =
        (call("moveSongPlaylist", buildJsonObject { put("playlistId", playlistId); put("setVideoId", setVideoId); put("successorSetVideoId", successorSetVideoId) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun registerPlayback(tracking: StreamTrackingInfo, cpn: String): Boolean =
        (call("registerPlayback", buildJsonObject { put("tracking", Wire.encode(tracking)); put("cpn", cpn) }) as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun reportWatchtime(tracking: StreamTrackingInfo, cpn: String, playedSeconds: Double, totalSeconds: Double): Boolean =
        (call("reportWatchtime", buildJsonObject {
            put("tracking", Wire.encode(tracking))
            put("cpn", cpn)
            put("playedSeconds", playedSeconds)
            put("totalSeconds", totalSeconds)
        }) as? JsonPrimitive)?.booleanOrNull == true

    // ---- servers and items ----

    val servers: List<ModuleServer> get() = Wire.serversFrom(ask("servers"))

    suspend fun addServer(link: String): ServerProblem? = Wire.serverProblemFrom(call("addServer", buildJsonObject { put("link", link) }))

    fun removeServer(url: String) {
        ask("removeServer", buildJsonObject { put("url", url) })
    }

    val items: List<ModuleItem> get() = Wire.itemsFrom(ask("items"))

    suspend fun addZip(fileName: String, bytes: ByteArray): ItemsAdded =
        Wire.addedFrom(call("addZip", buildJsonObject { put("fileName", fileName) }, data = bytes))

    suspend fun addLink(link: String): ItemsAdded = Wire.addedFrom(call("addLink", buildJsonObject { put("link", link) }))

    fun removeItem(id: String) {
        ask("removeItem", buildJsonObject { put("id", id) })
    }

    fun close() = bridge.close()

    private fun ask(method: String, args: JsonObject = Wire.Empty): JsonElement = Wire.parse(bridge.ask(method, args.toString()))

    private suspend fun call(method: String, args: JsonObject = Wire.Empty, data: ByteArray? = null, onStep: (String) -> Unit = {}): JsonElement {
        val id = callIds.incrementAndGet()
        val answer = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { bridge.cancel(id) }
            bridge.start(id, method, args.toString(), data, object : BridgeReply {
                override fun step(json: String) = onStep(json)
                override fun done(json: String) = continuation.resume(json)
                override fun failed(message: String) = continuation.resumeWithException(IOException(message))
            })
        }
        return Wire.parse(answer)
    }

    private fun songArgs(track: TrackQuery, picks: Map<String, String>) = buildJsonObject {
        put("track", Wire.trackToJson(track))
        put("picks", Wire.mapToJson(picks))
    }

    private fun license(id: String) = LicenseServer { challenge ->
        val answer = ask("license", buildJsonObject {
            put("id", id)
            put("challenge", Base64.encode(challenge))
        })
        Base64.decode((answer as? JsonPrimitive)?.contentOrNull ?: throw IOException("The module gave no license"))
    }

    private class HostEnd(private val host: ModuleHost) : HostBridge {
        override fun get(key: String): String? = host.storage.get(key)

        override fun set(key: String, value: String?) = host.storage.set(key, value)

        override fun http(head: String, body: ByteArray?): BridgeResponse {
            val response = host.http(Wire.requestFrom(Wire.objectOf(head), body))
            return BridgeResponse(response.code, Wire.mapToJson(response.headers).toString(), response.body)
        }

        override fun log(message: String) = host.log(message)

        override fun folder(): String = host.folder.path

        override fun platformContext(): Any? = host.platformContext
    }

    private class WithServers(bridge: ModuleBridge, about: Wire.About) : BridgedModule(bridge, about), ServerListModule

    private class WithItems(bridge: ModuleBridge, about: Wire.About) : BridgedModule(bridge, about), ItemListModule

    private class WithBoth(bridge: ModuleBridge, about: Wire.About) : BridgedModule(bridge, about), ServerListModule, ItemListModule

    companion object {
        const val ModuleSideClass = "com.mochyy.mochymusic.module.bridge.ModuleSide"

        /** builds a bridged module for entry on the far side of bridge; accepts api v3 (legacy zips) and v4 (byos packs) */
        fun open(bridge: ModuleBridge, entry: String): BridgedModule {
            val about = Wire.aboutFrom(Wire.objectOf(bridge.open(entry)))
            val version = about.info.apiVersion
            if (version != MODULE_API_VERSION && version != 3) {
                throw IOException("It's made for module API $version, this Mochy has $MODULE_API_VERSION")
            }
            if (about.info.id.isBlank()) throw IOException("It doesn't say what it is")
            return when {
                about.servers && about.items -> WithBoth(bridge, about)
                about.servers -> WithServers(bridge, about)
                about.items -> WithItems(bridge, about)
                else -> BridgedModule(bridge, about)
            }
        }
    }
}

/** parent of a module or .mochypck class loader: only java/android boot classes and the bridge shared package cross */
class BridgeOnlyParent(private val mochy: ClassLoader) : ClassLoader(String::class.java.classLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> =
        if (name.startsWith(SharedPackage)) mochy.loadClass(name) else super.loadClass(name, resolve)

    private companion object {
        val SharedPackage = ModuleBridge::class.java.name.substringBeforeLast('.') + "."
    }
}
