// CLI entry point for the mochypck compiler: compile, inspect, verify, extract, and init

package com.mochyy.mochypck.compiler

import com.mochyy.mochymusic.module.MODULE_API_VERSION
import com.mochyy.mochymusic.pack.MochyPackReader
import com.mochyy.mochymusic.pack.PackCapability
import com.mochyy.mochymusic.pack.PackInspectResult
import com.mochyy.mochymusic.pack.PackVerifyResult
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] in setOf("-h", "--help", "help")) {
        printUsage()
        return
    }
    when (args[0]) {
        "compile" -> runCompile(args.drop(1))
        "inspect" -> runInspect(args.drop(1))
        "verify" -> runVerify(args.drop(1))
        "extract" -> runExtract(args.drop(1))
        "init" -> runInit(args.drop(1))
        else -> {
            System.err.println("Unknown command '${args[0]}'")
            printUsage()
            exitProcess(1)
        }
    }
}

private fun runCompile(args: List<String>) {
    val opts = parseFlags(args)
    val spec = opts["--spec"]?.let(::File) ?: fail("Missing --spec <pack.json>")
    val apk = opts["--apk"]?.let(::File) ?: fail("Missing --apk <providers.apk>")
    val out = opts["--out"]?.let(::File) ?: fail("Missing --out <output.mochypck>")
    val skipDexCheck = "--no-dex-check" in args

    val report = PackCompiler.compile(
        CompileRequest(
            specFile = spec,
            apkFile = apk,
            outputFile = out,
            apiVersion = MODULE_API_VERSION,
            verifyDexSymbols = !skipDexCheck,
        ),
    )

    println("Compiled ${report.output.outputFile.path}")
    println("  Pack ID      : ${report.output.index.packId} v${report.output.index.version} (code ${report.output.index.versionCode})")
    println("  API version  : ${report.output.header.moduleApiVersion}")
    println("  Total size   : ${report.output.totalBytes} bytes (index ${report.output.header.indexLength} bytes)")
    println("  DEX classes  : ${report.dexClassCount}")
    println("  Native ABIs  : ${report.nativeAbis.ifEmpty { listOf("none") }.joinToString()}")
    println("  Signed       : ${report.signerSha256 ?: "unsigned (community pack)"}")
    println("  Pack SHA-256 : ${report.output.packSha256}")
    println("  Capabilities : ${report.output.index.capabilitySet.joinToString { it.label }}")
    println("  Providers    : ${report.output.index.providers.size}")
    report.output.index.providers.forEach { p ->
        val caps = PackCapability.fromMask(p.capabilities).joinToString { it.wireName }
        println("    - ${p.id} (${p.displayName}) [$caps] -> ${p.entryClass}")
    }
    report.warnings.forEach { println("  WARNING: $it") }
}

private fun runInspect(args: List<String>) {
    val file = args.firstOrNull()?.let(::File) ?: fail("Missing <file.mochypck>")
    when (val res = MochyPackReader.inspect(file)) {
        is PackInspectResult.Invalid -> fail("Invalid .mochypck: ${res.reason}")
        is PackInspectResult.Valid -> {
            val h = res.header
            val i = res.index
            println("Mochy Pack: ${i.name} (${i.packId}) v${i.version}")
            println("  Author       : ${i.author.ifBlank { "unknown" }}")
            println("  Description  : ${i.description}")
            println("  Format / API : MPCK v${h.formatVersion} / Module API ${h.moduleApiVersion}")
            println("  Index size   : ${h.indexLength} bytes (compressed=${h.isIndexCompressed})")
            println("  Signed       : ${h.isSigned} ${i.signerSha256?.let { "($it)" }.orEmpty()}")
            println("  Payload SHA  : ${h.payloadSha256Hex}")
            println("  Capabilities : ${i.capabilitySet.joinToString { it.label }}")
            println("  Providers (${i.providers.size}):")
            i.providers.forEach { p ->
                println("    - ${p.id}: ${p.displayName} [${PackCapability.fromMask(p.capabilities).joinToString { it.wireName }}]")
            }
            println("  Files (${i.files.size}):")
            i.files.forEach { f ->
                println("    - ${f.path} (${f.sizeBytes} B) sha256=${f.sha256.take(12)}...")
            }
        }
    }
}

private fun runVerify(args: List<String>) {
    val file = args.firstOrNull()?.let(::File) ?: fail("Missing <file.mochypck>")
    when (val res = MochyPackReader.verify(file, expectedApiVersion = MODULE_API_VERSION)) {
        is PackVerifyResult.Invalid -> fail("VERIFICATION FAILED: ${res.reason}")
        is PackVerifyResult.Verified -> {
            println("VERIFIED OK: ${res.index.name} (${res.index.packId} v${res.index.version})")
            println("  Pack SHA-256 : ${res.packSha256}")
            println("  Files checked: ${res.index.files.size}")
        }
    }
}

private fun runExtract(args: List<String>) {
    val file = args.firstOrNull()?.let(::File) ?: fail("Missing <file.mochypck>")
    val opts = parseFlags(args.drop(1))
    val outDir = opts["--out"]?.let(::File) ?: File("${file.nameWithoutExtension}-extracted")
    when (val res = MochyPackReader.verify(file)) {
        is PackVerifyResult.Invalid -> fail("Cannot extract invalid pack: ${res.reason}")
        is PackVerifyResult.Verified -> {
            val extracted = MochyPackReader.extractPayload(file, res.header, outDir)
            println("Extracted ${extracted.size} verified files into ${outDir.path}")
        }
    }
}

private fun runInit(args: List<String>) {
    val dir = File(args.firstOrNull() ?: "my-mochy-pack")
    dir.mkdirs()
    File(dir, "assets").mkdirs()
    val specFile = File(dir, "pack.json")
    if (!specFile.exists()) {
        specFile.writeText(
            """
            {
              "id": "example.my_pack",
              "name": "My Mochy Source Pack",
              "version": "1.0.0",
              "versionCode": 1,
              "description": "Custom BYOS providers compiled into .mochypck",
              "author": "you",
              "assetsDir": "assets",
              "providers": [
                {
                  "id": "my_source",
                  "name": "My Audio & Lyrics Source",
                  "capabilities": ["audio", "lyrics", "cover"],
                  "entryClass": "com.example.pack.MySourceModule"
                }
              ]
            }
            """.trimIndent() + "\n",
        )
    }
    println("Scaffolded new .mochypck workspace in ${dir.path}")
}

private fun parseFlags(args: List<String>): Map<String, String> {
    val out = HashMap<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--") && i + 1 < args.size && !args[i + 1].startsWith("--")) {
            out[a] = args[i + 1]
            i += 2
        } else {
            i++
        }
    }
    return out
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

private fun printUsage() {
    println(
        """
        mochypck — Mochy BYOS module pack compiler (MPCK v1 / Module API $MODULE_API_VERSION)

        Usage:
          mochypck compile --spec <pack.json> --apk <providers.apk> --out <pack.mochypck> [--no-dex-check]
          mochypck inspect <pack.mochypck>
          mochypck verify  <pack.mochypck>
          mochypck extract <pack.mochypck> --out <dir>
          mochypck init    [dir]
        """.trimIndent(),
    )
}
