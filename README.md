# LiveTVBox Native V1

This version uses the confirmed V1 API:

GET /api/public/channels
GET /api/public/channels/{id}

The second endpoint returns:
- short-lived signed MPD source(s)
- optional ClearKey DRM keyId/key

The app requests the channel immediately before playback, then uses Media3/ExoPlayer
for MPEG-DASH playback and ClearKey configuration.

It does NOT use the LiveTgTV website or WebView.

TV controls:
- D-pad: navigate
- OK/Enter: play selected channel
- Back: return from player
- Search: optional filter

Universal project targets Android 9+ and common ABIs.
