// on-disk structures for .mochypck: fixed 80-byte binary header, compiled index, and verified zip payload

package com.mochyy.mochymusic.pack

import kotlinx.serialization.Serializable

object MochyPackConstants {
    // four-byte magic spelling MPCK so mochy spots a module pack on the very first read
    val MAGIC = byteArrayOf('M'.code.toByte(), 'P'.code.toByte(), 'C'.code.toByte(), 'K'.code.toByte())
    const val FORMAT_VERSION: Int = 1
    const val HEADER_SIZE_BYTES: Int = 80
    const val INDEX_FORMAT_VERSION: Int = 1
    const val EXTENSION: String = ".mochypck"

    // header flags
    const val FLAG_SIGNED: Int = 1 shl 0
    const val FLAG_HAS_NATIVE_LIBS: Int = 1 shl 1
    const val FLAG_HAS_ASSETS: Int = 1 shl 2
    const val FLAG_ZLIB_INDEX: Int = 1 shl 3

    // safety caps so a malformed file cannot exhaust memory during inspection or extraction
    const val MAX_INDEX_BYTES: Int = 2 * 1024 * 1024
    const val MAX_PAYLOAD_BYTES: Long = 128L * 1024L * 1024L
    const val MAX_ENTRY_BYTES: Long = 96L * 1024L * 1024L
    const val MAX_ENTRIES: Int = 2048
}

/** fixed 80-byte preamble placed at byte 0 of every .mochypck file */
data class MochyPackHeader(
    val formatVersion: Int,
    val flags: Int,
    val moduleApiVersion: Int,
    val indexLength: Int,
    val payloadSha256: ByteArray,
    val indexSha256: ByteArray,
) {
    val payloadOffset: Long get() = MochyPackConstants.HEADER_SIZE_BYTES.toLong() + indexLength.toLong()
    val isSigned: Boolean get() = (flags and MochyPackConstants.FLAG_SIGNED) != 0
    val hasNativeLibs: Boolean get() = (flags and MochyPackConstants.FLAG_HAS_NATIVE_LIBS) != 0
    val hasAssets: Boolean get() = (flags and MochyPackConstants.FLAG_HAS_ASSETS) != 0
    val isIndexCompressed: Boolean get() = (flags and MochyPackConstants.FLAG_ZLIB_INDEX) != 0

    val payloadSha256Hex: String get() = payloadSha256.toHex()
    val indexSha256Hex: String get() = indexSha256.toHex()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MochyPackHeader) return false
        return formatVersion == other.formatVersion &&
            flags == other.flags &&
            moduleApiVersion == other.moduleApiVersion &&
            indexLength == other.indexLength &&
            payloadSha256.contentEquals(other.payloadSha256) &&
            indexSha256.contentEquals(other.indexSha256)
    }

    override fun hashCode(): Int {
        var result = formatVersion
        result = 31 * result + flags
        result = 31 * result + moduleApiVersion
        result = 31 * result + indexLength
        result = 31 * result + payloadSha256.contentHashCode()
        result = 31 * result + indexSha256.contentHashCode()
        return result
    }
}

@Serializable
data class PackOptionSpec(
    val id: String,
    val label: String,
    val description: String = "",
)

@Serializable
data class PackChoiceSpec(
    val id: String,
    val label: String,
    val options: List<PackOptionSpec> = emptyList(),
)

@Serializable
data class PackFieldSpec(
    val id: String,
    val label: String,
    val kind: String = "Text",
    val defaultValue: String = "",
    val hint: String = "",
    val description: String = "",
    val options: List<PackOptionSpec> = emptyList(),
)

/** one provider module compiled inside a .mochypck */
@Serializable
data class PackProviderEntry(
    val id: String,
    val name: String,
    val displayName: String = name,
    val description: String = "",
    val capabilities: Long = 0L,
    val entryClass: String,
    val defaultPriority: Int = 100,
    val requiresAccount: Boolean = false,
    val isLastResort: Boolean = false,
    val enabledByDefault: Boolean = true,
    val iconPath: String? = null,
    val bannerPath: String? = null,
    val projectName: String? = null,
    val projectUrl: String? = null,
    val websiteUrl: String? = null,
    val choices: List<PackChoiceSpec> = emptyList(),
    val fields: List<PackFieldSpec> = emptyList(),
)

/** checksum entry for one file stored inside the .mochypck zip payload */
@Serializable
data class PackFileEntry(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
)

/** compiled table of contents embedded directly after the 80-byte header */
@Serializable
data class MochyPackIndex(
    val packId: String,
    val name: String,
    val version: String,
    val versionCode: Int = 1,
    val description: String = "",
    val author: String = "",
    val sourceUrl: String? = null,
    val minAppVersion: String? = null,
    val builtAtEpochMs: Long = 0L,
    val capabilities: Long = 0L,
    val codePath: String = "code/providers.apk",
    val codeSha256: String = "",
    val nativeAbis: List<String> = emptyList(),
    val iconPath: String? = null,
    val bannerPath: String? = null,
    val signerSha256: String? = null,
    val providers: List<PackProviderEntry> = emptyList(),
    val files: List<PackFileEntry> = emptyList(),
) {
    val capabilitySet: Set<PackCapability> get() = PackCapability.fromMask(capabilities)
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
