// source-agnostic catalog, playback, artwork, canvas, and metadata models shared across mochy core and .mochypck packs

package com.mochyy.mochymusic.module.catalog

import kotlinx.serialization.Serializable

/** preferred ip family for network calls configured in mochy settings */
@Serializable
enum class IpVersion {
    AUTO,
    IPV4,
    IPV6,
}

/** locale hints passed to catalog providers (country code and language code) */
@Serializable
data class CatalogLocale(
    val gl: String,
    val hl: String,
)

@Serializable
data class Thumbnail(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class Thumbnails(
    val thumbnails: List<Thumbnail>,
)

@Serializable
data class TextRun(
    val text: String,
    val url: String? = null,
)

@Serializable
data class AccountInfo(
    val name: String,
    val email: String? = null,
    val channelHandle: String? = null,
    val thumbnailUrl: String? = null,
    val isPremium: Boolean? = null,
)

@Serializable
data class MediaInfo(
    val videoId: String,
    val title: String? = null,
    val author: String? = null,
    val authorId: String? = null,
    val authorThumbnail: String? = null,
    val description: String? = null,
    val uploadDate: String? = null,
    val subscribers: String? = null,
    val viewCount: Int? = null,
    val like: Int? = null,
    val dislike: Int? = null,
)

@Serializable
data class WatchEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null,
    val playlistSetVideoId: String? = null,
    val params: String? = null,
    val index: Int? = null,
    val musicVideoType: String? = null,
) {
    companion object {
        const val MUSIC_VIDEO_TYPE_OMV = "MUSIC_VIDEO_TYPE_OMV"
        const val MUSIC_VIDEO_TYPE_UGC = "MUSIC_VIDEO_TYPE_UGC"
        const val MUSIC_VIDEO_TYPE_ATV = "MUSIC_VIDEO_TYPE_ATV"
    }
}

@Serializable
data class BrowseEndpoint(
    val browseId: String,
    val params: String? = null,
    val pageType: String? = null,
) {
    val isArtistEndpoint: Boolean get() = pageType == MUSIC_PAGE_TYPE_ARTIST
    val isAlbumEndpoint: Boolean get() = pageType == MUSIC_PAGE_TYPE_ALBUM || pageType == MUSIC_PAGE_TYPE_AUDIOBOOK
    val isPlaylistEndpoint: Boolean get() = pageType == MUSIC_PAGE_TYPE_PLAYLIST

    companion object {
        const val MUSIC_PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
        const val MUSIC_PAGE_TYPE_AUDIOBOOK = "MUSIC_PAGE_TYPE_AUDIOBOOK"
        const val MUSIC_PAGE_TYPE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
        const val MUSIC_PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
        const val MUSIC_PAGE_TYPE_LIBRARY_ARTIST = "MUSIC_PAGE_TYPE_LIBRARY_ARTIST"
        const val MUSIC_PAGE_TYPE_USER_CHANNEL = "MUSIC_PAGE_TYPE_USER_CHANNEL"
        const val MUSIC_PAGE_TYPE_TRACK_LYRICS = "MUSIC_PAGE_TYPE_TRACK_LYRICS"
        const val MUSIC_PAGE_TYPE_TRACK_RELATED = "MUSIC_PAGE_TYPE_TRACK_RELATED"
    }
}

@Serializable
data class CatalogArtistRef(
    val name: String,
    val id: String? = null,
)

@Serializable
data class CatalogAlbumRef(
    val name: String,
    val id: String,
)

/** one item returned by any catalog provider (song, album, playlist, or artist) */
@Serializable
sealed class CatalogItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnail: String?
    abstract val explicit: Boolean
    abstract val shareLink: String
}

@Serializable
data class SongItem(
    override val id: String,
    override val title: String,
    val artists: List<CatalogArtistRef>,
    val album: CatalogAlbumRef? = null,
    val duration: Int? = null,
    val musicVideoType: String? = null,
    val chartPosition: Int? = null,
    val chartChange: String? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val endpoint: WatchEndpoint? = null,
    val setVideoId: String? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val historyRemoveToken: String? = null,
    val externalShareUrl: String? = null,
) : CatalogItem() {
    val isVideoSong: Boolean
        get() = musicVideoType != null && musicVideoType != WatchEndpoint.MUSIC_VIDEO_TYPE_ATV

    override val shareLink: String
        get() = externalShareUrl.orEmpty()
}

