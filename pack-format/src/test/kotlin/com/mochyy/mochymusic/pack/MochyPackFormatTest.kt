package com.mochyy.mochymusic.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MochyPackFormatTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun roundTripInspectVerifyAndExtract() {
        val out = temp.newFile("essentials.mochypck")
        val fakeApk = "PK\u0003\u0004fake-dex-apk-bytes".toByteArray()
        val fakeIcon = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)

        val draft = MochyPackIndex(
            packId = "mochy.essentials",
            name = "Mochy Essentials",
            version = "1.0.0",
            versionCode = 1,
            description = "All online sources, lyrics, covers and canvas",
            author = "ryukeicii",
            sourceUrl = "https://github.com/realidkroo/Mochy-Music",
            codePath = "code/providers.apk",
            iconPath = "assets/icon.png",
            providers = listOf(
                PackProviderEntry(
                    id = "innertube",
                    name = "YouTube Music",
                    capabilities = PackCapability.maskOf(
                        listOf(
                            PackCapability.AUDIO_SOURCE,
                            PackCapability.CATALOG,
                            PackCapability.SEARCH,
                            PackCapability.HOME,
                            PackCapability.ARTIST,
                            PackCapability.ALBUM,
                            PackCapability.PLAYLIST,
                            PackCapability.RADIO,
                        ),
                    ),
                    entryClass = "com.mochyy.pack.essentials.InnerTubePackModule",
                    choices = listOf(
                        PackChoiceSpec(
                            id = "backend",
                            label = "Stream engine",
                            options = listOf(
                                PackOptionSpec("innertubex", "InnerTubeX", "Modern extractor with legacy fallback"),
                                PackOptionSpec("legacy", "Legacy InnerTube", "Classic multi-client walk"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val built = MochyPackWriter.write(
            outputFile = out,
            moduleApiVersion = 4,
            draftIndex = draft,
            payloadFiles = mapOf(
                "code/providers.apk" to fakeApk,
                "assets/icon.png" to fakeIcon,
            ),
            signed = false,
        )

        val inspected = MochyPackReader.inspect(out)
        assertTrue(inspected is PackInspectResult.Valid)
        val validInspect = inspected as PackInspectResult.Valid
        assertEquals("mochy.essentials", validInspect.index.packId)
        assertEquals(4, validInspect.header.moduleApiVersion)
        assertTrue(PackCapability.has(validInspect.index.capabilities, PackCapability.SEARCH))
        assertTrue(PackCapability.has(validInspect.index.capabilities, PackCapability.AUDIO_SOURCE))

        val verified = MochyPackReader.verify(out, expectedApiVersion = 4)
        assertTrue("Expected verified, got $verified", verified is PackVerifyResult.Verified)
        val ok = verified as PackVerifyResult.Verified
        assertEquals(built.packSha256, ok.packSha256)

        val iconRead = MochyPackReader.readEntryBytes(out, ok.header, "assets/icon.png")
        assertTrue(fakeIcon.contentEquals(iconRead))

        val extractDir = temp.newFolder("extracted")
        val files = MochyPackReader.extractPayload(out, ok.header, extractDir)
        assertTrue(files["code/providers.apk"]!!.readBytes().contentEquals(fakeApk))
    }

    @Test
    fun detectsTamperedPayloadByte() {
        val out = temp.newFile("tamper.mochypck")
        MochyPackWriter.write(
            outputFile = out,
            moduleApiVersion = 4,
            draftIndex = MochyPackIndex(
                packId = "test.pack",
                name = "Test",
                version = "1.0",
                providers = listOf(
                    PackProviderEntry(
                        id = "p1",
                        name = "P1",
                        capabilities = PackCapability.LYRICS.bit,
                        entryClass = "test.P1",
                    ),
                ),
            ),
            payloadFiles = mapOf("code/providers.apk" to "hello world".toByteArray()),
        )

        val bytes = out.readBytes()
        bytes[bytes.lastIndex - 2] = (bytes[bytes.lastIndex - 2].toInt() xor 0xFF).toByte()
        out.writeBytes(bytes)

        val result = MochyPackReader.verify(out, expectedApiVersion = 4)
        assertTrue(result is PackVerifyResult.Invalid)
    }
}
