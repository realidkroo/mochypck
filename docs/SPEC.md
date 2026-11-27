# `.mochypck` Binary Module Pack Specification & BYOS Architecture

Mochy runs **100% locally offline by default (BYOS — Bring Your Own Source)**. Core Mochy ships with zero built-in YouTube / InnerTube / InnerTubeX backends, zero built-in online search or catalog providers, zero built-in online lyrics providers, zero built-in online cover / artist-image / canvas / artist-video providers, and zero built-in song/album metadata scrapers.

Online capabilities are supplied dynamically at runtime by compiled **`.mochypck`** module packs (or individual web/script/code modules).

---

## 1. Binary Container Format (`MPCK` v1)

A `.mochypck` file is a **compiled binary container** designed for instant O(1) metadata inspection, full cryptographic verification, and deterministic payload extraction:

```
┌──────────────────────────────────────────────────────────────────────┐
│ 1. Fixed 80-byte Binary Header (Big-Endian)                          │
│    Offset  0..3   : Magic bytes "MPCK" (0x4D 0x50 0x43 0x4B)         │
│    Offset  4..5   : Format version (uint16 = 1)                      │
│    Offset  6..7   : Flags bitmask (uint16)                           │
│                     bit 0 (0x0001) = FLAG_SIGNED                     │
│                     bit 1 (0x0002) = FLAG_HAS_NATIVE_LIBS            │
│                     bit 2 (0x0004) = FLAG_HAS_ASSETS                 │
│                     bit 3 (0x0008) = FLAG_ZLIB_INDEX                 │
│    Offset  8..11  : Module API version (uint32 = 4)                  │
│    Offset 12..15  : Compiled Index byte length N (uint32)            │
│    Offset 16..47  : Payload SHA-256 (32 raw bytes)                   │
│    Offset 48..79  : Compiled Index SHA-256 (32 raw bytes)            │
├──────────────────────────────────────────────────────────────────────┤
│ 2. Compiled Binary PackIndex (N bytes, DEFLATE-compressed if flag 3) │
│    - Index format version (1) + pack id, name, version, versionCode  │
│    - Author, description, sourceUrl, minAppVersion, builtAtEpochMs   │
│    - 64-bit aggregated capability bitmask                            │
│    - Code payload path ("code/providers.apk"), code SHA-256, ABIs    │
│    - Icon/banner paths, signer X.509 certificate SHA-256             │
│    - Compiled Provider table (id, name, entryClass, capabilities,    │
│      priority, choices, fields, defaults)                            │
│    - Per-file SHA-256 integrity table for every ZIP payload entry    │
├──────────────────────────────────────────────────────────────────────┤
│ 3. Deterministic ZIP Payload (bytes [80 + N .. EOF))                 │
│    Sorted entries with fixed epoch timestamps:                       │
│    - manifest.json          (human-readable mirror of PackIndex)     │
│    - code/providers.apk     (compiled DEX + optional JNI .so libs)   │
│    - assets/...             (pack/provider icons, banners, shaders)  │
└──────────────────────────────────────────────────────────────────────┘
```

### Why a compiled container instead of a raw ZIP?

1. **O(1) inspection**: Mochy reads only `80 + N` bytes (typically ~1–2 KB) to display pack name, capabilities, provider list, and trust state without scanning or inflating the multi-megabyte DEX payload.
2. **Pre-verified integrity**: Every file in the payload has a pre-computed SHA-256 in the compiled index, and both the index and the entire ZIP payload are hashed in the 80-byte header. Any byte modification fails verification before a single class is loaded.
3. **Compile-time validation**: The `mochypck` compiler inspects raw DEX headers and string tables inside `code/providers.apk` at build time to guarantee every declared `entryClass` and `com.mochyy.mochymusic.module.bridge.ModuleSide` actually exist before emitting the `.mochypck`.
4. **Single shared runtime**: Multiple providers bundled in one `.mochypck` share one `DexClassLoader`, one OkHttp connection pool, and one InnerTube session.

---

## 2. Capability Bitmask

Defined in `:pack-format` (`PackCapability`):

| Bit | Wire Name | Label | Description |
|---:|---|---|---|
| 0 | `audio` | Audio streams | Resolves playable audio streams |
| 1 | `catalog` | Catalog & browse | Full catalog gateway (`CatalogModule`) |
| 2 | `search` | Search | Online search & autocomplete suggestions |
| 3 | `home` | Home feed | Home shelves, explore, charts, moods/genres |
| 4 | `artist` | Artist pages | Artist pages, discography, items |
| 5 | `album` | Album pages | Album pages & tracklists |
| 6 | `playlist` | Playlists | Remote playlists & continuations |
| 7 | `radio` | Radio & up next | Watch queues & related tracks |
| 8 | `video` | Music videos | Adaptive video stream resolution |
| 9 | `lyrics` | Lyrics | Single (`LyricsModule`) or multi-source (`MultiLyricsModule`) lyrics |
| 10 | `metadata` | Song info | Song stories, credits, album notes, artist genres |
| 11 | `cover` | Covers | Album/track artwork search |
| 12 | `canvas` | Animated covers | Looping animated artwork & backgrounds |
| 13 | `artist_image` | Artist photos | Artist profile photo lookup |
| 14 | `artist_video` | Artist videos | Artist video loops |
| 15 | `account_sync` | Account sync | Library sync, likes, playlists, watchtime reporting |
| 16 | `look` | Looks & skins | Visual themes / players |
| 17 | `audio_fx` | Audio effects | Mixer DSP effects |

