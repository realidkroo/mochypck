// what mochy and its modules agree on, kept free of android so a module app or .mochypck loads at runtime; change a shape and bump the version (docs/notes/modules.md)

package com.mochyy.mochymusic.module

import java.io.File

const val MODULE_API_VERSION = 4

data class ModuleInfo(
    /** stable, lowercase: "applemusic" */
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val apiVersion: Int = MODULE_API_VERSION,
    /** where the module's own code lives, for the player's "star on github"; null when it isn't public */
    val sourceUrl: String? = null,
    /** who made it, shown on its page */
    val author: String? = null,
)

/** what a module or .mochypck brings to mochy, one module can bring several */
enum class ModuleFeature {
    AudioSource,
    Lyrics,
    Look,
    Metadata,
    AudioProcessor,
    Catalog,
    Cover,
    Canvas,
}

/** a module's own pictures: a square icon for its row and a wide banner over its page, each a link or a file path; null keeps mochy's own */
interface ModuleArt : MochyModule {
    val icon: String? get() = null
    val banner: String? get() = null
}

/** how a module's setting is shown: a line to type in, a list to pick from, or a switch */
enum class FieldKind { Text, Select, Toggle }

/** a setting a module asks people for, listed under options on its page; a toggle's [default] is "true" or "false" */
data class ModuleField(
    val id: String,
    val label: String,
    val kind: FieldKind,
    val options: List<StreamOption> = emptyList(),
    val default: String = "",
    val hint: String = "",
    val description: String = "",
)

/** a module with settings of its own: mochy shows them, keeps what people pick, and hands every value over */
interface ModuleFields : MochyModule {
    val fields: List<ModuleField>

    /** every field's value, once before the first call and again each time one changes */
    fun onFields(values: Map<String, String>) {}
}

/** what mochy gives a module */
interface ModuleHost {
    /** small key-value storage private to this module: tokens, choices */
    val storage: ModuleStorage

    /** http through mochy's own client, so its proxy, dns and ip-version settings apply, blocking */
    fun http(request: HttpRequest): HttpResponse

    fun log(message: String)

    /** a folder only this module uses, for things too big for storage: downloaded scripts, packs */
    val folder: File

    /** optional android application context handed to user-trusted .mochypck code modules (for webview/potoken/cookies); null for untrusted/jvm hosts */
    val platformContext: Any? get() = null
}

interface ModuleStorage {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
)

// headers come with lowercase names, read them through header()
class HttpResponse(val code: Int, val body: ByteArray, val headers: Map<String, String> = emptyMap()) {
    val ok: Boolean get() = code in 200..299
    val text: String get() = body.decodeToString()

    fun header(name: String): String? = headers[name.lowercase()]
}

/** the track mochy wants to play, as mochy knows it */
data class TrackQuery @JvmOverloads constructor(
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
    /** optional external item id when the track came from a catalog source; null for local files */
    val videoId: String? = null,
)

/** how a module's account is signed into through mochy's browser */
data class SignInSpec(
    val startUrl: String,
    val origins: List<String>,
    val sessionOrigins: Set<String>,
    val desktopSite: Boolean,
    val title: String,
    val subtitle: String,
)

sealed interface AccountState {
    data object SignedOut : AccountState
    data class SignedIn(val label: String) : AccountState
    data class Expired(val reason: String) : AccountState
}

/** one of a choice's options, best or most usual first */
data class StreamOption(val id: String, val label: String, val description: String)

/** a setting the module offers as a row of chips; the first option is the default */
data class ModuleChoice(val id: String, val label: String, val options: List<StreamOption>)

enum class StreamFormat { Progressive, Hls, Dash }

data class MatchedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    /** 0..1, how sure the module is it's the same recording */
    val confidence: Float,
)

/** something the player can open */
data class SourceStream(
    val url: String,
    val format: StreamFormat,
    val headers: Map<String, String> = emptyMap(),
    val drm: StreamDrm? = null,
    val sourceLabel: String,
    val qualityLabel: String,
    val matched: MatchedTrack,
    /** the audio's codec as listeners know it ("AAC", "HE-AAC", "FLAC"), for the player's format badge */
    val codec: String? = null,
    /** the audio's sample rate when known, so undecodable hi-res streams can be skipped */
    val sampleRateHz: Int? = null,
)

sealed interface StreamDrm {
    /** widevine cdm license callback called on the player's drm thread */
    class Widevine(val license: LicenseServer, val initData: List<ByteArray>) : StreamDrm
}

fun interface LicenseServer {
    fun acquire(challenge: ByteArray): ByteArray
}

sealed interface ResolveResult {
    data class Found(val stream: SourceStream) : ResolveResult
    data class NotFound(val reason: String) : ResolveResult
    data class Unavailable(val reason: String) : ResolveResult
}

/** one line of a module's self-test; ok is null while the step runs */
data class DiagnosticStep(val name: String, val ok: Boolean?, val detail: String)

/** every module: its name, what it adds, and its account when it needs one */
interface MochyModule {
    val info: ModuleInfo

    /** mochy only asks a module for the things listed here */
    val adds: Set<ModuleFeature>

