// the module's side of the bridge, packed into a zip or .mochypck: instantiates the entry class and dispatches every call over json

package com.mochyy.mochymusic.module.bridge

import com.mochyy.mochymusic.module.CanvasModule
import com.mochyy.mochymusic.module.CatalogModule
import com.mochyy.mochymusic.module.CatalogSessionConfig
import com.mochyy.mochymusic.module.CoverModule
import com.mochyy.mochymusic.module.ExtendedMetadataModule
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.ItemListModule
import com.mochyy.mochymusic.module.LicenseServer
import com.mochyy.mochymusic.module.LookModule
import com.mochyy.mochymusic.module.LyricsModule
import com.mochyy.mochymusic.module.MetadataModule
import com.mochyy.mochymusic.module.MochyModule
import com.mochyy.mochymusic.module.ModuleFields
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleStorage
import com.mochyy.mochymusic.module.MultiLyricsModule
import com.mochyy.mochymusic.module.ServerListModule
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SurfaceModule
import com.mochyy.mochymusic.module.bridge.Wire.bool
import com.mochyy.mochymusic.module.bridge.Wire.double
import com.mochyy.mochymusic.module.bridge.Wire.int
import com.mochyy.mochymusic.module.bridge.Wire.obj
import com.mochyy.mochymusic.module.bridge.Wire.strings
import com.mochyy.mochymusic.module.bridge.Wire.text
import com.mochyy.mochymusic.module.bridge.shared.BridgeReply
import com.mochyy.mochymusic.module.bridge.shared.HostBridge
import com.mochyy.mochymusic.module.bridge.shared.ModuleBridge
import com.mochyy.mochymusic.module.bridge.shared.SurfaceBridge
import com.mochyy.mochymusic.module.catalog.BrowseEndpoint
import com.mochyy.mochymusic.module.catalog.CaptionTrackInfo
import com.mochyy.mochymusic.module.catalog.SearchFilter
import com.mochyy.mochymusic.module.catalog.StreamTrackingInfo
import com.mochyy.mochymusic.module.catalog.VideoCaptionsInfo
import com.mochyy.mochymusic.module.catalog.WatchEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.encoding.Base64

class ModuleSide : ModuleBridge, SurfaceBridge {

    private lateinit var module: MochyModule

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val calls = ConcurrentHashMap<Long, Job>()