---

## 3. Trust Model & Runtime Isolation

Mochy supports **both official and community `.mochypck` packs** with transparent verification:

1. **Cryptographic verification (`MochyPackReader.verify`)**:
   - Checks `MPCK` magic, format version (`v1`), and module API version (`v4`).
   - Verifies header `indexSha256`, `payloadSha256`, every individual payload file's SHA-256 and byte length, and blocks path traversal (`..`, absolute paths, oversized archives).
2. **Official vs Community trust**:
   - **Verified official pack**: `code/providers.apk` is signed with the exact same certificate as this Mochy build (`CodeModules.signedLikeMochy`). Shown with a green **Verified official pack** badge.
   - **Community pack**: Any valid `.mochypck` signed with a third-party key (or unsigned) is allowed after the user explicitly inspects the **Community pack** warning sheet listing capabilities, bundled providers, author, and full SHA-256 fingerprint.
3. **Install-time trust pinning & Android 14–17 compliance**:
   - The approved pack SHA-256 is persisted in private app preferences (`pack_trust_<packId>`). On every app launch, Mochy re-verifies the stored pack file and extracted APK SHA-256 before loading any DEX code.
   - Extracted APKs and unpacked `.so` libraries are marked read-only (`setReadOnly()`) prior to `DexClassLoader` initialization (Android 14–17 dynamic code loading enforcement).
   - Every provider runs behind `BridgeOnlyParent` + `BridgedModule` (JSON wire protocol), so pack DEX cannot access Mochy's Room database, `SecretVault`, or internal UI classes.

---

## 4. Core BYOS Gateways (API v4)

Mochy core delegates online operations exclusively through provider-agnostic gateways in `com.mochyy.mochymusic.backend`:

- `CatalogBackend` -> active `CatalogModule` (search, home, explore, charts, moods, artists, albums, playlists, radio/next, audio & video streams, captions, account sync).
- `LyricsProviderRegistry` -> active `LyricsModule` and `MultiLyricsModule` (`PackLyricsProvider`).
- `CoverBackend` -> active `CoverModule` (album/track covers + artist profile photos).
- `CanvasBackend` -> active `CanvasModule` (animated covers, artist editorial/wordmark headers, artist video loops).
- `ExtendedMetadataBackend` -> active `ExtendedMetadataModule` (song facts/credits, album about notes, artist genres).

When no pack is active, every gateway is empty and Mochy operates cleanly as an offline local music player.

---

## 5. Compiler CLI (`mochypck`)

The CLI lives in `:compiler`. Build it with `./gradlew :compiler:installDist`, or run any command through `./gradlew :compiler:run --args="..."`.

```bash
# Make a folder with a starter pack.json and an assets/ folder
mochypck init my-pack

# Compile a pack.json + built provider APK into a verified .mochypck
mochypck compile --spec my-pack/pack.json --apk app-release.apk --out my-pack.mochypck

# Inspect header & compiled index in O(1)
mochypck inspect my-pack.mochypck

# Full cryptographic SHA-256 verification of header, index, payload, and every file
mochypck verify my-pack.mochypck

# Extract payload contents safely into a directory
mochypck extract my-pack.mochypck --out ./unpacked
```

### Building `mochy-essentials.mochypck`

The reference pack bundling all extracted providers (InnerTubeX + Legacy InnerTube with adaptive client health, 14 lyrics providers, Apple Music / Deezer / YouTube covers & artist images, Apple Music / Monochrome-Tidal / Spotify canvas + artist videos, and Musixmatch / Genius / Wikipedia / Apple Music metadata) lives in its own project, `mochy-essentials`, which builds against this SDK. From that project:

```bash
./gradlew :apk:compileMochyPackDebug
# Output: build/outputs/mochy-essentials-debug.mochypck
```

---

## 6. Algorithm Improvements (BYOS v4)

- **Multi-signal `TrackMatcher` (`:api`)**: Unicode NFKD diacritic folding, blended token F1 + character bigram Dice + Levenshtein title similarity, multi-artist splitting with lead-artist weighting, album match bonus, Gaussian duration decay, and expanded edition/version conflict guards (`instrumental`, `sped up`, `slowed`, `karaoke`, `nightcore`, `8d`, `acapella`, `live`, `remix`, `acoustic`).
- **Adaptive Legacy InnerTube Client Health (`AdaptiveClientHealth` in `mochy-essentials`)**: EWMA latency + success-rate scoring combined with exponential-backoff circuit breaking (including heightened penalties for bot-check `LOGIN_REQUIRED` responses) so failing clients are bypassed quickly while healthy clients rise to the front automatically.
- **Unified Cover & Genre Matching**: Cover and metadata providers inside `mochy-essentials` rank candidates via `TrackMatcher` rather than naive string prefix heuristics.
