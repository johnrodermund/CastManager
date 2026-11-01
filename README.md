# CastManager

An Android app that finds Chromecast devices on the local network, connects to one, and plays a media file from the phone.

## Planned stack

- Kotlin and Jetpack Compose
- Google Cast SDK with the Default Media Receiver
- A small in-app HTTP server that streams the selected file to the Chromecast

## Building

Needs JDK 17 or newer and the Android SDK with platform 37. Set `sdk.dir` in `local.properties`, or set `ANDROID_HOME`.

```bash
./gradlew assembleDebug
./gradlew test
```

GitHub Actions runs the same build and tests on every pull request and on each push to `master`.

All Cast SDK use stays in the `cast` package, behind `CastSessionGateway`. Playback Controller tests replace it with `FakeCastSession`, so they run on the JVM. Cast isn't reliable on emulators, so test Cast behaviour by hand on a physical phone and a Cast Device.

## Future features

- Play from pasted URLs
- Play from other sources, such as network shares and media servers
- Subtitles and audio track selection