    private val licenses = object : LinkedHashMap<String, LicenseServer>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LicenseServer>) = size > MaxLicenses
    }
    private val licenseIds = AtomicLong()

    override fun open(entry: String): String {
        module = Class.forName(entry, true, javaClass.classLoader).getDeclaredConstructor().newInstance() as MochyModule
        return Wire.aboutToJson(module).toString()
    }

    override fun attach(host: HostBridge) = module.attach(HostOverBridge(host))

    override fun ask(method: String, args: String): String = try {
        answer(method, Wire.objectOf(args)).toString()
    } catch (e: Throwable) {
        throw IllegalStateException(e.message ?: e.javaClass.simpleName)
    }

    private fun answer(method: String, args: JsonObject): JsonElement = when (method) {
        "accountState" -> Wire.accountToJson(module.accountState())
        "onSignInHeader" -> JsonPrimitive(module.onSignInHeader(args.text("name").orEmpty(), args.text("value").orEmpty(), args.text("url").orEmpty()))
        "signOut" -> JsonNull.also { module.signOut() }
        "choices" -> Wire.choicesToJson(module.choices)
        "look" -> Wire.mapToJson((module as? LookModule)?.look.orEmpty())
        "fields" -> Wire.fieldsToJson((module as? ModuleFields)?.fields.orEmpty())
        "onFields" -> JsonNull.also { (module as? ModuleFields)?.onFields(Wire.mapFrom(args["values"])) }
        "servers" -> Wire.serversToJson((module as? ServerListModule)?.servers.orEmpty())
        "removeServer" -> JsonNull.also { (module as? ServerListModule)?.removeServer(args.text("url").orEmpty()) }
        "items" -> Wire.itemsToJson((module as? ItemListModule)?.items.orEmpty())
        "removeItem" -> JsonNull.also { (module as? ItemListModule)?.removeItem(args.text("id").orEmpty()) }
        "license" -> JsonPrimitive(Base64.encode(licenseFor(args.text("id")).acquire(Base64.decode(args.text("challenge").orEmpty()))))
        // byos sync calls
        "lyricsProviders" -> Wire.encode((module as? MultiLyricsModule)?.lyricsProviders.orEmpty())
        "configureSession" -> JsonNull.also {
            Wire.decodeOrNull<CatalogSessionConfig>(args["config"])?.let { (module as? CatalogModule)?.configureSession(it) }
        }
        "currentVisitorData" -> JsonPrimitive((module as? CatalogModule)?.currentVisitorData())
        "hasSessionToken" -> JsonPrimitive((module as? CatalogModule)?.hasSessionToken() == true)
        "clearGuestSession" -> JsonNull.also { (module as? CatalogModule)?.clearGuestSession() }
        "trimMemory" -> JsonNull.also { (module as? CatalogModule)?.trimMemory() }
        "forceRefreshStream" -> JsonNull.also { (module as? CatalogModule)?.forceRefreshStream(args.text("itemId").orEmpty()) }
        "warmUpCanvas" -> JsonNull.also { (module as? CanvasModule)?.warmUpCanvas() }
        "forgetCanvasSession" -> JsonNull.also { (module as? CanvasModule)?.forgetCanvasSession() }
        "hasSpotifyCanvasSession" -> JsonPrimitive((module as? CanvasModule)?.hasSpotifyCanvasSession() == true)
        "trimCanvasMemory" -> JsonNull.also { (module as? CanvasModule)?.trimCanvasMemory() }
        else -> throw IllegalArgumentException("$method isn't something a module answers")
    }

    override fun start(id: Long, method: String, args: String, data: ByteArray?, reply: BridgeReply) {
        val job = work.launch(start = CoroutineStart.LAZY) {
            val answer = try {
                run(method, Wire.objectOf(args), data, reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                reply.failed(e.message ?: e.javaClass.simpleName)
                return@launch
            }
            reply.done(answer.toString())
        }
        calls[id] = job
        job.invokeOnCompletion { calls.remove(id) }
        job.start()
    }

    private suspend fun run(method: String, args: JsonObject, data: ByteArray?, reply: BridgeReply): JsonElement = when (method) {
        "verifyAccount" -> Wire.accountToJson(module.verifyAccount())
        "resolve" -> Wire.resultToJson(source().resolve(Wire.trackFrom(args.obj("track")), Wire.mapFrom(args["picks"])), ::licenseId)
        "diagnose" -> {
            val result = source().diagnose(Wire.trackFrom(args.obj("track")), Wire.mapFrom(args["picks"])) { step ->
                reply.step(Wire.stepToJson(step).toString())
            }
            Wire.resultToJson(result, ::licenseId)
        }
        "lyrics" -> JsonPrimitive((module as? LyricsModule)?.lyrics(Wire.trackFrom(args.obj("track"))))
        "lyricsFrom" -> JsonPrimitive((module as? MultiLyricsModule)?.lyricsFrom(args.text("providerId").orEmpty(), Wire.trackFrom(args.obj("track"))))
        "metadata" -> Wire.metadataToJson((module as? MetadataModule)?.metadata(Wire.trackFrom(args.obj("track"))))
        "songDetailBundle" -> (module as? ExtendedMetadataModule)?.songDetailBundle(Wire.trackFrom(args.obj("track")))?.let { Wire.encode(it) } ?: JsonNull
        "albumAbout" -> JsonPrimitive((module as? ExtendedMetadataModule)?.albumAbout(args.text("albumTitle").orEmpty(), args.text("artistName")))
        "artistGenre" -> JsonPrimitive((module as? ExtendedMetadataModule)?.artistGenre(args.text("artistName").orEmpty()))
        "addServer" -> JsonPrimitive((module as ServerListModule).addServer(args.text("link").orEmpty())?.name)
        "addZip" -> Wire.addedToJson((module as ItemListModule).addZip(args.text("fileName").orEmpty(), data ?: ByteArray(0)))
        "addLink" -> Wire.addedToJson((module as ItemListModule).addLink(args.text("link").orEmpty()))
        // cover and canvas
        "searchCovers" -> Wire.encode((module as? CoverModule)?.searchCovers(Wire.trackFrom(args.obj("track"))).orEmpty())
        "resolveArtistImage" -> JsonPrimitive((module as? CoverModule)?.resolveArtistImage(args.text("artistName").orEmpty(), args.text("artistId")))
        "canvasForTrack" -> (module as? CanvasModule)?.canvasForTrack(
            track = Wire.trackFrom(args.obj("track")),
            preferredOrder = args.strings("preferredOrder"),
            allowSpotify = args.bool("allowSpotify"),
            storefront = args.text("storefront") ?: "us",
        )?.let { Wire.encode(it) } ?: JsonNull
        "artistEditorial" -> (module as? CanvasModule)?.artistEditorial(args.text("artistName").orEmpty())?.let { Wire.encode(it) } ?: JsonNull
        "artistVideoCanvas" -> (module as? CanvasModule)?.artistVideoCanvas(Wire.trackFrom(args.obj("track")))?.let { Wire.encode(it) } ?: JsonNull
        // catalog backend
        "refreshVisitorData" -> JsonPrimitive(catalog().refreshVisitorData())
        "prewarm" -> JsonNull.also { catalog().prewarm() }
        "releaseWebViews" -> JsonNull.also { catalog().releaseWebViews() }
        "searchSuggestions" -> Wire.encode(catalog().searchSuggestions(args.text("query").orEmpty()))
        "searchSummary" -> Wire.encode(catalog().searchSummary(args.text("query").orEmpty()))
        "search" -> Wire.encode(catalog().search(args.text("query").orEmpty(), SearchFilter(args.text("filter").orEmpty())))
        "searchContinuation" -> Wire.encode(catalog().searchContinuation(args.text("continuation").orEmpty()))
        "home" -> Wire.encode(catalog().home(args.text("continuation"), args.text("params")))
        "explore" -> Wire.encode(catalog().explore())
        "charts" -> Wire.encode(catalog().charts(args.text("continuation")))
        "moodAndGenres" -> Wire.encode(catalog().moodAndGenres())
        "newReleaseAlbums" -> Wire.encode(catalog().newReleaseAlbums())
        "browse" -> Wire.encode(catalog().browse(args.text("browseId").orEmpty(), args.text("params")))
        "artist" -> catalog().artist(args.text("browseId").orEmpty())?.let { Wire.encode(it) } ?: JsonNull
        "artistItems" -> Wire.decodeOrNull<BrowseEndpoint>(args["endpoint"])?.let { catalog().artistItems(it) }?.let { Wire.encode(it) } ?: JsonNull
        "artistItemsContinuation" -> catalog().artistItemsContinuation(args.text("continuation").orEmpty())?.let { Wire.encode(it) } ?: JsonNull
        "album" -> catalog().album(args.text("browseId").orEmpty(), args.bool("withSongs"))?.let { Wire.encode(it) } ?: JsonNull
        "albumSongs" -> Wire.encode(catalog().albumSongs(args.text("playlistId").orEmpty()))
        "playlist" -> catalog().playlist(args.text("playlistId").orEmpty())?.let { Wire.encode(it) } ?: JsonNull
        "playlistContinuation" -> catalog().playlistContinuation(args.text("continuation").orEmpty())?.let { Wire.encode(it) } ?: JsonNull
        "mediaInfo" -> catalog().mediaInfo(args.text("videoId").orEmpty())?.let { Wire.encode(it) } ?: JsonNull
        "channelId" -> JsonPrimitive(catalog().channelId(args.text("browseId").orEmpty()))
        "next" -> Wire.decodeOrNull<WatchEndpoint>(args["endpoint"])?.let { catalog().next(it, args.text("continuation")) }?.let { Wire.encode(it) } ?: JsonNull
        "related" -> Wire.decodeOrNull<BrowseEndpoint>(args["endpoint"])?.let { catalog().related(it) }?.let { Wire.encode(it) } ?: JsonNull
        "queue" -> Wire.encode(catalog().queue(if ("videoIds" in args) args.strings("videoIds") else null, args.text("playlistId")))
        "resolveAudioStream" -> catalog().resolveAudioStream(
            videoId = args.text("videoId").orEmpty(),
            playlistId = args.text("playlistId"),
            quality = args.text("quality") ?: "AUTO",
            networkMetered = args.bool("networkMetered"),
            preferVideo = args.bool("preferVideo"),
            preferStudio = args.bool("preferStudio"),
            preferMp4 = args.bool("preferMp4"),
        )?.let { Wire.encode(it) } ?: JsonNull
        "resolveVideoStreams" -> catalog().resolveVideoStreams(args.text("videoId").orEmpty())?.let { Wire.encode(it) } ?: JsonNull
        "captions" -> JsonPrimitive(catalog().captions(args.text("videoId").orEmpty()))
        "videoCaptions" -> catalog().videoCaptions(args.text("videoId").orEmpty())?.let { Wire.encode(it) } ?: JsonNull
        "captionCues" -> {
            val caps = Wire.decodeOrNull<VideoCaptionsInfo>(args["captions"])
            val track = Wire.decodeOrNull<CaptionTrackInfo>(args["track"])
            Wire.encode(if (caps != null && track != null) catalog().captionCues(caps, track) else emptyList())
        }
        "transcript" -> JsonPrimitive(catalog().transcript(args.text("videoId").orEmpty()))
        "accountInfo" -> catalog().accountInfo()?.let { Wire.encode(it) } ?: JsonNull
        "musicHistory" -> catalog().musicHistory(args.text("hlOverride"), args.int("maxRequests") ?: 1)?.let { Wire.encode(it) } ?: JsonNull
        "library" -> catalog().library(args.text("browseId").orEmpty(), args.int("tabIndex") ?: 0)?.let { Wire.encode(it) } ?: JsonNull
        "libraryContinuation" -> catalog().libraryContinuation(args.text("continuation").orEmpty())?.let { Wire.encode(it) } ?: JsonNull
        "likeVideo" -> JsonPrimitive(catalog().likeVideo(args.text("videoId").orEmpty(), args.bool("like")))
        "likePlaylist" -> JsonPrimitive(catalog().likePlaylist(args.text("playlistId").orEmpty(), args.bool("like")))
        "subscribeChannel" -> JsonPrimitive(catalog().subscribeChannel(args.text("channelId").orEmpty(), args.bool("subscribe")))
        "toggleSongLibrary" -> JsonPrimitive(catalog().toggleSongLibrary(args.text("songId").orEmpty(), args.bool("addToLibrary")))
        "feedback" -> JsonPrimitive(catalog().feedback(args.strings("tokens")))
        "createPlaylist" -> JsonPrimitive(catalog().createPlaylist(args.text("title").orEmpty()))
        "renamePlaylist" -> JsonPrimitive(catalog().renamePlaylist(args.text("playlistId").orEmpty(), args.text("name").orEmpty()))
        "deletePlaylist" -> JsonPrimitive(catalog().deletePlaylist(args.text("playlistId").orEmpty()))
        "addToPlaylist" -> JsonPrimitive(catalog().addToPlaylist(args.text("playlistId").orEmpty(), args.text("videoId").orEmpty()))
        "addVideosToPlaylist" -> JsonPrimitive(catalog().addVideosToPlaylist(args.text("playlistId").orEmpty(), args.strings("videoIds")))
        "addVideosToPlaylistWithSetVideoIds" -> Wire.encode(catalog().addVideosToPlaylistWithSetVideoIds(args.text("playlistId").orEmpty(), args.strings("videoIds")).map { listOf(it.first, it.second) })
        "addPlaylistToPlaylist" -> JsonPrimitive(catalog().addPlaylistToPlaylist(args.text("playlistId").orEmpty(), args.text("addPlaylistId").orEmpty()))
        "removeFromPlaylist" -> JsonPrimitive(catalog().removeFromPlaylist(args.text("playlistId").orEmpty(), args.text("videoId").orEmpty(), args.text("setVideoId").orEmpty()))
        "moveSongPlaylist" -> JsonPrimitive(catalog().moveSongPlaylist(args.text("playlistId").orEmpty(), args.text("setVideoId").orEmpty(), args.text("successorSetVideoId")))
        "registerPlayback" -> {
            val tracking = Wire.decodeOrNull<StreamTrackingInfo>(args["tracking"])
            JsonPrimitive(tracking != null && catalog().registerPlayback(tracking, args.text("cpn").orEmpty()))
        }
        "reportWatchtime" -> {
            val tracking = Wire.decodeOrNull<StreamTrackingInfo>(args["tracking"])
            JsonPrimitive(tracking != null && catalog().reportWatchtime(tracking, args.text("cpn").orEmpty(), args.double("playedSeconds") ?: 0.0, args.double("totalSeconds") ?: 0.0))
        }
        else -> throw IllegalArgumentException("$method isn't something a module answers")
    }

    override fun cancel(id: Long) {
        calls.remove(id)?.cancel()
    }

    override fun close() {
        work.cancel()
        (module as? AutoCloseable)?.close()
        synchronized(licenses) { licenses.clear() }
    }

    override fun hasSurface(): Boolean = module is SurfaceModule

    override fun surfaceStart(assetPath: String, dataDir: String, extraPaths: Array<String>?): Int =
        (module as? SurfaceModule)?.surfaceStart(assetPath, dataDir, extraPaths?.toList().orEmpty()) ?: -1

    override fun surfaceSize(): IntArray = (module as? SurfaceModule)?.surfaceSize() ?: intArrayOf(320, 200)

    override fun surfaceTick(): Boolean = (module as? SurfaceModule)?.surfaceTick() ?: false

    override fun surfaceCopyFrame(pixels: IntArray, lastSeq: Long): Long =
        (module as? SurfaceModule)?.surfaceCopyFrame(pixels, lastSeq) ?: lastSeq

    override fun surfaceSetPaused(paused: Boolean) {
        (module as? SurfaceModule)?.surfaceSetPaused(paused)
    }

    override fun surfaceKey(keyCode: Int, pressed: Boolean) {
        (module as? SurfaceModule)?.surfaceKey(keyCode, pressed)
    }

    override fun surfaceTurn(delta: Int) {
        (module as? SurfaceModule)?.surfaceTurn(delta)
    }

    override fun surfaceLastError(): String = (module as? SurfaceModule)?.surfaceLastError().orEmpty()

    private fun source(): SourceModule = module as SourceModule

    private fun catalog(): CatalogModule = module as CatalogModule

    private fun licenseId(server: LicenseServer): String {
        val id = licenseIds.incrementAndGet().toString()
        synchronized(licenses) { licenses[id] = server }
        return id
    }

    private fun licenseFor(id: String?): LicenseServer =
        synchronized(licenses) { licenses[id] } ?: throw IllegalStateException("That stream's license is gone, start it again")

    private class HostOverBridge(private val bridge: HostBridge) : ModuleHost {
        override val storage = object : ModuleStorage {
            override fun get(key: String): String? = bridge.get(key)
            override fun set(key: String, value: String?) = bridge.set(key, value)
        }

        override fun http(request: HttpRequest): HttpResponse {
            val answer = bridge.http(Wire.requestToJson(request).toString(), request.body)
            return HttpResponse(answer.code, answer.body, Wire.mapFrom(Wire.parse(answer.headers)))
        }

        override fun log(message: String) = bridge.log(message)

        override val folder = File(bridge.folder())

        override val platformContext: Any?
            get() = runCatching { bridge.platformContext() }.getOrNull()
    }

    private companion object {
        const val MaxLicenses = 32
    }
}