@Serializable
data class AlbumItem(
    val browseId: String,
    val playlistId: String,
    override val id: String = browseId,
    override val title: String,
    val artists: List<CatalogArtistRef>? = null,
    val year: Int? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val description: String? = null,
    val externalShareUrl: String? = null,
) : CatalogItem() {
    override val shareLink: String
        get() = externalShareUrl.orEmpty()
}

@Serializable
data class PlaylistItem(
    override val id: String,
    override val title: String,
    val author: CatalogArtistRef? = null,
    val songCountText: String? = null,
    override val thumbnail: String? = null,
    val playEndpoint: WatchEndpoint? = null,
    val shuffleEndpoint: WatchEndpoint? = null,
    val radioEndpoint: WatchEndpoint? = null,
    val isEditable: Boolean = false,
    val externalShareUrl: String? = null,
) : CatalogItem() {
    override val explicit: Boolean get() = false
    override val shareLink: String get() = externalShareUrl.orEmpty()
}

@Serializable
data class ArtistItem(
    override val id: String,
    override val title: String,
    override val thumbnail: String? = null,
    val channelId: String? = null,
    val playEndpoint: WatchEndpoint? = null,
    val shuffleEndpoint: WatchEndpoint? = null,
    val radioEndpoint: WatchEndpoint? = null,
    val externalShareUrl: String? = null,
) : CatalogItem() {
    override val explicit: Boolean get() = false
    override val shareLink: String get() = externalShareUrl.orEmpty()
}

@Serializable
data class SearchFilter(val value: String) {
    companion object {
        val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
        val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
        val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
        val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
        val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
        val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
    }
}

@Serializable
data class SearchSuggestions(
    val queries: List<String> = emptyList(),
    val recommendedItems: List<CatalogItem> = emptyList(),
)

@Serializable
data class SearchResult(
    val items: List<CatalogItem> = emptyList(),
    val continuation: String? = null,
)

@Serializable
data class SearchSummary(
    val title: String,
    val items: List<CatalogItem>,
)

@Serializable
data class SearchSummaryPage(
    val summaries: List<SearchSummary> = emptyList(),
) {
    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) copy(summaries = summaries.mapNotNull { s -> s.copy(items = s.items.filterExplicit()).takeIf { it.items.isNotEmpty() } }) else this

    fun filterVideoSongs(disableVideos: Boolean = false) =
        if (disableVideos) copy(summaries = summaries.mapNotNull { s -> s.copy(items = s.items.filterVideoSongs(true)).takeIf { it.items.isNotEmpty() } }) else this

    fun filterYoutubeShorts(enabled: Boolean = false) =
        if (enabled) copy(summaries = summaries.mapNotNull { s -> s.copy(items = s.items.filterYoutubeShorts(true)).takeIf { it.items.isNotEmpty() } }) else this
}

@Serializable
data class MoodAndGenres(
    val title: String,
    val items: List<Item>,
) {
    @Serializable
    data class Item(
        val title: String,
        val stripeColor: Long,
        val endpoint: BrowseEndpoint,
    )
}

@Serializable
data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem> = emptyList(),
    val moodAndGenres: List<MoodAndGenres.Item> = emptyList(),
)

@Serializable
data class HomePage(
    val chips: List<Chip>? = null,
    val sections: List<Section> = emptyList(),
    val continuation: String? = null,
) {
    @Serializable
    data class Chip(
        val title: String,
        val endpoint: BrowseEndpoint? = null,
        val deselectEndPoint: BrowseEndpoint? = null,
    )

    @Serializable
    data class Section(
        val title: String,
        val label: String? = null,
        val thumbnail: String? = null,
        val endpoint: BrowseEndpoint? = null,
        val items: List<CatalogItem>,
        val isSpeedDial: Boolean = false,
    )

    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) copy(sections = sections.mapNotNull { s -> s.copy(items = s.items.filterExplicit()).takeIf { it.items.isNotEmpty() } }) else this

    fun filterVideoSongs(disableVideos: Boolean = false) =
        if (disableVideos) copy(sections = sections.mapNotNull { s -> s.copy(items = s.items.filterVideoSongs(true)).takeIf { it.items.isNotEmpty() } }) else this

    fun filterYoutubeShorts(enabled: Boolean = false) =
        if (enabled) copy(sections = sections.mapNotNull { s -> s.copy(items = s.items.filterYoutubeShorts(true)).takeIf { it.items.isNotEmpty() } }) else this

    fun filterAiGenerated(enabled: Boolean = false) =
        if (enabled) copy(sections = sections.mapNotNull { s -> s.copy(items = s.items.filterAiGenerated(true)).takeIf { it.items.isNotEmpty() } }) else this
}