    /** called once, before anything else */
    fun attach(host: ModuleHost)

    /** null for a module that needs no account */
    val signIn: SignInSpec? get() = null

    fun accountState(): AccountState = AccountState.SignedIn(NO_ACCOUNT_LABEL)

    /** a header the sign-in page sent; true once there's everything needed to sign in */
    fun onSignInHeader(name: String, value: String, url: String): Boolean = false

    /** asks the service whether the saved account still works */
    suspend fun verifyAccount(): AccountState = accountState()

    fun signOut() {}

    val choices: List<ModuleChoice> get() = emptyList()
}

/** what a module that needs no account says about it */
const val NO_ACCOUNT_LABEL = "No account needed"

/** a place songs play from */
interface SourceModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.AudioSource)

    /** picks maps choice id to option id for choices the user changed */
    suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult

    /** step by step self-test for the test button; the host adds the cdm part */
    suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult
}

/** a server a module plays from, as its page lists it; built-in ones can't be removed */
data class ModuleServer(val url: String, val name: String, val builtIn: Boolean)

/** why a link didn't become one of a module's servers */
enum class ServerProblem { NotALink, Unreachable, NotAServer, AlreadyAdded }

/** a module that plays from several servers people can add */
interface ServerListModule : MochyModule {
    val servers: List<ModuleServer>

    suspend fun addServer(link: String): ServerProblem?

    fun removeServer(url: String)
}

/** one thing people added to a module, as its page lists it */
data class ModuleItem @JvmOverloads constructor(
    val id: String,
    val name: String,
    val detail: String,
    val icon: String? = null,
    val working: Boolean? = null,
)

/** why a zip or a link added nothing */
enum class ItemProblem { NotAZip, NotALink, Unreachable, NothingFound, Broken, TooBig }

/** what adding a zip or a link came to */
sealed interface ItemsAdded {
    data class Added(val names: List<String>) : ItemsAdded
    data class Problem(val problem: ItemProblem) : ItemsAdded
}

/** a module that holds things people bring as zips or links */
interface ItemListModule : MochyModule {
    val itemsLabel: String
    val itemsAbout: String
    val items: List<ModuleItem>

    suspend fun addZip(fileName: String, bytes: ByteArray): ItemsAdded

    suspend fun addLink(link: String): ItemsAdded

    fun removeItem(id: String)
}

/** a lyrics source that joins the lyrics registry while the module is on */
interface LyricsModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Lyrics)

    /** lrc or plain lines; null when it has none */
    suspend fun lyrics(track: TrackQuery): String?
}

/** what a metadata module knows about a song */
data class TrackMetadata(
    val album: String? = null,
    /** "2014-03-17", "2014-03" or "2014" */
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val producers: List<String> = emptyList(),
    val label: String? = null,
    /** a paragraph or two about the song, shown in about song */
    val about: String? = null,
    /** a sharper cover than the one mochy has, a link */
    val coverUrl: String? = null,
    val isrc: String? = null,
)

/** a source of song facts (genre, release, credits, what it's about) */
interface MetadataModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Metadata)

    suspend fun metadata(track: TrackQuery): TrackMetadata?
}

/** a look: values for mochy's look settings put in place while on and given back when off */
interface LookModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Look)

    val look: Map<String, String>
}

/** a module that renders live 32-bit argb frames into a player block */
interface SurfaceModule : MochyModule {
    fun surfaceStart(assetPath: String, dataDir: String, extraPaths: List<String> = emptyList()): Int
    fun surfaceSize(): IntArray
    fun surfaceTick(): Boolean
    fun surfaceCopyFrame(pixels: IntArray, lastSeq: Long): Long
    fun surfaceSetPaused(paused: Boolean)
    fun surfaceKey(keyCode: Int, pressed: Boolean)
    fun surfaceTurn(delta: Int)
    fun surfaceLastError(): String
}

/** the only settings a look may change so a module can restyle mochy without reaching accounts or network */
object LookSetting {
    const val PLAYER_STYLE = "playerStyle"
    const val HOME_STYLE = "homeStyle"
    const val PLAYLIST_STYLE = "playlistStyle"
    const val COVER_ROUNDING = "thumbnailCornerRadius"
    const val LYRICS_MOTION = "lyricsMotionStyle"
    const val BLUR = "blur"
    const val BLUR_QUALITY = "blurQuality"
    const val BLUR_STRENGTH = "blurStrengthPx"
    const val PROGRESSIVE_BLUR = "enableProgressiveBlur"
    const val ELEMENT_BLUR = "elementBlur"
    const val PURE_BLACK = "pureBlack"
    const val ACCENT_COLOR = "accentColor"
    const val DYNAMIC_THEME = "dynamicTheme"
    const val PLAYER_BACKGROUND = "playerBackground"
    const val FLAGSHIP_BACKGROUND = "flagshipBackground"
    const val IPOD_THEME = "ipodTheme"
    const val IPOD_WHEEL = "ipodWheelColor"
    const val BOTTOM_BAR = "bottomBar"
}
