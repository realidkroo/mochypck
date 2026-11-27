// deterministic binary codec for the .mochypck header and compiled index so inspection needs zero json or reflection

package com.mochyy.mochymusic.pack

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

object MochyPackCodec {

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256Hex(bytes: ByteArray): String = sha256(bytes).toHex()

    fun encodeHeader(header: MochyPackHeader): ByteArray {
        require(header.payloadSha256.size == 32) { "payload sha-256 must be 32 bytes" }
        require(header.indexSha256.size == 32) { "index sha-256 must be 32 bytes" }
        val out = ByteArrayOutputStream(MochyPackConstants.HEADER_SIZE_BYTES)
        DataOutputStream(out).use { d ->
            d.write(MochyPackConstants.MAGIC)
            d.writeShort(header.formatVersion)
            d.writeShort(header.flags)
            d.writeInt(header.moduleApiVersion)
            d.writeInt(header.indexLength)
            d.write(header.payloadSha256)
            d.write(header.indexSha256)
        }
        return out.toByteArray()
    }

    fun decodeHeader(input: InputStream): MochyPackHeader {
        val raw = readExact(input, MochyPackConstants.HEADER_SIZE_BYTES)
        val d = DataInputStream(ByteArrayInputStream(raw))
        val magic = ByteArray(4).also { d.readFully(it) }
        if (!magic.contentEquals(MochyPackConstants.MAGIC)) {
            throw IOException("Not a .mochypck file (bad magic)")
        }
        val formatVersion = d.readUnsignedShort()
        val flags = d.readUnsignedShort()
        val apiVersion = d.readInt()
        val indexLength = d.readInt()
        if (indexLength <= 0 || indexLength > MochyPackConstants.MAX_INDEX_BYTES) {
            throw IOException("Invalid compiled index size: $indexLength")
        }
        val payloadSha = ByteArray(32).also { d.readFully(it) }
        val indexSha = ByteArray(32).also { d.readFully(it) }
        return MochyPackHeader(
            formatVersion = formatVersion,
            flags = flags,
            moduleApiVersion = apiVersion,
            indexLength = indexLength,
            payloadSha256 = payloadSha,
            indexSha256 = indexSha,
        )
    }

