# Screenshots

The current install-page previews are `docs/assets/home-preview.jpg` and
`docs/assets/settings-preview.jpg`, captured from the isolated public debug APK
on MiTV-AFMU1 at 1920×1080 and resized to 1280×720 for the page. They contain
no addresses, pairing codes, camera views, or personal analytics. The library
capture is deliberately excluded because it shows apps installed by the owner.

The comparison also uses `docs/assets/google-tv-home-test-stick.png`, a real
1920×1080 capture of the stock Google TV Home on the same MiTV-AFMU1. It shows
the owner's app row and third-party recommendation artwork; it is labeled as
Google TV in both languages and is not presented as a generic mockup. Review
the visible account avatar and recommendation imagery again before publishing.

`docs/assets/interface-preview.mp4` is a 7-second H.264 screen recording of
real Home → Settings → Home transitions from that same isolated APK. The
transitions were triggered by ADB input with HDMI output off; it is not a
physical-remote or projector smoothness test. Video has no microphone audio.

Replace and review these preview assets from the final production-signed
release candidate before publishing the first stable release. Check visual
scaling on the connected projector and remove device-specific content.