@Serializable
data class BrowseResult(
    val title: String? = null,
    val items: List<Item> = emptyList(),
) {
    @Serializable
    data class Item(
        val title: String? = null,
        val items: List<CatalogItem>,
    )

    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) copy(items = items.mapNotNull { i -> i.copy(items = i.items.filterExplicit()).takeIf { it.items.isNotEmpty() } }) else this

    fun filterVideoSongs(disableVideos: Boolean = false) =
        if (disableVideos) copy(items = items.mapNotNull { i -> i.copy(items = i.items.filterVideoSongs(true)).takeIf { it.items.isNotEmpty() } }) else this

    fun filterYoutubeShorts(enabled: Boolean = false) =
        if (enabled) copy(items = items.mapNotNull { i -> i.copy(items = i.items.filterYoutubeShorts(true)).takeIf { it.items.isNotEmpty() } }) else this
}

@Serializable
data class ChartsPage(
    val sections: List<ChartSection> = emptyList(),
    val continuation: String? = null,
) {
    @Serializable
    data class ChartSection(
        val title: String,
        val items: List<CatalogItem>,
        val chartType: ChartType,
    )

    @Serializable
    enum class ChartType { TRENDING, TOP, GENRE, NEW_RELEASES }
}

@Serializable
data class ArtistSection(
    val title: String,
    val items: List<CatalogItem>,
    val moreEndpoint: BrowseEndpoint? = null,
)

@Serializable
data class ArtistPage(
    val artist: ArtistItem,
    val sections: List<ArtistSection> = emptyList(),
    val description: String? = null,
    val subscriberCountText: String? = null,
    val monthlyListenerCount: String? = null,
    val descriptionRuns: List<TextRun>? = null,
    val subscribed: Boolean? = null,
) {
    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) copy(sections = sections.mapNotNull { s -> s.copy(items = s.items.filterExplicit()).takeIf { it.items.isNotEmpty() } }) else this
}

@Serializable
data class ArtistItemsPage(
    val title: String,
    val items: List<CatalogItem>,
    val continuation: String? = null,
)

@Serializable
data class ArtistItemsContinuationPage(
    val items: List<CatalogItem>,
    val continuation: String? = null,
)

@Serializable
data class AlbumPage(
    val album: AlbumItem,
    val songs: List<SongItem> = emptyList(),
    val otherVersions: List<AlbumItem> = emptyList(),
    val releasesForYou: List<AlbumItem> = emptyList(),
    val description: String? = null,
    val descriptionRuns: List<TextRun>? = null,
)

@Serializable
data class PlaylistPage(
    val playlist: PlaylistItem,
    val songs: List<SongItem> = emptyList(),
    val songsContinuation: String? = null,
    val continuation: String? = null,
    val related: List<CatalogItem>? = null,
)

@Serializable
data class PlaylistContinuationPage(
    val songs: List<SongItem> = emptyList(),
    val continuation: String? = null,
)

@Serializable
data class NextResult(
    val title: String? = null,
    val items: List<SongItem> = emptyList(),
    val currentIndex: Int? = null,
    val lyricsEndpoint: BrowseEndpoint? = null,
    val relatedEndpoint: BrowseEndpoint? = null,
    val continuation: String? = null,
    val endpoint: WatchEndpoint = WatchEndpoint(),
)

@Serializable
data class RelatedPage(
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
)

@Serializable
data class HistoryPage(
    val sections: List<HistorySection>? = null,
) {
    @Serializable
    data class HistorySection(
        val title: String,
        val songs: List<SongItem>,
    )
}

@Serializable
data class LibraryPage(
    val items: List<CatalogItem> = emptyList(),
    val continuation: String? = null,
)

@Serializable
data class LibraryContinuationPage(
    val items: List<CatalogItem> = emptyList(),
    val continuation: String? = null,
)

@Serializable
data class CaptionCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

@Serializable
data class CaptionTrackInfo(
    val baseUrl: String,
    val languageCode: String? = null,
    val name: String? = null,
    val vssId: String? = null,
    val isAutoGenerated: Boolean = false,
)

