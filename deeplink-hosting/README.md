# Deep Link Invite — PERMANENT FREE FIX (Firebase Hosting)

The one-tap invite link `https://dotclashai.web.app/join?code=XXXXXX` opens the app
directly once these files are deployed to **Firebase Hosting** (free, no domain to buy —
you already have the `dotclashai` Firebase project).

The app is already wired for host `dotclashai.web.app`. You only need to deploy ONCE.

## Deploy steps (run from the `DotsAndBoxes/` folder)

```bash
# 1. Log in with the Google account that OWNS the dotclashai Firebase project
#    (the CLI here is logged in as a different account, so switch first)
firebase login

# 2. Make sure Hosting is enabled for the project once (Firebase console →
#    Build → Hosting → Get started), then deploy:
firebase deploy --only hosting
```

That publishes:
- `https://dotclashai.web.app/.well-known/assetlinks.json`  (App Link verification)
- `https://dotclashai.web.app/join`  (fallback page for users without the app)

## Verify it worked
```bash
# assetlinks must return the JSON (not 404):
curl https://dotclashai.web.app/.well-known/assetlinks.json

# App Link verification on the device:
adb shell pm verify-app-links --re-verify com.pixelplay.dotsboxes
adb shell pm get-app-links com.pixelplay.dotsboxes   # dotclashai.web.app should show "verified"
```
Then tap `https://dotclashai.web.app/join?code=ABC123` in WhatsApp → app opens straight
into Online with the code pre-filled.

## Fingerprints in assetlinks.json
- Current file has the **debug** keystore SHA-256 (works for the test APK you're using now).
- Before Play Store release, add your **release** keystore SHA-256 AND the **Play App
  Signing** SHA-256 (Play Console → Setup → App integrity) to the array in
  `.well-known/assetlinks.json`, then `firebase deploy --only hosting` again.

## Until deployed
The tap-to-open link won't work yet, but the **room code still works**: friend copies the
code from the shared message → opens app → Online lobby auto-fills it → Join.
