// capability bitmask stored in a compiled .mochypck header so mochy knows what a pack brings without unpacking it

package com.mochyy.mochymusic.pack

/** one kind of capability a .mochypck provider can supply */
enum class PackCapability(val bit: Long, val wireName: String, val label: String) {
    AUDIO_SOURCE(1L shl 0, "audio", "Audio streams"),
    CATALOG(1L shl 1, "catalog", "Catalog & browse"),
    SEARCH(1L shl 2, "search", "Search"),
    HOME(1L shl 3, "home", "Home feed"),
    ARTIST(1L shl 4, "artist", "Artist pages"),
    ALBUM(1L shl 5, "album", "Album pages"),
    PLAYLIST(1L shl 6, "playlist", "Playlists"),
    RADIO(1L shl 7, "radio", "Radio & up next"),
    VIDEO_STREAM(1L shl 8, "video", "Music videos"),
    LYRICS(1L shl 9, "lyrics", "Lyrics"),
    METADATA(1L shl 10, "metadata", "Song info"),
    COVER(1L shl 11, "cover", "Covers"),
    CANVAS(1L shl 12, "canvas", "Animated covers"),
    ARTIST_IMAGE(1L shl 13, "artist_image", "Artist photos"),
    ARTIST_VIDEO(1L shl 14, "artist_video", "Artist videos"),
    ACCOUNT_SYNC(1L shl 15, "account_sync", "Account sync"),
    LOOK(1L shl 16, "look", "Looks & skins"),
    AUDIO_PROCESSOR(1L shl 17, "audio_fx", "Audio effects");

    companion object {
        fun maskOf(capabilities: Iterable<PackCapability>): Long =
            capabilities.fold(0L) { acc, cap -> acc or cap.bit }

        fun fromMask(mask: Long): Set<PackCapability> =
            entries.filterTo(LinkedHashSet()) { (mask and it.bit) != 0L }

        fun fromWireName(name: String): PackCapability? =
            entries.firstOrNull { it.wireName.equals(name.trim(), ignoreCase = true) || it.name.equals(name.trim(), ignoreCase = true) }

        fun has(mask: Long, capability: PackCapability): Boolean = (mask and capability.bit) != 0L
    }
}
