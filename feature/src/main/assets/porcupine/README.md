# Porcupine wake-word assets (not bundled)

This directory is intentionally empty in the repository. Wake Mode's
[WakeWordEngine] (`PorcupineWakeWordEngine`) needs two files here before it
can actually detect "Диджей":

1. **`dj_ru_android.ppn`** — the trained Porcupine keyword file for the
   phrase "Диджей", for the Android platform.
2. **`porcupine_params_ru.pv`** — Porcupine's Russian language model,
   required because "Диджей" is not an English keyword.

## How to get them

1. Create a free account at https://console.picovoice.ai.
2. Under "Porcupine" → "Train Wake Word", train a new keyword for the
   phrase "Диджей", language Russian, platform Android.
3. Download the resulting `.ppn` file and rename it to `dj_ru_android.ppn`.
4. Download the Russian Porcupine language model (`porcupine_params_ru.pv`)
   from the same console / the Porcupine GitHub repo's `lib/common/`
   directory.
5. Place both files directly in this directory
   (`feature/src/main/assets/porcupine/`).
6. In the app's Settings screen, paste your personal Picovoice **Access
   Key** (also from the console) into the "Porcupine Access Key" field.

This is a one-time setup step, analogous to adding the Vosk model under
`assets/model/` (see the README there) — after these files are in place,
wake-word detection runs fully offline, on-device, exactly like the rest of
the app. Training the keyword itself does require an internet connection
and a Picovoice account, the same way the original Vosk model had to be
downloaded once before being bundled.

Until these files (and an Access Key) are configured, `PorcupineWakeWordEngine.isReady`
returns `false`, `VoiceEngine` logs a clear error and backs off instead of
crashing, and Wake Mode simply never detects the wake word — Continuous
Mode is unaffected either way.