@Serializable
data class VideoCaptionsInfo(
    val videoId: String,
    val tracks: List<CaptionTrackInfo> = emptyList(),
    val clientHint: String = "",
)

/** playback tracking pings handed back with a resolved stream for optional watch-history reporting */
@Serializable
data class StreamTrackingInfo(
    val playbackUrl: String? = null,
    val watchtimeUrl: String? = null,
    val atrUrl: String? = null,
)

/** resolved audio stream returned by a catalog/stream backend pack */
@Serializable
data class BackendAudioStream(
    val streamUrl: String,
    val itag: Int = 0,
    val mimeType: String = "audio/mp4",
    val bitrate: Int = 0,
    val sampleRate: Int? = null,
    val contentLength: Long? = null,
    val loudnessDb: Double? = null,
    val perceptualLoudnessDb: Double? = null,
    val durationSec: Int? = null,
    val expiresInSeconds: Int = 21600,
    val clientName: String = "unknown",
    val headers: Map<String, String> = emptyMap(),
    val isPremium: Boolean = false,
    val fromMusicVideo: Boolean = false,
    val fromStudio: Boolean = false,
    val tracking: StreamTrackingInfo? = null,
    val title: String? = null,
    val author: String? = null,
    val thumbnailUrl: String? = null,
)

/** one video stream option (progressive or adaptive dash) returned by a catalog backend pack */
@Serializable
data class BackendVideoFormat(
    val url: String,
    val itag: Int,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val fps: Int? = null,
    val bitrate: Int = 0,
    val contentLength: Long? = null,
    val initRangeStart: Long? = null,
    val initRangeEnd: Long? = null,
    val indexRangeStart: Long? = null,
    val indexRangeEnd: Long? = null,
    val durationMs: Long? = null,
    val isAudioOnly: Boolean = false,
)

@Serializable
data class BackendVideoStreams(
    val videoId: String,
    val formats: List<BackendVideoFormat> = emptyList(),
    val audioFormat: BackendVideoFormat? = null,
    val headers: Map<String, String> = emptyMap(),
    val clientName: String = "unknown",
    val expiresInSeconds: Int = 21600,
)

/** one cover image returned by a cover provider */
@Serializable
data class CoverCandidate(
    val source: String,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val score: Float = 0f,
)

/** animated or static canvas media returned by a canvas provider */
@Serializable
data class CanvasArtwork(
    val name: String? = null,
    val artist: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val static: String? = null,
    val animated: String? = null,
    val videoUrl: String? = null,
) {
    val preferredAnimationUrl: String? get() = animated ?: videoUrl
}

@Serializable
data class ArtistLatestRelease(
    val name: String,
    val releaseDate: String? = null,
    val isSingle: Boolean = false,
    val trackCount: Int? = null,
    val coverUrl: String? = null,
)

/** artist editorial header media (animated portrait, banner, wordmark logo, bio, latest release) from a visual provider */
@Serializable
data class ArtistEditorial(
    val artistName: String? = null,
    val motionSquareUrl: String? = null,
    val motionVideoUrl: String? = null,
    val previewFrameUrl: String? = null,
    val bannerUrl: String? = null,
    val logoUrl: String? = null,
    val logoAspect: Float? = null,
    val bio: String? = null,
    val genres: List<String> = emptyList(),
    val latestRelease: ArtistLatestRelease? = null,
)

/** descriptor of one lyrics provider bundled inside a multi-lyrics .mochypck */
@Serializable
data class LyricsProviderDescriptor(
    val id: String,
    val displayName: String,
    val defaultOrder: Int = 100,
    val isLastResort: Boolean = false,
    val projectName: String? = null,
    val projectUrl: String? = null,
    val websiteUrl: String? = null,
)

/** structured song details from one named metadata source (musixmatch, genius, wikipedia, etc.) */
@Serializable
data class SongSourceDetail(
    val sourceId: String,
    val sourceName: String,
    val album: String? = null,
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val producers: List<String> = emptyList(),
    val label: String? = null,
    val about: String? = null,
    val sourceUrl: String? = null,
    val spotifyId: String? = null,
    val appleMusicId: String? = null,
    val artistImageUrl: String? = null,
    val artistPhotos: Map<String, String> = emptyMap(),
)

/** combined song detail payload returned by an extended metadata module */
@Serializable
data class SongDetailBundle(
    val sources: List<SongSourceDetail> = emptyList(),
    val description: String? = null,
)

// ---- pure offline item filters and parsers ----

