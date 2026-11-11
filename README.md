# Folio — your little PDF library

A native, offline Android PDF reader built with Kotlin and Jetpack Compose. Add a PDF once and open it from your own library whenever you want to read.

## Features

- Create subject folders (Science, Maths, Art, or any name), rename them, move PDFs between folders, and import directly into an open folder. Deleting a folder keeps its PDFs and notes in All PDFs.
- Light, dark, and device-default appearance, saved between launches. Dark mode also renders PDF pages in dark colours; Reader options can restore original PDF colours for photos or illustrations.
- Full-screen reading hides system bars and annotation toolbars. Tap the sides of a page to change pages, tap the centre to toggle page controls, and use the top-right exit button or Android Back to return.
- Open PDFs from WhatsApp, Files, email, and other apps using Android's PDF Open with menu, or share one or multiple PDFs to Folio. Shared PDFs are copied into the library and opened automatically; already imported PDFs reopen without duplicates.

- Import one or several PDFs with Android's document picker. Folio keeps a private copy, so moving or deleting the downloaded original does not break your library.
- PDF cover previews, title search, favourites, sorting, grid/list layouts, rename, and removal.
- Resume the last page, jump to a page, bookmark pages, and pinch to zoom/pan.
- Drag-to-highlight in four colours, freehand drawing, and editable notes pinned to a page.
- Browse all annotations in My notes, or manage annotations within the reader. Undo removes the most recent annotation on the current page.
- Automatically save annotations and reading progress without modifying the PDF or exporting new versions.
- Includes a removable, original three-page Folio guide for trying the reader.

## Using the new controls

Tap **Add PDF** at the top to import. Use **Your folders → New folder** to create a subject section, or a PDF's options menu → **Move to folder** to organise existing documents. Tap the sun/moon button for **Appearance**. In the reader, use the expand icon for full-screen mode, and the three-dot menu for appearance and annotations. The bottom bar is compact and kept above Android's system navigation area.

After installing the update, choose **Folio** when another app offers **Open with** for a PDF. If an app uses its own built-in preview or another viewer is already the default, use its **Share** action and choose Folio. The sender must provide a readable PDF attachment.

## Run

Open this folder in Android Studio and run the `app` configuration, or:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires Android 9 (API 28) or newer. The project compiles with Android SDK 37.0 and uses the Gradle-managed Java 21 toolchain. `local.properties` should point to your local Android SDK.

## Verification

```sh
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The instrumented tests cover rendering, importing a durable private copy, invalid PDF cleanup, annotation/progress persistence, preserving PDF bytes, removal, corrupt-index protection, legacy-library migration, folders and appearance persistence, full-screen recreation and system-bar visibility, Android intent resolution, importing through an external test-APK content provider, duplicate-free reopening, and reader interactions including activity recreation.

## Storage and scope

PDF files are kept under the app's private `files/library` directory. A versioned-in-memory, serial write queue saves a versioned JSON document containing folders, appearance, library metadata and normalized annotation coordinates to `library.json` using Android's `AtomicFile`. The Android `PdfRenderer` renders pages on an IO dispatcher; only the active reader page and visible cover previews are loaded.

Annotations are an app-local overlay, so they work without generating another PDF. Highlights are rectangular regions drawn by dragging, not selectable PDF text. Notes and marks are not embedded in PDFs for other readers. Annotation tools operate at fit-page scale; Read mode supports zooming and panning. Password-protected PDFs must be unlocked before importing. Imports are limited to 300 MB per file.

There is no account, server, analytics, or network permission. Clearing app data or uninstalling may remove the library and annotations; retain original PDFs as a backup. Removing a book removes its private PDF copy and annotations after the updated index has been saved successfully, without touching the source download.
