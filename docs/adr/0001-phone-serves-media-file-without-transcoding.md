# The phone serves the Media File over HTTP, without transcoding

A Cast Device can only play media it fetches from a URL, so it can't read a file directly from the phone. We decided that the app serves the current Media File itself over HTTP during Playback, and does not transcode. This needs no account, cloud service or PC, and it keeps the app small. Transcoding on a phone would be slow, drain the battery and add heavy dependencies.

## Considered Options

- **Upload to cloud storage and cast the URL:** rejected because it adds an account, an upload wait and a privacy cost for a file that is already on the same network.
- **Serve from a PC or home server:** rejected because the app should work with only a phone and a Cast Device.
- **Transcode on the phone:** rejected for the reasons above. A Media File that the Cast Device can't play fails with a clear error.

## Consequences

- Playback depends on the phone staying on the same Wi-Fi network as the Cast Device, and it ends if the phone leaves.
- The phone needs to keep serving while the app is in the background, so Playback runs in a foreground service.
- The server is reachable from the local network, so it serves only the current Media File, at a random URL, and only during Playback.
- Accepting incoming connections from the Cast Device needs Android's local network permission on apps targeting SDK 37 or higher, so the output switcher's exemption from that permission doesn't help this app. The app targets SDK 37 and requests the permission just before the first Playback. If it's denied, the app explains that casting can't work without it.
