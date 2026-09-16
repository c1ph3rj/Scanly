# Scanly: a private document scanner built for real life

Scanly is an Android document scanner that I built around a simple idea: scanning a document should feel quick, reliable, and private.

When I scan something, I do not want to create an account, start a subscription, or send a personal document to a remote service just to turn it into a PDF. I wanted a tool that could handle the whole process on the phone, from capturing a page to exporting the finished document.

That is what Scanly is. It helps you capture paper documents, improve their readability, keep pages together in a local library, and export them when you are ready to share them.

## The experience I wanted to create

I designed Scanly around the way people actually scan documents. You open the app because you need to digitise something, not because you want to manage a complicated document system.

The main flow is deliberately straightforward:

1. Start a document from the Home screen, Library, Tools, a widget, or a launcher shortcut.
2. Capture pages with the camera or import images from the gallery.
3. Review the pages and retake only the ones that need another attempt.
4. Crop, rotate, enhance, and adjust the pages in the editor.
5. Arrange the document in the local library and place it in a folder if needed.
6. Export the result as a PDF or image archive, or share it directly.

The app keeps the original captures available, so editing a page does not mean losing the source image. That makes it easier to try a different crop or filter later without starting the scan again.

## Capture that helps without getting in the way

The camera screen gives you guidance while you are lining up a page. Scanly looks at the document edges and provides feedback about framing, lighting, blur, and other conditions that can affect the result.

You can use automatic capture when the page is stable, or use the shutter yourself whenever you want more control. If the camera does not recognise the page correctly, you still have manual options. You can capture the image, run detection again, adjust the crop points, or rotate the page yourself.

For detection, Scanly uses on-device machine learning models to identify document corners. It also has a physical-document check that helps distinguish a real page from a screen or another rectangular object. Book pages and nearby page edges need special handling too, so the capture flow includes logic for those situations rather than treating every rectangle as a document.

## Editing that stays reversible

The editor is where a captured image becomes a useful document page. Scanly corrects perspective, applies document-focused filters that stay connected to the photo you captured, and lets you adjust the result when the automatic processing is not quite right.

You can choose from several looks, including colour, grayscale, clean paper, shadow reduction, receipt, and black-and-white styles. You can also fine-tune brightness, contrast, saturation, and sharpness.

The important part is that these edits are derived from the original capture. Scanly keeps raw images separate from processed images and thumbnails. This gives you a non-destructive workflow and makes it possible to revisit a page later without degrading it each time you edit it.

## A local workspace, not just a camera screen

As the project grew, Scanly became more than a camera screen. I added the surrounding tools that make scanned documents easier to work with:

- Home gives quick access to recent documents, folders, scanning, importing, and creating new items.
- Library lets you search, sort, rename, delete, and organise documents.
- Tools brings scanning, gallery import, QR scanning and generation, and PDF utilities into one place.
- PDF utilities support reading, merging, compressing, password protection, and watermarking.
- Widgets and launcher shortcuts let you start common actions without opening the app first.
- Backup and restore let you create a portable local library archive when you want an extra copy of your work.

I wanted these features to feel like part of the same workflow. A person should be able to scan a document, organise it, prepare a PDF, and share it without having to move between several unrelated apps.

## Privacy as a product decision

Scanly is offline-first by design. The core scanning, editing, storage, and export workflows run locally on the device. There is no account system, cloud document library, or automatic upload of scanned pages.

This approach also makes the product easier to understand. Your documents live in your local library. You decide when to export, share, or copy a backup. The app does not quietly turn a private scan into data that needs to be stored somewhere else.

## How I built it

Scanly is a single-module Android application built with Kotlin and Jetpack Compose. I kept the project divided into clear layers so the UI can stay focused on the user experience while the processing and storage logic remain testable.

- `feature/` contains screens and ViewModels.
- `navigation/` defines the app destinations and user flows.
- `domain/` contains models, repository contracts, and use cases.
- `data/` handles Room, DataStore, local files, exports, updates, and backups.
- `core/` contains shared UI, machine learning, OpenCV processing, and utilities.
- `di/` contains the Hilt dependency wiring.

The main technologies are Kotlin, Jetpack Compose, Material 3, CameraX, LiteRT, OpenCV, Room, DataStore, WorkManager, Hilt, and Coroutines with Flow.

The processing path is intentionally local and layered. A captured image is normalised, checked, analysed for document corners, perspective-corrected, enhanced, and then saved as a processed page with a thumbnail. The raw capture remains available underneath that result.

## What I learned from building Scanly

The hardest part of a scanner is not taking a picture. It is handling all the imperfect situations around that picture. Pages are bent, lighting changes, desks contain other rectangular shapes, books show two pages at once, and automatic detection will sometimes be uncertain.

That is why I have treated manual controls as an important part of the product rather than a fallback to hide. Good automation should make the common case faster, but the user should still be able to understand and correct the result when the real world does not cooperate.

I have also tried to keep the app calm and focused. The interface should make the next useful action obvious, whether that is scanning a page, reviewing a document, fixing an edit, or exporting the final file.

## What Scanly is not

Scanly is not intended to be a cloud document platform or a replacement for a full enterprise records system. It does not currently focus on OCR, cloud collaboration, scheduled cloud backups, or account-based document synchronisation.

The focus is narrower and more practical: capture, improve, organise, and export documents privately on Android.

## Why this project matters to me

Scanly is a project where product decisions, computer vision, image processing, Android architecture, and interaction design all meet in one place. Every feature has to work as part of a real flow, not just exist as an isolated screen.

That combination is what makes the project interesting to me. I am not only building a camera feature. I am building a dependable tool around the moments before capture, the corrections after capture, and the decisions a person makes when they need a document to be clear and ready to use.

Scanly is open source under the AGPL-3.0-only license. The project documentation explains the architecture, processing pipeline, storage model, and development setup in more detail.

## Explore the project

- [Features](features.md) explains what the app can do.
- [User guide](user-guide.md) walks through the main user workflows.
- [Architecture overview](../architecture/overview.md) explains how the application is organised.
- [Capture and scan](../processing/capture-and-scan.md) describes the camera and finalisation flow.
- [Image processing](../processing/image-processing.md) covers detection, perspective correction, filters, and adjustments.
