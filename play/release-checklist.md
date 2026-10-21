# Closed test release checklist

## Before the first upload

1. **Upload key.** Create one (or reuse the upload key pattern from your other apps) and keep it outside the repository:

   ```
   keytool -genkeypair -v -keystore ../keys/kansalaistreeni-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

   Copy `keystore.properties.example` to `keystore.properties` (git-ignored) and fill in the passwords. Enrol in Play App Signing when creating the app; Google keeps the app signing key.
2. **Feedback form (blocks upload).** Put the Google Form URL in `app/src/main/res/values/strings.xml` → `feedback_url`. The Settings → About button appears only when it is an `https://` URL; with the empty value in the repository, testers have no in-app feedback route. Check the button on a release build before uploading.
3. **Privacy policy.** Fill in the contact address in `play/privacy-policy.md`, publish it at a public URL and enter the URL in Play Console.
4. **Build.** `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`, signed when `keystore.properties` exists. Run `scripts/verify.ps1` first.

## Play Console answers

- **App access:** all functionality available without login.
- **Ads:** no ads.
- **Content rating:** education; no violence, sexual content, gambling, user interaction or data sharing.
- **Target audience:** adults (18+). Not designed for children.
- **Data safety:** no data collected, no data shared. The app has no internet permission; data stays on the device; users can delete it by clearing app data. (A Google Form opened in the browser is outside the app.)
- **Government apps declaration:** the app is not developed by or on behalf of a government; the listing says it is unofficial and not affiliated with Migri.
- **Financial features, health, news:** none.

## Tester note (paste into Test Circle / the testing track)

> Kansalaistreeni is an unofficial practice app for the basics of Finnish society (Finnish or Swedish questions). Please try: one lesson from Home, one topic with its reading text, switching FI/SV in the top bar, the practice assessment, and Settings. Tell me via Settings → About → Send feedback (or email dmitry.gurbanov@protonmail.com) about anything wrong, unclear or hard to read — especially wrong facts or unnatural Finnish/Swedish. The app works offline and collects no data.
