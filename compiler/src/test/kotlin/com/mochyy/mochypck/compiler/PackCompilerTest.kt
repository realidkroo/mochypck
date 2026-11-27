package com.mochyy.mochypck.compiler

import com.mochyy.mochymusic.module.MODULE_API_VERSION
import com.mochyy.mochymusic.pack.MochyPackReader
import com.mochyy.mochymusic.pack.PackCapability
import com.mochyy.mochymusic.pack.PackVerifyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackCompilerTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun compilesAndSelfVerifiesPack() {
        val dir = temp.newFolder("src")
        val assets = File(dir, "assets").apply { mkdirs() }
        File(assets, "icon.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3))

        val spec = File(dir, "pack.json").apply {
            writeText(
                """
                {
                  "id": "mochy.test_pack",
                  "name": "Test Pack",
                  "version": "1.0.0",
                  "versionCode": 1,
                  "description": "Unit test pack",
                  "author": "ryukeicii",
                  "icon": "icon.png",
                  "assetsDir": "assets",
                  "providers": [
                    {
                      "id": "demo_catalog",
                      "name": "Demo Catalog",
                      "capabilities": ["catalog", "search", "audio", "lyrics", "cover", "canvas"],
                      "entryClass": "com.example.DemoCatalogModule"
                    }
                  ]
                }
                """.trimIndent(),
            )
        }

        val apk = File(dir, "demo.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            // minimal valid magic for --no-dex-check disabled test
            zip.write(byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte(), 0, 0, 0, 0))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libdemo.so"))
            zip.write(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
            zip.closeEntry()
        }

        val out = File(temp.root, "demo.mochypck")
        val report = PackCompiler.compile(
            CompileRequest(
                specFile = spec,
                apkFile = apk,
                outputFile = out,
                apiVersion = MODULE_API_VERSION,
                verifyDexSymbols = false,
            ),
        )

        assertEquals(listOf("arm64-v8a"), report.nativeAbis)
        val verified = MochyPackReader.verify(out, expectedApiVersion = MODULE_API_VERSION)
        assertTrue(verified is PackVerifyResult.Verified)
        val ok = verified as PackVerifyResult.Verified
        assertEquals("mochy.test_pack", ok.index.packId)
        assertTrue(PackCapability.has(ok.index.capabilities, PackCapability.CATALOG))
        assertTrue(PackCapability.has(ok.index.capabilities, PackCapability.CANVAS))
    }
}
