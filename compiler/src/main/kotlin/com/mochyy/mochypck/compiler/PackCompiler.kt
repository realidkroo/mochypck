// compiles a pack.json spec, compiled android apk/dex, and asset folder into a verified .mochypck container

package com.mochyy.mochypck.compiler

import com.mochyy.mochymusic.module.MODULE_API_VERSION
import com.mochyy.mochymusic.pack.MochyPackCodec
import com.mochyy.mochymusic.pack.MochyPackIndex
import com.mochyy.mochymusic.pack.MochyPackReader
import com.mochyy.mochymusic.pack.MochyPackWriter
import com.mochyy.mochymusic.pack.PackBuildOutput
import com.mochyy.mochymusic.pack.PackCapability
import com.mochyy.mochymusic.pack.PackProviderEntry
import com.mochyy.mochymusic.pack.PackVerifyResult
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.zip.ZipFile

data class CompileRequest(
    val specFile: File,
    val apkFile: File,
    val outputFile: File,
    val apiVersion: Int = MODULE_API_VERSION,
    val verifyDexSymbols: Boolean = true,
    val deterministicTimestamp: Long = 0L,
)

data class CompileReport(
    val output: PackBuildOutput,
    val warnings: List<String>,
    val dexClassCount: Int,
    val nativeAbis: List<String>,
    val signerSha256: String?,
)

object PackCompiler {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val CodePayloadPath = "code/providers.apk"
    private const val BridgeSideClass = "com.mochyy.mochymusic.module.bridge.ModuleSide"

    fun compile(request: CompileRequest): CompileReport {
        require(request.specFile.isFile) { "Pack spec not found: ${request.specFile.path}" }
        require(request.apkFile.isFile) { "Compiled APK not found: ${request.apkFile.path}" }

        val specDir = request.specFile.absoluteFile.parentFile ?: File(".")
        val spec = json.decodeFromString<PackSourceSpec>(request.specFile.readText(Charsets.UTF_8))
        validateSpec(spec)

        val warnings = mutableListOf<String>()
        val apkInspection = inspectApk(request.apkFile)
        if (!apkInspection.hasDex) {
            throw IllegalStateException("APK ${request.apkFile.name} contains no classes.dex bytecode")
        }

        if (request.verifyDexSymbols) {
            val requiredClasses = listOf(BridgeSideClass) + spec.providers.map { it.entryClass }
            val missing = requiredClasses.filter { cls ->
                val descriptor = "L${cls.replace('.', '/')};"
                descriptor !in apkInspection.dexStrings
            }
            if (missing.isNotEmpty()) {
                throw IllegalStateException(
                    "Missing compiled classes in ${request.apkFile.name} (check R8/ProGuard or entryClass typos): ${missing.joinToString()}",
                )
            }
        }

        val payloadFiles = linkedMapOf<String, ByteArray>()
        payloadFiles[CodePayloadPath] = request.apkFile.readBytes()

        // collect explicit assets directory first
        spec.assetsDir?.takeIf { it.isNotBlank() }?.let { relDir ->
            val dir = File(specDir, relDir)
            if (dir.isDirectory) {
                dir.walkTopDown()
                    .filter { it.isFile && !it.name.startsWith(".") }
                    .sortedBy { it.relativeTo(dir).path }
                    .forEach { file ->
                        val rel = file.relativeTo(dir).path.replace('\\', '/')
                        payloadFiles["assets/$rel"] = file.readBytes()
                    }
            } else {
                warnings += "Declared assetsDir '$relDir' was not found next to ${request.specFile.name}"
            }
        }

        fun resolveAsset(path: String?): String? {
            if (path.isNullOrBlank()) return null
            val clean = path.replace('\\', '/').trimStart('/')
            if (clean in payloadFiles) return clean
            val prefixed = if (clean.startsWith("assets/")) clean else "assets/$clean"
            if (prefixed in payloadFiles) return prefixed
            val directFile = File(specDir, clean)
            if (directFile.isFile) {
                payloadFiles[prefixed] = directFile.readBytes()
                return prefixed
            }
            warnings += "Asset '$path' not found on disk; skipping"
            return null
        }

        val iconPath = resolveAsset(spec.icon)
        val bannerPath = resolveAsset(spec.banner)

        val providers = spec.providers.map { p ->
            val caps = p.capabilities.map { wire ->
                PackCapability.fromWireName(wire)
                    ?: throw IllegalArgumentException("Unknown capability '$wire' on provider '${p.id}'")
            }
            PackProviderEntry(
                id = p.id,
                name = p.name,
                displayName = p.displayName?.takeIf { it.isNotBlank() } ?: p.name,
                description = p.description,
                capabilities = PackCapability.maskOf(caps),
                entryClass = p.entryClass,
                defaultPriority = p.defaultPriority,
                requiresAccount = p.requiresAccount,
                isLastResort = p.isLastResort,
                enabledByDefault = p.enabledByDefault,
                iconPath = resolveAsset(p.icon),
                bannerPath = resolveAsset(p.banner),
                projectName = p.projectName,
                projectUrl = p.projectUrl,
                websiteUrl = p.websiteUrl,
                choices = p.choices,
                fields = p.fields,
            )
        }

        val draftIndex = MochyPackIndex(
            packId = spec.id,
            name = spec.name,
            version = spec.version,
            versionCode = spec.versionCode,
            description = spec.description,
            author = spec.author,
            sourceUrl = spec.sourceUrl,
            minAppVersion = spec.minAppVersion,
            builtAtEpochMs = request.deterministicTimestamp,
            capabilities = providers.fold(0L) { acc, p -> acc or p.capabilities },
            codePath = CodePayloadPath,
            nativeAbis = apkInspection.nativeAbis,
            iconPath = iconPath,
            bannerPath = bannerPath,
            signerSha256 = apkInspection.signerSha256,
            providers = providers,
        )

        val built = MochyPackWriter.write(
            outputFile = request.outputFile,
            moduleApiVersion = request.apiVersion,
            draftIndex = draftIndex,
            payloadFiles = payloadFiles,
            signed = apkInspection.signerSha256 != null,
            compressIndex = true,
            includeManifestJson = true,
        )

        // self-verify the emitted .mochypck so a broken artifact never leaves the compiler
        when (val check = MochyPackReader.verify(request.outputFile, expectedApiVersion = request.apiVersion)) {
            is PackVerifyResult.Verified -> Unit
            is PackVerifyResult.Invalid -> throw IllegalStateException("Post-compile verification failed: ${check.reason}")
        }

        return CompileReport(
            output = built,
            warnings = warnings,
            dexClassCount = apkInspection.dexClassCount,
            nativeAbis = apkInspection.nativeAbis,
            signerSha256 = apkInspection.signerSha256,
        )
    }

