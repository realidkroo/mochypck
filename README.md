# `mochypck` — Mochy BYOS Module Pack Compiler & SDK

Standalone compiler, binary container format (`MPCK` v1), and Module API v4 SDK for building **`.mochypck`** provider packs for **Mochy Music (BYOS)**.

Anyone can use `mochypck` to build, verify, and share their own catalog, streaming, lyrics, cover, animated canvas, or metadata packs — no Mochy app checkout required.

---

## Modules in this Repository

| Module | Artifact | Description |
|---|---|---|
| `:api` | `com.github.realidkroo.mochypck:api` | Module API v4 contracts (`CatalogModule`, `SourceModule`, `MultiLyricsModule`, `LyricsModule`, `CoverModule`, `CanvasModule`, `ExtendedMetadataModule`), provider-agnostic catalog DTOs, and multi-signal `TrackMatcher`. |
| `:pack-format` | `com.github.realidkroo.mochypck:pack-format` | `MPCK` v1 binary header codec, DEFLATE-compressed `PackIndex`, 64-bit capability bitmask, cryptographic SHA-256 verifier (`MochyPackReader`), and deterministic writer (`MochyPackWriter`). |
| `:bridge` | `com.github.realidkroo.mochypck:bridge` | `ModuleSide`, `BridgedModule`, `BridgeOnlyParent`, and JSON wire protocol so pack DEX code runs cleanly isolated inside Mochy. |
| `:compiler` | `com.github.realidkroo.mochypck:compiler` | Standalone `mochypck` CLI tool (`init`, `compile`, `inspect`, `verify`, `extract`) with zero-dependency DEX symbol verification and X.509 signing certificate SHA-256 extraction. |

---

## Quickstart (CLI)

Requires **JDK 21**.

```bash
# Build & install the standalone CLI locally
./gradlew :compiler:installDist

# Or run directly via Gradle
./gradlew :compiler:run --args="--help"
```

### Commands

```bash
# 1. Scaffold a new pack.json workspace
mochypck init my-pack

# 2. Compile pack.json + your built provider APK into a verified .mochypck
mochypck compile --spec my-pack/pack.json --apk path/to/providers.apk --out my-pack.mochypck

# 3. Inspect header, capabilities, providers, and file hashes in O(1)
mochypck inspect my-pack.mochypck

# 4. Full cryptographic SHA-256 verification (header, index, payload, every file)
mochypck verify my-pack.mochypck

# 5. Extract verified payload contents safely
mochypck extract my-pack.mochypck --out ./unpacked
```

---

## Building Your Own `.mochypck` Pack

1. Create a standard Android application module (for DEX packaging — it is never installed as an Android app; Mochy loads its DEX behind `BridgeOnlyParent`).
2. Add the SDK dependencies via **JitPack** (or Gradle `includeBuild("../mochypck")` locally):

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.realidkroo.mochypck:api:1.0.0")
    implementation("com.github.realidkroo.mochypck:bridge:1.0.0")
}
```

3. Implement one or more Mochy Module API v4 interfaces (see [examples/starter-pack/StarterProvider.kt](examples/starter-pack/StarterProvider.kt) and [examples/starter-pack/pack.json](examples/starter-pack/pack.json)):
   - `CatalogModule` — search, home, explore, artist/album/playlist pages, radio, audio & video streams, captions, account sync
   - `SourceModule` — standalone audio stream resolver
   - `LyricsModule` / `MultiLyricsModule` — single or bundled multi-source lyrics providers
   - `CoverModule` — album/track covers and artist profile images
   - `CanvasModule` — animated artwork, artist editorial banners/wordmarks, and artist video loops
   - `MetadataModule` / `ExtendedMetadataModule` — song stories, credits, album editorial notes, genres
4. Keep `isMinifyEnabled = false` (or keep your `entryClass` names and `com.mochyy.mochymusic.module.bridge.ModuleSide`), enable V1 + V2 APK signing if you want your certificate SHA-256 embedded in the header, and compile the built APK with `mochypck compile`:

```bash
mochypck compile \
  --spec pack.json \
  --apk build/outputs/apk/release/app-release.apk \
  --out my-source-pack.mochypck
```

The compiler automatically checks that:
- `classes*.dex` contains `com.mochyy.mochymusic.module.bridge.ModuleSide` and every declared `entryClass`
- All declared capability names and provider IDs are valid
- Any bundled JNI `.so` ABIs and X.509 signing certificate SHA-256 are indexed in the binary header
- The emitted `.mochypck` passes full `MochyPackReader.verify(...)` self-verification before returning

---

## Trust Model & Signing

- **Community packs**: Any developer can compile and distribute `.mochypck` packs (signed with your own key or unsigned). Mochy verifies every SHA-256 hash and shows a clear **Community pack** review sheet listing capabilities, providers, author, and SHA-256 fingerprint before installing.
- **Official packs**: Packs signed with Mochy's release keystore additionally receive a **Verified official pack** badge inside Mochy.
- **GitHub Releases**: Attach your `.mochypck` directly to a GitHub Release so Mochy users can import it by pasting your repository URL.

---

## Format Specification

See [docs/SPEC.md](docs/SPEC.md) for the full `MPCK` v1 binary header layout, capability bitmask table, verification rules, and runtime bridge architecture.
