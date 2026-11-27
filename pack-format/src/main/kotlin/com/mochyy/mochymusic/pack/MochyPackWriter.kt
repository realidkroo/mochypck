// deterministic writer for .mochypck containers used by the modulestuff compiler and tests

package com.mochyy.mochymusic.pack

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class PackBuildOutput(
    val outputFile: File,
    val header: MochyPackHeader,
    val index: MochyPackIndex,
    val packSha256: String,
    val totalBytes: Long,
)

object MochyPackWriter {

    private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

    /** compiles metadata and payload files into a deterministic, integrity-checked .mochypck file */
    fun write(
        outputFile: File,
        moduleApiVersion: Int,
        draftIndex: MochyPackIndex,
        payloadFiles: Map<String, ByteArray>,
        signed: Boolean = false,
        compressIndex: Boolean = true,
        includeManifestJson: Boolean = true,
    ): PackBuildOutput {
        val sortedEntries = linkedMapOf<String, ByteArray>()
        for ((rawPath, bytes) in payloadFiles.entries.sortedBy { it.key }) {
            val clean = MochyPackReader.sanitizeRelativePath(rawPath)
                ?: throw IllegalArgumentException("Unsafe payload path: $rawPath")
            sortedEntries[clean] = bytes
        }

        // fold every provider's capability bits into the top-level mask so O(1) inspection is always complete
        val combinedCaps = draftIndex.capabilities or draftIndex.providers.fold(0L) { acc, p -> acc or p.capabilities }
        val codeBytes = draftIndex.codePath.takeIf { it.isNotBlank() }?.let { sortedEntries[it] }
        val codeSha = codeBytes?.let(MochyPackCodec::sha256Hex) ?: draftIndex.codeSha256

        // first pass builds the index without manifest.json to serialize a readable mirror inside the zip
        val preliminaryFiles = sortedEntries.map { (path, bytes) ->
            PackFileEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = MochyPackCodec.sha256Hex(bytes))
        }
        val preliminaryIndex = draftIndex.copy(
            capabilities = combinedCaps,
            codeSha256 = codeSha,
            files = preliminaryFiles,
        )
        if (includeManifestJson && "manifest.json" !in sortedEntries) {
            val manifestBytes = prettyJson.encodeToString(preliminaryIndex).toByteArray(Charsets.UTF_8)
            sortedEntries["manifest.json"] = manifestBytes
        }

        // final sorted file table covering every single entry in the zip payload
        val finalEntries = sortedEntries.entries.sortedBy { it.key }
        val finalFileTable = finalEntries.map { (path, bytes) ->
            PackFileEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = MochyPackCodec.sha256Hex(bytes))
        }
        val finalIndex = preliminaryIndex.copy(files = finalFileTable)

        // build deterministic zip payload with fixed timestamps so rebuilds are byte-identical
        val zipOutBytes = ByteArrayOutputStream()
        ZipOutputStream(zipOutBytes).use { zip ->
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
            for ((path, bytes) in finalEntries) {
                val entry = ZipEntry(path).apply { time = 0L }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val payloadBytes = zipOutBytes.toByteArray()
        val payloadSha = MochyPackCodec.sha256(payloadBytes)

        val indexBytes = MochyPackCodec.encodeIndex(finalIndex, compress = compressIndex)
        val indexSha = MochyPackCodec.sha256(indexBytes)

        var flags = 0
        if (signed) flags = flags or MochyPackConstants.FLAG_SIGNED
        if (finalIndex.nativeAbis.isNotEmpty()) flags = flags or MochyPackConstants.FLAG_HAS_NATIVE_LIBS
        if (finalEntries.any { it.key.startsWith("assets/") }) flags = flags or MochyPackConstants.FLAG_HAS_ASSETS
        if (compressIndex) flags = flags or MochyPackConstants.FLAG_ZLIB_INDEX

        val header = MochyPackHeader(
            formatVersion = MochyPackConstants.FORMAT_VERSION,
            flags = flags,
            moduleApiVersion = moduleApiVersion,
            indexLength = indexBytes.size,
            payloadSha256 = payloadSha,
            indexSha256 = indexSha,
        )
        val headerBytes = MochyPackCodec.encodeHeader(header)

        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().buffered().use { out ->
            out.write(headerBytes)
            out.write(indexBytes)
            out.write(payloadBytes)
        }

        val fullSha = MochyPackCodec.sha256Hex(headerBytes + indexBytes + payloadBytes)
        return PackBuildOutput(
            outputFile = outputFile,
            header = header,
            index = finalIndex,
            packSha256 = fullSha,
            totalBytes = outputFile.length(),
        )
    }
}
