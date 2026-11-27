# mochypck

Build your own source packs for **Mochy Music**.

Mochy plays your local music on its own. Everything that needs the internet, like search, streaming,
lyrics, covers, animated canvas and song info, comes from a **`.mochypck`** pack you add. This repo has
everything you need to make one: the Module API you write your providers against, the bridge that
lets Mochy run them safely, and the `mochypck` command line tool that turns your code into a pack.

You don't need a copy of Mochy's source to use it.

> Packs load in Mochy builds that have **Settings → Modules → Module packs (.mochypck)**. If you
> don't see that row, your Mochy doesn't support packs yet.

---

## What's in here

| Folder | What it's for |
|---|---|
| [`api/`](api) | The interfaces your providers implement (`CatalogModule`, `SourceModule`, `LyricsModule`, `MultiLyricsModule`, `CoverModule`, `CanvasModule`, `MetadataModule`, `ExtendedMetadataModule`), the shared catalog models, and `TrackMatcher` for matching songs across services. |
| [`bridge/`](bridge) | `ModuleSide` and the JSON bridge. Your code talks to Mochy only through this, so a different Kotlin version on your side can't break the app. |
| [`pack-format/`](pack-format) | Reads, writes and verifies the `.mochypck` file itself. |
| [`compiler/`](compiler) | The `mochypck` CLI: `init`, `compile`, `inspect`, `verify` and `extract`. |
| [`examples/starter-pack/`](examples/starter-pack) | A tiny lyrics and covers provider to copy from. |
| [`docs/SPEC.md`](docs/SPEC.md) | The byte layout of the file, every capability, and how Mochy checks a pack. |

---

## Get the CLI

You need **JDK 21**.

```bash
./gradlew :compiler:installDist
# the tool is now at compiler/build/install/mochypck/bin/mochypck

# or run it straight from Gradle without installing
./gradlew :compiler:run --args="--help"
```

The examples below call it `mochypck`. Add `compiler/build/install/mochypck/bin` to your `PATH`, or
use the full path.

### Commands

```bash
# make a folder with a starter pack.json and an assets/ folder
mochypck init my-pack

# turn your pack.json and your built APK into a .mochypck
mochypck compile --spec my-pack/pack.json --apk path/to/providers.apk --out my-pack.mochypck

# show what's inside: name, capabilities, providers, files (only reads the first couple of KB)
mochypck inspect my-pack.mochypck

# check every hash in the pack, the same way Mochy does before it loads anything
mochypck verify my-pack.mochypck

# unpack it into a folder, after checking it
mochypck extract my-pack.mochypck --out ./unpacked
```

---

## Make your own pack

### 1. Make an Android app module for your code

A pack's code is an ordinary Android APK. Mochy never installs it as an app. It only loads the code
out of it, so the APK is just a convenient way to get your Kotlin compiled to DEX.

### 2. Add the SDK

From JitPack:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    // main-SNAPSHOT follows the latest commit; swap it for a release tag once there is one
    implementation("com.github.realidkroo.mochypck:api:main-SNAPSHOT")
    implementation("com.github.realidkroo.mochypck:bridge:main-SNAPSHOT")
}
```

Or point Gradle at a local checkout with `includeBuild("../mochypck")` in your `settings.gradle.kts`.

### 3. Write your providers

Implement whichever interfaces fit what you're adding. One pack can hold several providers, and they
share one class loader and one connection pool.

| You want to add | Implement | Capabilities in `pack.json` |
|---|---|---|
| Search, Home, artist, album and playlist pages, radio, streams, account sync | `CatalogModule` | `catalog`, `search`, `home`, `artist`, `album`, `playlist`, `radio`, `audio`, `video`, `account_sync` |
| Just a place to play songs from | `SourceModule` | `audio` |
| Lyrics from one source, or many | `LyricsModule` / `MultiLyricsModule` | `lyrics` |
| Album covers and artist photos | `CoverModule` | `cover`, `artist_image` |
| Animated covers, artist banners and video loops | `CanvasModule` | `canvas`, `artist_video` |
| Song stories, credits, album notes, genres | `MetadataModule` / `ExtendedMetadataModule` | `metadata` |

The [starter pack](examples/starter-pack/StarterProvider.kt) is a working lyrics and covers provider
in about 50 lines. The full list of capabilities is in [docs/SPEC.md](docs/SPEC.md#2-capability-bitmask).

### 4. Describe the pack in `pack.json`

```json
{
  "id": "example.starter",
  "name": "Starter Source Pack",
  "version": "1.0.0",
  "versionCode": 1,
  "author": "your-name",
  "minAppVersion": "9.0.0",
  "assetsDir": "assets",
  "providers": [
    {
      "id": "example.starter_lyrics",
      "name": "Starter Lyrics & Covers",
      "capabilities": ["lyrics", "cover"],
      "entryClass": "com.example.starterpack.StarterProvider"
    }
  ]
}
```

`entryClass` is the full class name of your provider. Bump `versionCode` every time you ship an update.
See [examples/starter-pack/pack.json](examples/starter-pack/pack.json) for every field.

### 5. Build the APK and compile the pack

- Keep `isMinifyEnabled = false`. If you do turn on R8, keep your `entryClass` names and
  `com.mochyy.mochymusic.module.bridge.ModuleSide`, or Mochy won't find them.
- Turn on V1 and V2 APK signing if you want your signing certificate shown in the pack.

```bash
mochypck compile \
  --spec pack.json \
  --apk build/outputs/apk/release/app-release.apk \
  --out my-source-pack.mochypck
```

Before it writes anything, the compiler checks that:

- your APK's DEX contains `ModuleSide` and every `entryClass` you listed
- ids are lowercase (`a-z`, `0-9`, `.`, `_`, `-`), no two providers share one, and every
  capability name is one Mochy knows
- any native `.so` libraries and the signing certificate are recorded in the header

Then it reads the finished pack back and verifies it, so a pack that comes out of `compile` is one
Mochy will accept.

### 6. Share it

Attach the `.mochypck` to a **GitHub Release**. People can then add it in Mochy by pasting your
repository's link, or by picking the file under **Settings → Modules → Module packs**.

---

## Signing and trust

- **Community packs.** Anyone can build and share a pack, signed with their own key or not signed at
  all. Before adding one, Mochy checks every hash and shows a **Community pack** sheet with its
  capabilities, providers, author and SHA-256 fingerprint, so people can decide whether they trust it.
- **Official packs.** Packs signed with Mochy's own release key get a **Verified official pack** badge.
- **Isolation.** Pack code only sees Android, Java and Mochy's bridge. It can't reach Mochy's
  database, saved logins or UI.

---

## The file format

[docs/SPEC.md](docs/SPEC.md) explains the 80-byte header, the compressed index, every capability bit,
and exactly what Mochy checks before it runs a pack.

---

## License

GPL-3.0, see [LICENSE](LICENSE). Made by [realidkroo](https://github.com/realidkroo) for
[Mochy Music](https://github.com/realidkroo/mochy-music).
