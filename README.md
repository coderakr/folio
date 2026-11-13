# Folio — your little PDF library

A native, offline Android PDF reader built with Kotlin and Jetpack Compose. Add a PDF once and open it from your own library whenever you want to read.

## Features

- Create nested subject folders at any depth (for example Science → Physics → Mechanics), with breadcrumbs and parent navigation. Move PDFs to any section. Deleting a folder and its subfolders keeps all their PDFs and annotations in All PDFs.
- Import a device folder with all its subfolders and PDFs, preserving empty folders too. Progress, Stop, and a completion report handle large imports and unreadable files. Import again to add new PDFs while skipping identical PDFs already in the same section.
- One-tap light/dark appearance switching, saved between launches (new installs initially follow the device setting). Dark mode also renders PDF pages in dark colours; Reader options can restore original PDF colours for photos or illustrations.
- Full-screen reading hides system bars and annotation toolbars. The top-right rotate button turns the reading view clockwise through 0°, 90°, 180°, and 270° in both swipe and scroll modes, with annotations kept aligned. Rotation applies to all pages during the current reading session, survives activity recreation, and returns to an upright view outside full screen. PDF files are unchanged. Tap the sides of a page to change pages, tap the centre to toggle page controls, and use the top-right exit button or Android Back to return.
- Open PDFs from WhatsApp, Files, email, and other apps using Android's PDF Open with menu, or share one or multiple PDFs to Folio. Shared PDFs are copied into the library and opened automatically; already imported PDFs reopen without duplicates.

- Import one or several PDFs with Android's document picker. Folio keeps a private copy, so moving or deleting the downloaded original does not break your library.
- PDF cover previews, title search, favourites, sorting, grid/list layouts, rename, and removal.
- Swipe left/right between pages, or choose continuous vertical scrolling from Reader options. Both work in full screen and display saved annotations.
- Resume the last page, jump to a page, bookmark pages, and pinch to zoom/pan in page view. A bottom page slider lets you jump anywhere in a document in swipe, scroll, and full-screen modes. Drag to preview a page number, then release to navigate; tapping the track also jumps. Tap the page counter for an exact page number.
- Drag-to-highlight in four colours, freehand drawing, and editable notes pinned to a page.
- Browse all annotations in My notes, or manage annotations within the reader. Undo removes the most recent annotation on the current page.
- Automatically save annotations and reading progress without modifying the PDF or exporting new versions.
- Includes a removable, original three-page Folio guide for trying the reader.

## Using the new controls

Tap **Add → Add PDFs** or **Add → Import folder**. Open a section and choose **New folder** to create a subfolder, or import a directory directly into that section. Use a PDF's options menu → **Move to folder** to organise existing documents. Folder paths distinguish similarly named sections. Use the search field in the top bar to find PDFs or notes. Tap the sun/moon **Appearance** button to switch immediately between light and dark mode. Use the grid/list button beside **Your folders** or **Subfolders** to choose how folders are displayed; PDF layout has its own toggle. In the reader, use the expand icon for full-screen mode, and the three-dot menu to toggle appearance or browse annotations. Choose **Reader options → Scroll pages** for vertical reading, or **Swipe pages** to return to page view. Selecting Highlight, Draw, or Note from scroll mode opens the current page for annotation. The bottom bar is compact and kept above Android's system navigation area.

Android’s folder picker controls which locations are selectable; on Android 11 and later, storage roots and Downloads itself cannot be selected. Choose a subfolder inside Downloads or another accessible folder. Only PDFs are copied; non-PDF files are ignored. Stopping an import keeps completed copies, and the original folder is untouched. Imports continue through activity recreation while the app process remains alive.

After installing the update, choose **Folio** when another app offers **Open with** for a PDF. If an app uses its own built-in preview or another viewer is already the default, use its **Share** action and choose Folio. The sender must provide a readable PDF attachment.

## Run

Open this folder in Android Studio and run the `app` configuration, or:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires Android 9 (API 28) or newer. The project compiles with Android SDK 37.0 and uses the Gradle-managed Java 21 toolchain. `local.properties` should point to your local Android SDK.

## APK releases with GitHub Actions

[The workflow](.github/workflows/release.yml) runs on pushes to `main` and version tags, with two build paths:

- **Push to `main`:** runs JVM tests and debug lint, builds a debug APK, and uploads it under **Actions → Build and release APK → completed run → Artifacts → folio-debug-<run number>**. No signing secrets are required. This does not publish a GitHub Release.
- **Push a version tag such as `v1.5`:** builds a signed release APK, runs release lint and JVM tests, verifies its signature, and publishes it with a SHA-256 checksum to GitHub Releases. This requires the four signing secrets below.

You can also use **Actions → Build and release APK → Run workflow**. Leave the tag blank to build a debug APK from the selected branch, or supply an existing version tag to publish its signed release. The workflow file must be on the default branch for the manual-run menu.

### One-time signing setup

Keep a permanent release keystore outside the repository. If you already have a signing key for a distributed Folio release, reuse it. Otherwise create one (the command prompts for passwords):

