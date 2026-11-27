// byos provider contracts implemented by .mochypck packs for catalog, streaming, multi-lyrics, covers, canvas, and extended metadata

package com.mochyy.mochymusic.module

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
import com.mochyy.mochymusic.module.catalog.CatalogLocale
import com.mochyy.mochymusic.module.catalog.ChartsPage
import com.mochyy.mochymusic.module.catalog.CoverCandidate
import com.mochyy.mochymusic.module.catalog.ExplorePage
import com.mochyy.mochymusic.module.catalog.HistoryPage
import com.mochyy.mochymusic.module.catalog.HomePage
import com.mochyy.mochymusic.module.catalog.IpVersion
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
import kotlinx.serialization.Serializable

/** runtime session settings synced from mochy core into any active catalog backend */
@Serializable
data class CatalogSessionConfig(
    val locale: CatalogLocale = CatalogLocale("US", "en"),
    val cookie: String? = null,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val useLoginForBrowse: Boolean = false,
    val offline: Boolean = false,
    val ipVersion: IpVersion = IpVersion.AUTO,
    val streamEngine: String = "innertubex",
    val accountIsPremium: Boolean? = null,
)

/** full catalog, search, browse, radio, audio/video stream, and account sync backend provided by a .mochypck */
interface CatalogModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Catalog, ModuleFeature.AudioSource)

    fun configureSession(config: CatalogSessionConfig) {}
    fun currentVisitorData(): String? = null
    fun hasSessionToken(): Boolean = false
    suspend fun refreshVisitorData(): String? = null
    fun clearGuestSession() {}
    suspend fun prewarm() {}
    fun trimMemory() {}
    suspend fun releaseWebViews() {}
    fun forceRefreshStream(itemId: String) {}

    // search and suggestions
    suspend fun searchSuggestions(query: String): SearchSuggestions = SearchSuggestions()
    suspend fun searchSummary(query: String): SearchSummaryPage = SearchSummaryPage()
    suspend fun search(query: String, filter: SearchFilter): SearchResult = SearchResult()
    suspend fun searchContinuation(continuation: String): SearchResult = SearchResult()

    // discovery and browsing
    suspend fun home(continuation: String? = null, params: String? = null): HomePage = HomePage()
    suspend fun explore(): ExplorePage = ExplorePage()
    suspend fun charts(continuation: String? = null): ChartsPage = ChartsPage()
    suspend fun moodAndGenres(): List<MoodAndGenres> = emptyList()
    suspend fun newReleaseAlbums(): List<AlbumItem> = emptyList()
    suspend fun browse(browseId: String, params: String? = null): BrowseResult = BrowseResult()

    // artists, albums, playlists, and media info
    suspend fun artist(browseId: String): ArtistPage? = null
    suspend fun artistItems(endpoint: BrowseEndpoint): ArtistItemsPage? = null
    suspend fun artistItemsContinuation(continuation: String): ArtistItemsContinuationPage? = null
    suspend fun album(browseId: String, withSongs: Boolean = true): AlbumPage? = null
    suspend fun albumSongs(playlistId: String): List<SongItem> = emptyList()
    suspend fun playlist(playlistId: String): PlaylistPage? = null
    suspend fun playlistContinuation(continuation: String): PlaylistContinuationPage? = null
    suspend fun mediaInfo(videoId: String): MediaInfo? = null
    suspend fun channelId(browseId: String): String? = null

    // playback queues and related tracks
    suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): NextResult? = null
    suspend fun related(endpoint: BrowseEndpoint): RelatedPage? = null
    suspend fun queue(videoIds: List<String>? = null, playlistId: String? = null): List<SongItem> = emptyList()

    // audio and video stream resolution (supports both innertubex and legacy innertube engines)
    suspend fun resolveAudioStream(
        videoId: String,
        playlistId: String? = null,
        quality: String = "AUTO",
        networkMetered: Boolean = false,
        preferVideo: Boolean = false,
        preferStudio: Boolean = false,
        preferMp4: Boolean = false,
    ): BackendAudioStream? = null

    suspend fun resolveVideoStreams(videoId: String): BackendVideoStreams? = null

    // captions and transcripts
    suspend fun captions(videoId: String): String? = null
    suspend fun videoCaptions(videoId: String): VideoCaptionsInfo? = null
    suspend fun captionCues(captions: VideoCaptionsInfo, track: CaptionTrackInfo): List<CaptionCue> = emptyList()
    suspend fun transcript(videoId: String): String? = null

    // account, library, and playlist sync
    suspend fun accountInfo(): AccountInfo? = null
    suspend fun musicHistory(hlOverride: String? = null, maxRequests: Int = 1): HistoryPage? = null
    suspend fun library(browseId: String, tabIndex: Int = 0): LibraryPage? = null
    suspend fun libraryContinuation(continuation: String): LibraryContinuationPage? = null
    suspend fun likeVideo(videoId: String, like: Boolean): Boolean = false
    suspend fun likePlaylist(playlistId: String, like: Boolean): Boolean = false
    suspend fun subscribeChannel(channelId: String, subscribe: Boolean): Boolean = false
    suspend fun toggleSongLibrary(songId: String, addToLibrary: Boolean): Boolean = false
    suspend fun feedback(tokens: List<String>): Boolean = false
    suspend fun createPlaylist(title: String): String? = null
    suspend fun renamePlaylist(playlistId: String, name: String): Boolean = false
    suspend fun deletePlaylist(playlistId: String): Boolean = false
    suspend fun addToPlaylist(playlistId: String, videoId: String): Boolean = false
    suspend fun addVideosToPlaylist(playlistId: String, videoIds: List<String>): Boolean = false
    suspend fun addVideosToPlaylistWithSetVideoIds(playlistId: String, videoIds: List<String>): List<Pair<String, String>> = emptyList()
    suspend fun addPlaylistToPlaylist(playlistId: String, addPlaylistId: String): Boolean = false
    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String): Boolean = false
    suspend fun moveSongPlaylist(playlistId: String, setVideoId: String, successorSetVideoId: String?): Boolean = false
    suspend fun registerPlayback(tracking: StreamTrackingInfo, cpn: String): Boolean = false
    suspend fun reportWatchtime(tracking: StreamTrackingInfo, cpn: String, playedSeconds: Double, totalSeconds: Double): Boolean = false
}