    fun encodeIndex(index: MochyPackIndex, compress: Boolean = true): ByteArray {
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).use { d ->
            d.writeShort(MochyPackConstants.INDEX_FORMAT_VERSION)
            d.writeStr(index.packId)
            d.writeStr(index.name)
            d.writeStr(index.version)
            d.writeInt(index.versionCode)
            d.writeStr(index.description)
            d.writeStr(index.author)
            d.writeOptStr(index.sourceUrl)
            d.writeOptStr(index.minAppVersion)
            d.writeLong(index.builtAtEpochMs)
            d.writeLong(index.capabilities)
            d.writeStr(index.codePath)
            d.writeStr(index.codeSha256)
            d.writeStrList(index.nativeAbis)
            d.writeOptStr(index.iconPath)
            d.writeOptStr(index.bannerPath)
            d.writeOptStr(index.signerSha256)

            d.writeInt(index.providers.size)
            for (p in index.providers) {
                d.writeStr(p.id)
                d.writeStr(p.name)
                d.writeStr(p.displayName)
                d.writeStr(p.description)
                d.writeLong(p.capabilities)
                d.writeStr(p.entryClass)
                d.writeInt(p.defaultPriority)
                var pFlags = 0
                if (p.requiresAccount) pFlags = pFlags or 1
                if (p.isLastResort) pFlags = pFlags or 2
                if (p.enabledByDefault) pFlags = pFlags or 4
                d.writeByte(pFlags)
                d.writeOptStr(p.iconPath)
                d.writeOptStr(p.bannerPath)
                d.writeOptStr(p.projectName)
                d.writeOptStr(p.projectUrl)
                d.writeOptStr(p.websiteUrl)

                d.writeInt(p.choices.size)
                for (c in p.choices) {
                    d.writeStr(c.id)
                    d.writeStr(c.label)
                    d.writeOptions(c.options)
                }

                d.writeInt(p.fields.size)
                for (f in p.fields) {
                    d.writeStr(f.id)
                    d.writeStr(f.label)
                    d.writeStr(f.kind)
                    d.writeStr(f.defaultValue)
                    d.writeStr(f.hint)
                    d.writeStr(f.description)
                    d.writeOptions(f.options)
                }
            }

            d.writeInt(index.files.size)
            for (file in index.files) {
                d.writeStr(file.path)
                d.writeLong(file.sizeBytes)
                d.writeStr(file.sha256)
            }
        }
        val plain = raw.toByteArray()
        if (!compress) return plain
        val deflated = ByteArrayOutputStream()
        DeflaterOutputStream(deflated, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(plain) }
        return deflated.toByteArray()
    }

    fun decodeIndex(bytes: ByteArray, compressed: Boolean): MochyPackIndex {
        val plain = if (compressed) inflateCapped(bytes, MochyPackConstants.MAX_INDEX_BYTES * 2) else bytes
        val d = DataInputStream(ByteArrayInputStream(plain))
        val version = d.readUnsignedShort()
        if (version != MochyPackConstants.INDEX_FORMAT_VERSION) {
            throw IOException("Unsupported compiled index version: $version")
        }
        val packId = d.readStr()
        val name = d.readStr()
        val ver = d.readStr()
        val versionCode = d.readInt()
        val description = d.readStr()
        val author = d.readStr()
        val sourceUrl = d.readOptStr()
        val minAppVersion = d.readOptStr()
        val builtAt = d.readLong()
        val capabilities = d.readLong()
        val codePath = d.readStr()
        val codeSha256 = d.readStr()
        val nativeAbis = d.readStrList()
        val iconPath = d.readOptStr()
        val bannerPath = d.readOptStr()
        val signerSha256 = d.readOptStr()

        val providerCount = d.readBoundedCount(512)
        val providers = List(providerCount) {
            val id = d.readStr()
            val pName = d.readStr()
            val displayName = d.readStr()
            val pDesc = d.readStr()
            val pCaps = d.readLong()
            val entryClass = d.readStr()
            val priority = d.readInt()
            val pFlags = d.readUnsignedByte()
            val icon = d.readOptStr()
            val banner = d.readOptStr()
            val projectName = d.readOptStr()
            val projectUrl = d.readOptStr()
            val websiteUrl = d.readOptStr()

            val choiceCount = d.readBoundedCount(64)
            val choices = List(choiceCount) {
                PackChoiceSpec(id = d.readStr(), label = d.readStr(), options = d.readOptions())
            }
            val fieldCount = d.readBoundedCount(128)
            val fields = List(fieldCount) {
                PackFieldSpec(
                    id = d.readStr(),
                    label = d.readStr(),
                    kind = d.readStr(),
                    defaultValue = d.readStr(),
                    hint = d.readStr(),
                    description = d.readStr(),
                    options = d.readOptions(),
                )
            }
            PackProviderEntry(
                id = id,
                name = pName,
                displayName = displayName,
                description = pDesc,
                capabilities = pCaps,
                entryClass = entryClass,
                defaultPriority = priority,
                requiresAccount = (pFlags and 1) != 0,
                isLastResort = (pFlags and 2) != 0,
                enabledByDefault = (pFlags and 4) != 0,
                iconPath = icon,
                bannerPath = banner,
                projectName = projectName,
                projectUrl = projectUrl,
                websiteUrl = websiteUrl,
                choices = choices,
                fields = fields,
            )
        }

        val fileCount = d.readBoundedCount(MochyPackConstants.MAX_ENTRIES)
        val files = List(fileCount) {
            PackFileEntry(path = d.readStr(), sizeBytes = d.readLong(), sha256 = d.readStr())
        }

        return MochyPackIndex(
            packId = packId,
            name = name,
            version = ver,
            versionCode = versionCode,
            description = description,
            author = author,
            sourceUrl = sourceUrl,
            minAppVersion = minAppVersion,
            builtAtEpochMs = builtAt,
            capabilities = capabilities,
            codePath = codePath,
            codeSha256 = codeSha256,
            nativeAbis = nativeAbis,
            iconPath = iconPath,
            bannerPath = bannerPath,
            signerSha256 = signerSha256,
            providers = providers,
            files = files,
        )
    }

    internal fun readExact(input: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buf, offset, count - offset)
            if (read < 0) throw EOFException("Unexpected end of .mochypck stream")
            offset += read
        }
        return buf
    }

    private fun inflateCapped(compressed: ByteArray, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        InflaterInputStream(ByteArrayInputStream(compressed)).use { inf ->
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val read = inf.read(buf)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IOException("Compiled index exceeds safety cap")
                out.write(buf, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun DataOutputStream.writeStr(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeOptStr(value: String?) {
        if (value == null) {
            writeInt(-1)
        } else {
            writeStr(value)
        }
    }

    private fun DataOutputStream.writeStrList(values: List<String>) {
        writeInt(values.size)
        for (v in values) writeStr(v)
    }

    private fun DataOutputStream.writeOptions(options: List<PackOptionSpec>) {
        writeInt(options.size)
        for (o in options) {
            writeStr(o.id)
            writeStr(o.label)
            writeStr(o.description)
        }
    }

    private fun DataInputStream.readStr(): String {
        val len = readInt()
        if (len < 0 || len > 1024 * 1024) throw IOException("Bad string length: $len")
        val bytes = ByteArray(len)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun DataInputStream.readOptStr(): String? {
        val len = readInt()
        if (len == -1) return null
        if (len < 0 || len > 1024 * 1024) throw IOException("Bad optional string length: $len")
        val bytes = ByteArray(len)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun DataInputStream.readStrList(): List<String> = List(readBoundedCount(256)) { readStr() }

    private fun DataInputStream.readOptions(): List<PackOptionSpec> = List(readBoundedCount(128)) {
        PackOptionSpec(id = readStr(), label = readStr(), description = readStr())
    }

    private fun DataInputStream.readBoundedCount(max: Int): Int {
        val count = readInt()
        if (count < 0 || count > max) throw IOException("List count out of bounds: $count")
        return count
    }
}