```sh
mkdir -p "$HOME/.android/folio-signing"
keytool -genkeypair -v -keystore "$HOME/.android/folio-signing/folio-release.jks" \
  -alias folio -keyalg RSA -keysize 3072 -validity 10000
```

In **GitHub repository → Settings → Secrets and variables → Actions**, add:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded contents of the keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing alias, e.g. `folio` |
| `ANDROID_KEY_PASSWORD` | Key password (usually the same as the keystore password for PKCS12) |

To prepare the keystore value for the GitHub website, write its Base64 encoding to a local file and paste that file's contents into the `ANDROID_KEYSTORE_BASE64` secret:

```sh
base64 < "$HOME/.android/folio-signing/folio-release.jks" | tr -d '\n' > "$HOME/.android/folio-signing/folio-release.base64"
```

Alternatively, with the GitHub CLI authenticated, upload the keystore without printing its contents:

```sh
base64 < "$HOME/.android/folio-signing/folio-release.jks" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64
```

Back up the keystore and passwords securely; future updates must use the same signing key. An existing debug APK uses a different certificate and cannot be updated directly with this release APK. Do not uninstall a library you want to keep: uninstalling can remove its PDFs and annotations. Use the same signing key as the installed app when continuity is required. See [Android app signing](https://developer.android.com/studio/publish/app-signing).

### Publish a version

Set `versionName` and increase `versionCode` in `app/build.gradle.kts`, commit the changes including the workflow, then push a matching tag. The current version is **1.5** (`versionCode = 6`):

```sh
git push origin HEAD
git tag v1.5
git push origin v1.5
```

The release will contain `folio-v1.5.apk` and `folio-v1.5.apk.sha256`. Download both into one directory and run `sha256sum --check folio-v1.5.apk.sha256` to verify the download. The tag must match `versionName`; mismatches or missing signing secrets fail the workflow. The built-in `GITHUB_TOKEN` publishes the release; no personal access token is needed. Repository/organisation policy must allow the publishing job's `contents: write` permission.

Publishing uses a draft until both files are uploaded. A retry can finish an interrupted draft; an already published release is left unchanged. Publish a new version tag for subsequent builds. Signed APKs are also retained as Actions artifacts for 14 days. Signing secrets and the decoded keystore are excluded from artifacts; the key is removed after the build, and signing builds disable Gradle's configuration cache.

### If a workflow fails or does not start

Commit and push this workflow change to `main` to start a debug build. Re-running an older failed job uses its older workflow definition; start a new run after pushing the fix.

`Missing repository Actions secret: ANDROID_KEYSTORE_BASE64` means the release signing configuration is incomplete. Add **all four repository secrets**, not repository variables, under **Settings → Secrets and variables → Actions → New repository secret**. See [GitHub's secrets instructions](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets). A debug build from `main` remains available without them.

The tag must also match the app version: the code currently has `versionName = "1.5"`, so a `v1.2` release run will fail the version check even after signing is configured. Commit the workflow fix, then create and push `v1.5` (or increment the app version and push its corresponding new tag). Keep existing published tags unchanged.

Local debug builds need no secrets. Local release builds are unsigned unless `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` are supplied as environment variables; use `--no-configuration-cache` when building with signing credentials.

## Verification

```sh
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The instrumented tests cover rendering, importing a durable private copy, invalid PDF cleanup, annotation/progress persistence, preserving PDF bytes, removal, corrupt-index protection, legacy-library migration, folders and appearance persistence, full-screen recreation and system-bar visibility, Android intent resolution, importing through an external test-APK content provider, duplicate-free reopening, reader interactions including activity recreation, recursive folder imports through a synthetic document tree, repeated-import deduplication, nested navigation and persistence, partial-copy cancellation cleanup, folder-schema migration, swipe/scroll reading modes, toolbar search, nested-folder view switching, direct appearance toggling, rotation with aligned annotations, slider preview/commit behaviour, accessible page jumps, single-page slider handling, and slider navigation/persistence in normal and full-screen reading modes.

## Storage and scope

PDF files are kept under the app's private `files/library` directory. A versioned-in-memory, serial write queue saves a versioned JSON document containing folders, appearance, library metadata and normalized annotation coordinates to `library.json` using Android's `AtomicFile`. The Android `PdfRenderer` renders pages on an IO dispatcher; reader pages and cover previews are loaded on demand; continuous reading uses a lazy list of pages.

Annotations are an app-local overlay, so they work without generating another PDF. Highlights are rectangular regions drawn by dragging, not selectable PDF text. Notes and marks are not embedded in PDFs for other readers. Annotation tools operate at fit-page scale; Read mode in page view supports zooming and panning. Continuous scroll displays marks and allows editing existing notes; choosing an annotation tool switches to page view. Password-protected PDFs must be unlocked before importing. Imports are limited to 300 MB per file.

There is no account, server, analytics, or network permission. Clearing app data or uninstalling may remove the library and annotations; retain original PDFs as a backup. Removing a book removes its private PDF copy and annotations after the updated index has been saved successfully, without touching the source download.