/** a module or .mochypck bundling multiple named lyrics providers (betterlyrics, unison, lrclib, etc.) */
interface MultiLyricsModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Lyrics)

    val lyricsProviders: List<LyricsProviderDescriptor>

    suspend fun lyricsFrom(providerId: String, track: TrackQuery): String?
}

/** a module or .mochypck supplying album/track covers and artist profile images */
interface CoverModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Cover)

    suspend fun searchCovers(track: TrackQuery): List<CoverCandidate> = emptyList()

    suspend fun resolveArtistImage(artistName: String, artistId: String? = null): String? = null
}

/** a module or .mochypck supplying animated track canvases, artist backgrounds, and artist videos */
interface CanvasModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Canvas)

    suspend fun canvasForTrack(
        track: TrackQuery,
        preferredOrder: List<String> = emptyList(),
        allowSpotify: Boolean = false,
        storefront: String = "us",
    ): CanvasArtwork? = null

    suspend fun artistEditorial(artistName: String): ArtistEditorial? = null

    suspend fun artistVideoCanvas(track: TrackQuery): CanvasArtwork? = null

    fun warmUpCanvas() {}
    fun forgetCanvasSession() {}
    fun hasSpotifyCanvasSession(): Boolean = false
    fun trimCanvasMemory() {}
}

/** extended metadata provider for multi-source song facts, album editorial notes, and artist genres */
interface ExtendedMetadataModule : MetadataModule {
    suspend fun songDetailBundle(track: TrackQuery): SongDetailBundle? = null
    suspend fun albumAbout(albumTitle: String, artistName: String?): String? = null
    suspend fun artistGenre(artistName: String): String? = null
}
