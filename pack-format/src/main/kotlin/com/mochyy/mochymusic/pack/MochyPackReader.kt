// reader and cryptographic verifier for .mochypck files: O(1) header inspect, full sha-256 verification, and safe extraction

package com.mochyy.mochymusic.pack

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

sealed interface PackInspectResult {
    data class Valid(val header: MochyPackHeader, val index: MochyPackIndex) : PackInspectResult
    data class Invalid(val reason: String) : PackInspectResult
}

sealed interface PackVerifyResult {
    data class Verified(
        val header: MochyPackHeader,
        val index: MochyPackIndex,
        val packSha256: String,
    ) : PackVerifyResult
    data class Invalid(val reason: String) : PackVerifyResult
}

object MochyPackReader {

    /** checks magic bytes quickly so file pickers can tell a .mochypck from a regular .zip */
    fun hasMagic(input: InputStream): Boolean = runCatching {
        val magic = ByteArray(4)
        var offset = 0
        while (offset < 4) {
            val read = input.read(magic, offset, 4 - offset)
            if (read < 0) return false
            offset += read
        }
        magic.contentEquals(MochyPackConstants.MAGIC)
    }.getOrDefault(false)

    /** reads only the 80-byte header and compiled index in O(1) without touching the zip payload */
    fun inspect(file: File): PackInspectResult =
        runCatching { FileInputStream(file).buffered().use { inspect(it) } }
            .getOrElse { PackInspectResult.Invalid(it.message ?: "Couldn't read pack file") }

    fun inspect(input: InputStream): PackInspectResult = runCatching {
        val header = MochyPackCodec.decodeHeader(input)
        if (header.formatVersion != MochyPackConstants.FORMAT_VERSION) {
            return PackInspectResult.Invalid("Unsupported pack format v${header.formatVersion}")
        }
        val indexBytes = MochyPackCodec.readExact(input, header.indexLength)
        val actualIndexSha = MochyPackCodec.sha256(indexBytes)
        if (!actualIndexSha.contentEquals(header.indexSha256)) {
            return PackInspectResult.Invalid("Compiled index checksum mismatch")
        }
        val index = MochyPackCodec.decodeIndex(indexBytes, header.isIndexCompressed)
        validateIndexMetadata(index)?.let { return PackInspectResult.Invalid(it) }
        PackInspectResult.Valid(header, index)
    }.getOrElse { PackInspectResult.Invalid(it.message ?: "Malformed .mochypck") }