private const val SHORT_MAX_SECONDS = 90
private val SHORTS_HASHTAG = Regex("(?i)#shorts?\\b")
private val AI_GENERATED_PATTERN = Regex(
    "(?i)\\b(ai[\\s-]*(covers?|generated|music|remix|version|voice|vocals?)|suno\\s*ai|udio|(made|created|generated|produced)\\s*(with|by|using)\\s*ai)\\b|[(\\[]\\s*ai\\s*[)\\]]",
)

val SongItem.isLikelyShort: Boolean
    get() = isVideoSong && (
        SHORTS_HASHTAG.containsMatchIn(title) ||
            (musicVideoType == WatchEndpoint.MUSIC_VIDEO_TYPE_UGC && duration != null && duration <= SHORT_MAX_SECONDS)
        )

fun <T : CatalogItem> List<T>.filterExplicit(enabled: Boolean = true): List<T> =
    if (enabled) filter { !it.explicit } else this

fun <T : CatalogItem> List<T>.filterVideoSongs(disableVideos: Boolean = false): List<T> =
    if (disableVideos) filterNot { it is SongItem && it.isVideoSong } else this

fun <T : CatalogItem> List<T>.filterYoutubeShorts(enabled: Boolean = false): List<T> =
    if (enabled) filterNot { (it is PlaylistItem && it.id.startsWith("SS")) || (it is SongItem && it.isLikelyShort) } else this

fun isAiGeneratedText(text: String?): Boolean = text != null && AI_GENERATED_PATTERN.containsMatchIn(text)

fun CatalogItem.looksAiGenerated(): Boolean = isAiGeneratedText(title) || when (this) {
    is SongItem -> artists.any { isAiGeneratedText(it.name) } || isAiGeneratedText(album?.name)
    is AlbumItem -> artists.orEmpty().any { isAiGeneratedText(it.name) }
    is PlaylistItem -> isAiGeneratedText(author?.name)
    else -> false
}

fun <T : CatalogItem> List<T>.filterAiGenerated(enabled: Boolean = false): List<T> =
    if (enabled) filterNot { it.looksAiGenerated() } else this

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }
        .toMap()

/** generic link parser that recognizes external media urls passed into search or share intents */
object MediaUrlParser {
    sealed class ParsedUrl {
        abstract val id: String
        data class Track(override val id: String) : ParsedUrl()
        data class Artist(override val id: String) : ParsedUrl()
        data class Playlist(override val id: String) : ParsedUrl()
        data class Album(override val id: String) : ParsedUrl()
    }

    private val trackPatterns = listOf(
        Regex("""(?:https?://)?(?:www\.)?(?:music\.)?youtube\.com/watch\?.*v=([a-zA-Z0-9_-]{11})"""),
        Regex("""(?:https?://)?youtu\.be/([a-zA-Z0-9_-]{11})"""),
        Regex("""(?:https?://)?(?:www\.)?youtube\.com/shorts/([a-zA-Z0-9_-]{11})"""),
    )
    private val artistPatterns = listOf(
        Regex("""(?:https?://)?(?:www\.)?music\.youtube\.com/channel/([a-zA-Z0-9_-]+)"""),
    )
    private val albumPatterns = listOf(
        Regex("""(?:https?://)?(?:www\.)?music\.youtube\.com/browse/(MPRE[a-zA-Z0-9_-]+)"""),
    )
    private val playlistPatterns = listOf(
        Regex("""(?:https?://)?(?:www\.)?(?:music\.)?youtube\.com/playlist\?.*list=([a-zA-Z0-9_-]+)"""),
    )

    fun isRecognizedUrl(text: String): Boolean = parse(text) != null

    fun parse(url: String): ParsedUrl? {
        val trimmed = url.trim()
        for (p in trackPatterns) p.find(trimmed)?.groupValues?.getOrNull(1)?.let { return ParsedUrl.Track(it) }
        for (p in playlistPatterns) p.find(trimmed)?.groupValues?.getOrNull(1)?.let { return ParsedUrl.Playlist(it) }
        for (p in albumPatterns) p.find(trimmed)?.groupValues?.getOrNull(1)?.let { return ParsedUrl.Album(it) }
        for (p in artistPatterns) p.find(trimmed)?.groupValues?.getOrNull(1)?.let { return ParsedUrl.Artist(it) }
        return null
    }

    fun extractTrackId(url: String): String? = (parse(url) as? ParsedUrl.Track)?.id
}