    private fun validateSpec(spec: PackSourceSpec) {
        val safeId = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
        require(safeId.matches(spec.id)) { "Invalid pack id '${spec.id}': use lowercase letters, digits, '.', '_' or '-'" }
        require(spec.name.isNotBlank()) { "Pack name must not be blank" }
        require(spec.version.isNotBlank()) { "Pack version must not be blank" }
        require(spec.providers.isNotEmpty()) { "Pack must declare at least one provider" }
        val seenIds = HashSet<String>()
        for (p in spec.providers) {
            require(safeId.matches(p.id)) { "Invalid provider id '${p.id}'" }
            require(seenIds.add(p.id)) { "Duplicate provider id '${p.id}'" }
            require(p.entryClass.isNotBlank()) { "Provider '${p.id}' must declare entryClass" }
            require(p.capabilities.isNotEmpty()) { "Provider '${p.id}' must declare at least one capability" }
        }
    }

    private data class ApkInspection(
        val hasDex: Boolean,
        val dexClassCount: Int,
        val dexStrings: Set<String>,
        val nativeAbis: List<String>,
        val signerSha256: String?,
    )

    private fun inspectApk(apk: File): ApkInspection {
        var hasDex = false
        var classCount = 0
        val strings = HashSet<String>()
        val abis = sortedSetOf<String>()
        var signerSha: String? = null

        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                val name = e.name
                when {
                    name.matches(Regex("^classes\\d*\\.dex$")) -> {
                        hasDex = true
                        val dexBytes = zip.getInputStream(e).use { it.readBytes() }
                        val parsed = parseDexStrings(dexBytes)
                        strings += parsed.descriptors
                        classCount += parsed.classDefCount
                    }
                    name.startsWith("lib/") && name.endsWith(".so") -> {
                        val parts = name.split('/')
                        if (parts.size >= 3) abis += parts[1]
                    }
                    signerSha == null && name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".EC") || name.endsWith(".DSA")) -> {
                        signerSha = extractCertSha256(zip.getInputStream(e).use { it.readBytes() })
                    }
                }
            }
        }
        return ApkInspection(hasDex, classCount, strings, abis.toList(), signerSha)
    }

    private data class DexInfo(val classDefCount: Int, val descriptors: Set<String>)

    // reads the string table and class count straight from dex binary headers (zero external dex library needed)
    private fun parseDexStrings(bytes: ByteArray): DexInfo {
        if (bytes.size < 112) return DexInfo(0, emptySet())
        if (bytes[0] != 'd'.code.toByte() || bytes[1] != 'e'.code.toByte() || bytes[2] != 'x'.code.toByte()) {
            return DexInfo(0, emptySet())
        }
        fun leInt(off: Int): Int =
            (bytes[off].toInt() and 0xFF) or
                ((bytes[off + 1].toInt() and 0xFF) shl 8) or
                ((bytes[off + 2].toInt() and 0xFF) shl 16) or
                ((bytes[off + 3].toInt() and 0xFF) shl 24)

        val stringIdsSize = leInt(56)
        val stringIdsOff = leInt(60)
        val classDefsSize = leInt(96)
        val descriptors = HashSet<String>()

        for (i in 0 until stringIdsSize) {
            val idOff = stringIdsOff + i * 4
            if (idOff + 4 > bytes.size) break
            var pos = leInt(idOff)
            if (pos <= 0 || pos >= bytes.size) continue
            // skip uleb128 utf16 length prefix
            var shift = 0
            while (pos < bytes.size && shift < 35) {
                val b = bytes[pos++].toInt() and 0xFF
                if ((b and 0x80) == 0) break
                shift += 7
            }
            val start = pos
            if (start >= bytes.size || bytes[start] != 'L'.code.toByte()) continue
            var end = start
            while (end < bytes.size && bytes[end] != 0.toByte()) end++
            if (end > start + 2 && bytes[end - 1] == ';'.code.toByte()) {
                descriptors += String(bytes, start, end - start, Charsets.UTF_8)
            }
        }
        return DexInfo(classDefsSize, descriptors)
    }

    private fun extractCertSha256(pkcs7: ByteArray): String? = runCatching {
        val factory = CertificateFactory.getInstance("X.509")
        val certs = factory.generateCertificates(pkcs7.inputStream())
        val first = certs.firstOrNull() ?: return@runCatching null
        MochyPackCodec.sha256Hex(first.encoded)
    }.getOrNull()
}