    /** verifies header, compiled index, entire zip payload sha-256, and every file's sha-256 */
    fun verify(file: File, expectedApiVersion: Int? = null): PackVerifyResult = runCatching {
        if (!file.isFile) return PackVerifyResult.Invalid("Pack file does not exist")
        val totalLen = file.length()
        if (totalLen <= MochyPackConstants.HEADER_SIZE_BYTES) return PackVerifyResult.Invalid("Pack file is truncated")
        if (totalLen > MochyPackConstants.MAX_PAYLOAD_BYTES + MochyPackConstants.MAX_INDEX_BYTES) {
            return PackVerifyResult.Invalid("Pack file exceeds maximum allowed size")
        }

        val packDigest = MessageDigest.getInstance("SHA-256")
        val payloadDigest = MessageDigest.getInstance("SHA-256")
        val header: MochyPackHeader
        val index: MochyPackIndex

        FileInputStream(file).buffered().use { raw ->
            val headerBytes = MochyPackCodec.readExact(raw, MochyPackConstants.HEADER_SIZE_BYTES)
            packDigest.update(headerBytes)
            header = MochyPackCodec.decodeHeader(headerBytes.inputStream())
            if (header.formatVersion != MochyPackConstants.FORMAT_VERSION) {
                return PackVerifyResult.Invalid("Unsupported pack format v${header.formatVersion}")
            }
            if (expectedApiVersion != null && header.moduleApiVersion != expectedApiVersion) {
                return PackVerifyResult.Invalid("Built for module API ${header.moduleApiVersion}, Mochy expects API $expectedApiVersion")
            }

            val indexBytes = MochyPackCodec.readExact(raw, header.indexLength)
            packDigest.update(indexBytes)
            if (!MochyPackCodec.sha256(indexBytes).contentEquals(header.indexSha256)) {
                return PackVerifyResult.Invalid("Compiled index checksum mismatch")
            }
            index = MochyPackCodec.decodeIndex(indexBytes, header.isIndexCompressed)
            validateIndexMetadata(index)?.let { return PackVerifyResult.Invalid(it) }

            val buf = ByteArray(64 * 1024)
            var payloadLen = 0L
            while (true) {
                val read = raw.read(buf)
                if (read < 0) break
                payloadLen += read
                if (payloadLen > MochyPackConstants.MAX_PAYLOAD_BYTES) {
                    return PackVerifyResult.Invalid("Pack payload exceeds maximum allowed size")
                }
                packDigest.update(buf, 0, read)
                payloadDigest.update(buf, 0, read)
            }
        }

        if (!payloadDigest.digest().contentEquals(header.payloadSha256)) {
            return PackVerifyResult.Invalid("Payload checksum mismatch (file was modified or corrupted)")
        }

        // verify every individual zip entry against the compiled file table
        val expectedFiles = index.files.associateBy { it.path }
        val seen = HashSet<String>(expectedFiles.size)
        openPayloadZip(file, header).use { zip ->
            var entryCount = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MochyPackConstants.MAX_ENTRIES) {
                    return PackVerifyResult.Invalid("Too many entries inside pack")
                }
                if (entry.isDirectory) continue
                val path = sanitizeRelativePath(entry.name)
                    ?: return PackVerifyResult.Invalid("Unsafe entry path: ${entry.name}")
                val spec = expectedFiles[path]
                    ?: return PackVerifyResult.Invalid("Unindexed file inside payload: $path")
                val fileDigest = MessageDigest.getInstance("SHA-256")
                val buf = ByteArray(32 * 1024)
                var size = 0L
                while (true) {
                    val read = zip.read(buf)
                    if (read < 0) break
                    size += read
                    if (size > MochyPackConstants.MAX_ENTRY_BYTES) {
                        return PackVerifyResult.Invalid("Entry $path exceeds size limit")
                    }
                    fileDigest.update(buf, 0, read)
                }
                if (size != spec.sizeBytes) {
                    return PackVerifyResult.Invalid("Size mismatch for $path")
                }
                val actualHex = fileDigest.digest().toHex()
                if (!actualHex.equals(spec.sha256, ignoreCase = true)) {
                    return PackVerifyResult.Invalid("Checksum mismatch for $path")
                }
                seen += path
            }
        }

        val missing = expectedFiles.keys - seen
        if (missing.isNotEmpty()) {
            return PackVerifyResult.Invalid("Missing indexed files: ${missing.take(3).joinToString()}")
        }
        if (index.codePath.isNotBlank()) {
            val codeEntry = expectedFiles[index.codePath]
                ?: return PackVerifyResult.Invalid("Code payload missing from index: ${index.codePath}")
            if (index.codeSha256.isNotBlank() && !codeEntry.sha256.equals(index.codeSha256, ignoreCase = true)) {
                return PackVerifyResult.Invalid("Code payload checksum mismatch")
            }
        }

        PackVerifyResult.Verified(header, index, packDigest.digest().toHex())
    }.getOrElse { PackVerifyResult.Invalid(it.message ?: "Pack verification failed") }

    /** extracts a verified pack's payload files safely into targetDir */
    fun extractPayload(file: File, header: MochyPackHeader, targetDir: File): Map<String, File> {
        targetDir.mkdirs()
        val canonicalRoot = targetDir.canonicalFile
        val extracted = LinkedHashMap<String, File>()
        openPayloadZip(file, header).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                if (count > MochyPackConstants.MAX_ENTRIES) throw IOException("Too many entries")
                if (entry.isDirectory) continue
                val clean = sanitizeRelativePath(entry.name) ?: throw IOException("Unsafe path: ${entry.name}")
                val dest = File(canonicalRoot, clean).canonicalFile
                if (!dest.path.startsWith(canonicalRoot.path + File.separator)) {
                    throw IOException("Path escapes target dir: $clean")
                }
                dest.parentFile?.mkdirs()
                var written = 0L
                val buf = ByteArray(32 * 1024)
                dest.outputStream().use { out ->
                    while (true) {
                        val read = zip.read(buf)
                        if (read < 0) break
                        written += read
                        if (written > MochyPackConstants.MAX_ENTRY_BYTES) {
                            throw IOException("Entry too large: $clean")
                        }
                        out.write(buf, 0, read)
                    }
                }
                extracted[clean] = dest
            }
        }
        return extracted
    }

    /** reads one specific asset (like an icon) from the payload without unpacking the whole pack */
    fun readEntryBytes(file: File, header: MochyPackHeader, path: String, maxBytes: Int = 2 * 1024 * 1024): ByteArray? {
        val cleanTarget = sanitizeRelativePath(path) ?: return null
        return runCatching {
            openPayloadZip(file, header).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: return@use null
                    if (entry.isDirectory) continue
                    if (sanitizeRelativePath(entry.name) == cleanTarget) {
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val read = zip.read(buf)
                            if (read < 0) break
                            if (out.size() + read > maxBytes) return@use null
                            out.write(buf, 0, read)
                        }
                        return@use out.toByteArray()
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun openPayloadZip(file: File, header: MochyPackHeader): ZipInputStream {
        val raw = FileInputStream(file).buffered()
        var toSkip = header.payloadOffset
        while (toSkip > 0) {
            val skipped = raw.skip(toSkip)
            if (skipped <= 0) {
                if (raw.read() < 0) {
                    raw.close()
                    throw IOException("Unexpected EOF seeking payload")
                }
                toSkip--
            } else {
                toSkip -= skipped
            }
        }
        return ZipInputStream(raw)
    }

    private fun validateIndexMetadata(index: MochyPackIndex): String? {
        if (index.packId.isBlank() || !SafeIdRegex.matches(index.packId)) {
            return "Invalid pack id '${index.packId}'"
        }
        if (index.name.isBlank()) return "Pack has no name"
        if (index.version.isBlank()) return "Pack has no version"
        if (index.providers.isEmpty() && index.capabilities == 0L) return "Pack declares no providers or capabilities"
        for (p in index.providers) {
            if (p.id.isBlank() || !SafeIdRegex.matches(p.id)) return "Invalid provider id '${p.id}'"
            if (p.entryClass.isBlank()) return "Provider '${p.id}' has no entry class"
        }
        return null
    }

    internal fun sanitizeRelativePath(raw: String): String? {
        val clean = raw.replace('\\', '/').trimStart('/')
        if (clean.isBlank() || '\u0000' in clean) return null
        val parts = clean.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    private val SafeIdRegex = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
}
