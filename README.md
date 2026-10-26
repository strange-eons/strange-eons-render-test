# strange-eons-render-test

A Gradle plugin that renders Strange Eons components with a **locally built
Strange Eons** and compares every sheet, pixel for pixel, against checked-in
reference images ("masters").

The intended use is refactoring a game plug-in safely, for example moving an
existing DIY plug-in onto conventional components: record masters with the
current plug-in, refactor, and `./gradlew check` tells you exactly which
sheets changed.

## How it works

No changes to Strange Eons are needed. The plugin drives SE through options
it already has:

1. **`buildStrangeEons`** runs `:strange-eons-app:installDist` in your SE
   checkout using the checkout's own Gradle wrapper.
2. **`packagePluginFolder`** zips an SE plug-in project folder into a bundle
   the way SE's *Make Bundle* command does: web-safe header, hidden files,
   `seproject`, `*.ts.js` and VCS folders left out. The bundle extension is
   guessed the way SE guesses it (`.seext`, `.seplugin`, `.selibrary`).
3. **`renderComponents`** starts SE once, hidden:
   `--plugintest <bundles> --xDisablePluginLoading --run render.js`, with
   `STRANGE_EONS_USER_DIR` pointing at a fresh, empty user folder.
   The script renders saved `.eon` files and/or one default instance of every
   class-map entry of the games you name, and writes one PNG per sheet to
   `build/render-test/actual`.
4. **`verifyRenders`** (part of `check`) compares them with the masters for
   the platform the build runs on, `src/renderTest/masters/<platform>/`.
   Any differing pixel, size change, missing master or missing render fails
   the build. Magenta-on-grey diff images go to `build/render-test/diffs`.
5. **`updateRenderMasters`** replaces the current platform's masters with
   the current renders. Other platforms' masters are left alone.

Each run uses a fresh SE user folder (via `STRANGE_EONS_USER_DIR`), so your
installed plug-ins and preferences never affect the result. Set
`userSettings` to render with specific preferences, e.g. a plug-in option:
`userSettings.put("my-plugin-option", "yes")`.

## Usage

`settings.gradle.kts` (until the plugin is published):

```kotlin
pluginManagement {
    includeBuild("../strange-eons-render-test")
}
```

`build.gradle.kts`:

```kotlin
plugins {
    id("org.strangeeons.render-test")
}

strangeEonsRenderTest {
    // An SE plug-in project folder (holds eons-plugin and resources/) ...
    pluginFolder = file("MyGame/MyGame")
    // ... and/or bundles built elsewhere, plus every library they require.
    // bundles.from(tasks.named("shadowJar"))

    // Render one default instance of each class-map entry for these games.
    classMapGames.add("MYGAME")
    // Render saved components too (searched recursively).
    components = file("src/renderTest/components")
}
```

```
./gradlew updateRenderMasters   # first time, or after an intended change
./gradlew check                 # verify
```

| Setting | Default |
|---|---|
| `strangeEonsDir` | `-PstrangeEonsDir`, else `../strange-eons` |
| `pluginFolder`, `pluginBundleName` | none; name = folder name + guessed extension |
| `bundles` | empty |
| `components` | none |
| `classMapGames` | empty |
| `masters` | `src/renderTest/masters` (root; one subfolder per platform) |
| `platform` | `-PrenderTestPlatform`, else `linux`, `macos` or `windows` from the OS |
| `resolution` | 150 ppi |
| `gameLanguage`, `uiLanguage` | `en` |
| `launcher` | Java 25 toolchain (must be a full JDK, not a headless-only one) |
| `jvmArgs` | none, added to the two flags SE's own `run` task passes |
| `userSettings` | empty: SE preferences written to the fresh user folder |
| `renderBacks` | `true`; `false` skips DIY back faces (odd-numbered sheets) |

Output names: `components/<path>-<sheet>.png` for `.eon` files and
`classmap/<GAME>/<entry key>-<sheet>.png` for class-map defaults. Sheets
keep their index when backs are skipped, so fronts are `-0`, `-2` and so on.

## Masters per platform

Text and antialiasing differ between operating systems, JDK builds and
FreeType versions, so each platform keeps its own masters:

```
src/renderTest/masters/
  linux/
    environment.properties
    classmap/MYGAME/MYGAME-Asset-0.png
    ...
  macos/
    environment.properties
    ...
```

A run reads and writes only the folder for its platform. By default that is
picked from the OS: `linux`, `macos` or `windows`. Record each set by running
`updateRenderMasters` on that OS. If a run finds no masters for its platform,
it fails, names the platforms that do have masters, and says how to create
them. It never falls back to another platform's images.

Environments on the same OS can still differ, for example a Temurin JDK in CI
and a distribution JDK locally. To keep them apart, name the platform
yourself with `-PrenderTestPlatform=linux-ci` (or `platform = "linux-ci"` in
the build script).

`updateRenderMasters` also writes `environment.properties`: the OS name and
architecture, and the vendor and runtime version of the JDK that rendered
the masters. When `verifyRenders` runs on a different JDK or OS than the one
recorded, it logs a warning listing the differences. If images also differ,
the failure message repeats them, so you can tell an environment change from
a plug-in change.

## Requirements and limits

- **A display.** SE builds its main window even in `--run` mode. On Linux
  without `DISPLAY` the plugin wraps SE in `xvfb-run`, which must be
  installed. macOS and Windows always have a display. A truly headless mode
  needs a separate startup path in SE core.
- **Library bundles must be listed.** SE does not resolve `catalog-requires`
  for bundles passed with `--plugintest`, so add required libraries (for
  example the GraalJS language bundle) to `bundles`.
- **Class-map defaults** cover DIY and compiled entries. Scripted entries
  open an editor and are skipped with a warning: save an `.eon` instead.
- **Java sources** in a plug-in project folder are not compiled (SE's Make
  Bundle compiles them first).
- **Render script is ES5.** It runs on Strange Rhino, which rejects arrow
  functions and similar syntax.
- **Masters are large.** 93 sheets of the AHLCG plug-in at 150 ppi are about
  40 MB. Lower `resolution` or use Git LFS if that matters.

## CI example (GitHub Actions)

Not yet run in CI; adjust repository names and paths.

```yaml
name: render-test
on: [push, pull_request]
jobs:
  render:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
        with: { path: plugin }
      - uses: actions/checkout@v4
        with: { repository: christophercurrie/strange-eons, path: strange-eons }
      - uses: actions/checkout@v4
        with: { repository: christophercurrie/strange-eons-render-test, path: strange-eons-render-test }
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 25 }
      - run: sudo apt-get install -y xvfb
      - run: ./gradlew check
        working-directory: plugin
      - if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: render-diffs
          path: |
            plugin/build/render-test/diffs
            plugin/build/render-test/actual
            plugin/build/render-test/work/*.log
```

Masters for CI are best recorded by CI itself: run `updateRenderMasters`
in a job, upload `src/renderTest/masters/<platform>` as an artifact, and
commit it. Pass `-PrenderTestPlatform=linux-ci` in the workflow if CI's
Linux masters should be kept separate from local Linux runs.

## License

TBD.

## Publishing

Pushes to `main` publish a `-SNAPSHOT` to the Maven Central snapshot
repository. Pushing a `v<version>` tag releases that version to Maven Central.
Both run in the `maven-central` environment, which holds
`MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (a Central Portal user
token), plus `SIGNING_KEY` (ASCII-armored private key) and
`SIGNING_KEY_PASSWORD`.
