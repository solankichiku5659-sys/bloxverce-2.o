# BloxVerse — Production Gaming Platform Guide & Deployment Instructions

## 1. Platform Overview
**BloxVerse** is a multi-game platform inspired by Roblox where players discover, favorite, rate, and play self-contained HTML5 (HTML, CSS, JavaScript, and ZIP-packaged) games directly inside the app with real-time Cloud Firestore synchronization.

- **Normal Users**: Can sign in with Google, browse Featured / Popular / Trending / Recommended games, filter by category, search, favorite games, track recently played history, rate & review games, view global leaderboards, and play HTML5 games inside the full-screen WebView Game Player. Normal users **never** see upload, edit, disable, or delete buttons.
- **Private Owner System**: Protected by the Owner Password **`games`** and enforced on the backend via Firestore Security Rules (`/admins/{uid}`).

---

## 2. Accessing the Hidden Owner Panel
Normal users cannot see the Owner Panel in navigation or on game cards. As the Owner, you can unlock the **Owner Vault Authentication Dialog** in two ways:
1. **Logo 5-Tap Gesture**: Tap the **BLOXVERSE** logo in the top-left header **5 times** quickly.
2. **Search Command**: Type `/owner` or `#owner` into the Home Search Bar.
3. **Profile Footer**: Tap the `BloxVerse Platform v1.0 • Cloud Synced` footer at the bottom of the Profile screen **5 times**.

When prompted:
- Enter the Owner Password: **`games`**
- Tap **Unlock Studio**.
- Your authenticated UID is verified and registered in `/admins/{uid}`, unlocking the **Owner** tab and granting full Firestore write/update/delete privileges.

---

## 3. Game Upload & Removal Flows

### Owner Upload Flow (`.ZIP` / `.HTML` / `.CSS` / `.JS`)
1. Open the **Owner** tab and tap **Upload New Game**.
2. Enter the Game Title, Description, Category (`Obby`, `Tycoon`, `Simulator`, `Adventure`, `Action`, `Arcade`, `Puzzle`), and select a Thumbnail preset.
3. Tap **Upload ZIP / HTML** to pick a `.zip` HTML5 game package or `.html`/`.js` file from your device:
   - `GameZipExtractor` automatically extracts `index.html`, `.css` stylesheets, and `.js` scripts from the ZIP archive and bundles them into a self-contained, responsive HTML5 payload.
4. Tap **Publish Live**. The game is saved to Cloud Firestore (`/games/{gameId}`) and becomes immediately visible to all users in real time.

### Owner Removal & Disable Flow
- **Disable / Enable**: Tap **Disable** on any game card in the Owner Panel to set `status: "disabled"`. It immediately disappears from all normal users' feeds while remaining manageable in the Owner Panel.
- **Delete**: Tap the **Delete (Trash)** icon and confirm. The game document is permanently removed from Firestore (`/games/{gameId}`) and disappears immediately across all connected devices without requiring an app update.

---

## 4. Firebase Architecture & Security Rules
- **Authentication**: Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption` and silent `GetGoogleIdOption`).
- **Firestore Database ID**: Uses the provisioned Enterprise database ID configured in `app/src/main/res/values/firebase_applet_config.xml` (`R.string.firestore_database_id`).
- **Collections**:
  - `/games/{gameId}` — Game catalog, HTML5 bundles, play/like counters, and ratings.
  - `/users/{userId}` — Per-user profile, favorite game IDs, liked game IDs, and recently played history.
  - `/reviews/{reviewId}` — Player ratings (`1..5`) and text reviews.
  - `/admins/{adminId}` — Verified Owner session records (`passcode == "games"`).

---

## 5. Building & Deploying for Production
1. **Android APK / AAB Export**:
   - In Google AI Studio, open the **Settings / Export** menu to download the signed APK/AAB or export the full Gradle project as a ZIP / GitHub repository.
   - Or run locally with Android Studio / Gradle:
     ```bash
     gradle :app:assembleDebug
     ```
2. **Firestore Security Rules Deployment**:
   - Rules are defined in `/firestore.rules` and tested via `firestore.test.js` and `BloxVerseRepositoryRuleTest.kt`.
3. **Optional Firebase Console Extensions**:
   - Google Sign-In is pre-provisioned out of the box. To enable additional auth providers or external cloud buckets, open the Firebase Console for project `gen-lang-client-0454925176`.
