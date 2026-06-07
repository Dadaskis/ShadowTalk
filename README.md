<img height="400" alt="image" src="https://github.com/user-attachments/assets/5cce544a-8739-4621-a0e7-958a8fcb1bf0" />

# ShadowTalk

ShadowTalk is a simple Android app for **voice shadowing practice**. Shadowing is a language-learning technique where you listen to native speech and speak along simultaneously, mimicking pronunciation, rhythm, and intonation.

## What the app does

1. Lets you select an audio file from your device (your practice material).
2. Shows a waveform visualization of the target audio.
3. Records your voice while the target audio plays through the speaker.
4. Stops recording automatically when the target audio finishes (optional manual stop).
5. Displays a waveform of your recording and lets you play it back to review.

## How to use

1. **Grant permissions** when prompted on first launch.
2. Tap **Select Audio** and choose an audio file (MP3, M4A, WAV, etc.).
3. Wait for the target waveform to appear.
4. Optionally check:
   - **Mute target during recording** - records your voice without playing the target audio aloud.
   - **Manual stop** - keeps recording until you tap **Stop Recording** instead of stopping when the target ends.
5. Tap **Record** to start shadowing. Speak along with the target audio.
6. When recording finishes, review the recorded waveform.
7. Tap **Play Recorded** to listen to your shadowing attempt.

## Permissions

| Permission | Why it's needed |
|---|---|
| `READ_EXTERNAL_STORAGE` (Android 12 and below) | Read audio files you select from device storage |
| `READ_MEDIA_AUDIO` (Android 13+) | Read audio files you select from device storage |
| `RECORD_AUDIO` | Record your voice during shadowing practice |

Recordings are saved only in the app's private internal storage - they are not shared with other apps.

## Technical notes

- **Language:** Kotlin
- **Min SDK:** API 24 (Android 7.0)
- **UI:** XML layouts with ViewBinding (not Compose)
- **Playback:** `MediaPlayer` with audio focus handling
- **Recording:** `MediaRecorder`
- **Waveform:** Android `Visualizer` class + custom `Canvas` drawing
- **Theme:** Dark theme throughout (#121212 background, #BB86FC accent)

## Building

Open the project in Android Studio and run on a device or emulator with API 24+.

```bash
./gradlew assembleDebug
```

## License

MIT License

Copyright (c) 2026 ShadowTalk

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

**Note:** This app is designed specifically for shadowing practice - listening and speaking along with target audio to improve fluency and pronunciation.
