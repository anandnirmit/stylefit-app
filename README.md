# StyleFit — AI Virtual Try-On App

Take a photo of yourself and a photo of an outfit, and see the clothes
realistically fitted onto your body using a free, publicly hosted AI model
(IDM-VTON on Hugging Face Spaces).

## How it works
- No backend of your own needed. The app calls a **free public AI Space** on
  Hugging Face directly over the internet.
- You can switch between a couple of known-working free models from the
  in-app **Settings** screen if one is slow or temporarily offline.
- Because this depends on a free community-hosted demo (not a paid,
  guaranteed-uptime service), results can take anywhere from ~15 seconds to
  a minute or two, and the model can occasionally be busy. This is the
  tradeoff of $0 cost + real AI try-on.

## Get the installable APK (no Android Studio needed)

This repo is set up to auto-build the APK in the cloud via GitHub Actions —
you just need a free GitHub account.

1. **Create a free GitHub account** at github.com if you don't have one.
2. **Create a new repository** (e.g. "stylefit-app"), then upload every file
   in this folder to it (drag-and-drop works on github.com, or use `git push`
   if you're comfortable with git).
3. Once pushed, go to the **Actions** tab of your repo. A workflow called
   "Build APK" will run automatically (takes ~3-5 minutes).
4. When it finishes (green checkmark), click into that workflow run, scroll
   to **Artifacts**, and download **StyleFit-debug-apk** — that's a zip
   containing `app-debug.apk`.
5. Transfer that `.apk` to your Android phone (email it to yourself, use
   Google Drive, USB cable, etc.) and tap it to install. You'll need to allow
   "install unknown apps" for whichever app you used to open the file — Android
   will prompt you for this automatically the first time.

That's it — no computer build tools required on your end.

## If you ever do get access to a machine with Android Studio
Just open this folder as a project and click Run — everything is standard
Gradle/Kotlin/Compose, no special setup needed.

## Notes on the AI model / customizing
- The API call logic lives in `app/src/main/java/com/stylefit/tryon/network/GradioClient.kt`.
- If try-on requests start failing, the free Space's API may have changed
  slightly. Open the Space in a browser (e.g.
  https://huggingface.co/spaces/yisol/IDM-VTON ), scroll to the bottom, and
  click **"Use via API"** to see the current parameter names — then update
  `buildRequestData()` in `GradioClient.kt` to match.
- To add more free model options, add entries to `TryOnPresets.presets` in
  the same file.
- For best results: use a clear, well-lit, front-facing full-body-ish photo
  of yourself, and a clothing photo on a plain/flat background (product-style
  shots work best).

## Upgrading later
If you eventually want more reliable/faster results, paid try-on APIs (e.g.
FASHN.AI, Segmind, Replicate-hosted IDM-VTON) offer better uptime and speed
for a small per-image cost. Swapping one in only requires changing the
network layer — the rest of the app stays the same.
