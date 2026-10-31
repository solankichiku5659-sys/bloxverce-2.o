# BloxVerse Firestore Security Specification (`security_spec.md`)

## 1. Data Invariants & Access Model
1. **Zero-Trust Default Deny**: Every unmapped path is denied (`match /{document=**} { allow read, write: if false; }`).
2. **Authentication Invariant**: All reads and writes require an authenticated user (`request.auth != null`).
3. **Owner Privilege Isolation (`/admins/{adminId}`)**:
   - A user can only register their own UID (`request.auth.uid == adminId`) by supplying the exact owner passcode (`passcode == 'games'`) and server timestamp.
   - Users can only read/delete their own admin registration document.
4. **Game Catalog Security (`/games/{gameId}`)**:
   - **Normal Users**: Can read games where `platformId == 'bloxverse'`, and can ONLY update counter/rating fields (`['plays', 'likes', 'rating', 'ratingCount', 'updatedAt']`) on active games while preserving all immutable metadata (`id`, `title`, `description`, `thumbnail`, `gameUrl`, `category`, `creator`, `status`, `featured`, `ownerUid`, `createdAt`).
   - **Owner Only (`isAdmin()`)**: Only an authenticated Owner can `create` new games, `delete` games, or `update` game metadata/files/status (`title`, `description`, `thumbnail`, `gameUrl`, `category`, `creator`, `status`, `featured`).
5. **User Profile Isolation (`/users/{userId}`)**:
   - Strictly isolated per user (`request.auth.uid == userId` and `resource.data.userId == request.auth.uid`) for all `get`, `list`, `create`, `update`, and `delete` operations.
6. **Reviews Integrity (`/reviews/{reviewId}`)**:
   - Authenticated users can query published reviews (`status == 'published'`) or their own reviews (`userId == request.auth.uid`).
   - Users can only create, update, or delete reviews where `userId == request.auth.uid` and `rating` is an integer in `1..5`.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Game Read**: `unauthDb.collection("games").where("platformId", "==", "bloxverse").get()` -> REJECTED.
2. **Normal User Game Creation**: Authenticated non-owner attempts `db.collection("games").doc("hack").set(...)` -> REJECTED.
3. **Normal User Game Deletion**: Authenticated non-owner attempts `db.collection("games").doc("g1").delete()` -> REJECTED.
4. **Normal User Game Title/Status Tampering**: Non-owner updates `title` or `status` on `/games/g1` -> REJECTED by `affectedKeys().hasOnly(...)`.
5. **Shadow Field Injection on Game Update**: Owner or user injects `isVerified: true` into `/games/g1` -> REJECTED by `hasOnly()`.
6. **Cross-User Profile Read (PII / Isolation Leak)**: Alice attempts `db.collection("users").doc(BOB_UID).get()` -> REJECTED.
7. **Unfiltered User Profile List Scraping**: Alice calls `db.collection("users").get()` without `.where("userId", "==", ALICE_UID)` -> REJECTED.
8. **Identity Spoofing on Review Creation**: Alice creates `/reviews/r1` with `userId: BOB_UID` -> REJECTED.
9. **Out-of-Bounds Rating Value Poisoning**: Alice creates `/reviews/r1` with `rating: 999` or `rating: "five"` -> REJECTED by `isValidGameReview()`.
10. **Wrong Owner Passcode Escalation**: Alice creates `/admins/alice` with `passcode: "wrong"` -> REJECTED.
11. **Timestamp Manipulation**: Client writes `createdAt` in the future (`request.time + 1h`) or mutates `createdAt` on update -> REJECTED.
12. **Oversized String Resource Exhaustion**: Client writes a 10,000-character `title` or invalid ID format -> REJECTED by `.size()` and `isValidId()`.
