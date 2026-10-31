const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const OWNER_UID = "owner_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validGamePayload(id = "game_1", ownerUid = OWNER_UID, overrides = {}) {
  const now = new Date();
  return {
    id,
    platformId: "bloxverse",
    title: "Neon Obby Rush",
    description: "Jump across glowing cyber platforms and beat the clock!",
    thumbnail: "obby_neon",
    gameUrl: "<html><body><h1>Neon Obby</h1></body></html>",
    category: "Obby",
    creator: "BloxVerse",
    plays: 10,
    likes: 5,
    rating: 4.5,
    ratingCount: 2,
    status: "active",
    featured: true,
    ownerUid,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validUserProfilePayload(userId = ALICE_UID, overrides = {}) {
  const now = new Date();
  return {
    userId,
    username: "AliceBlox",
    avatarKey: "cyber_knight",
    bio: "Obby speedrunner",
    totalPlays: 3,
    favoriteGameIds: "game_1",
    likedGameIds: "game_1",
    recentGameIds: "game_1",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validReviewPayload(id = "rev_1", gameId = "game_1", userId = ALICE_UID, overrides = {}) {
  const now = new Date();
  return {
    id,
    gameId,
    userId,
    userName: "AliceBlox",
    rating: 5,
    comment: "Awesome neon levels and responsive controls!",
    status: "published",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("1. Unauthenticated user cannot read private user profiles, while login-free game discovery works", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).get()
  );
  await assertSucceeds(
    unauthDb.collection("games").where("platformId", "==", "bloxverse_live_v2").get()
  );
});

test("2. Normal authenticated user cannot create a game", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("games").doc("game_1").set(validGamePayload("game_1", ALICE_UID))
  );
});

test("3. Verified Owner (with /admins/{uid} unlocked via password 'games') CAN create, update, and delete games", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const now = new Date();

  // Unlock owner privileges with passcode 'games'
  await assertSucceeds(
    ownerDb.collection("admins").doc(OWNER_UID).set({
      userId: OWNER_UID,
      passcode: "games",
      createdAt: now,
    })
  );

  // Create game as owner
  await assertSucceeds(
    ownerDb.collection("games").doc("game_1").set(validGamePayload("game_1", OWNER_UID))
  );

  // Update game details and status as owner
  await assertSucceeds(
    ownerDb.collection("games").doc("game_1").update({
      title: "Neon Obby Rush v2",
      status: "disabled",
      updatedAt: new Date(),
    })
  );

  // Delete game as owner
  await assertSucceeds(ownerDb.collection("games").doc("game_1").delete());
});

test("4. Wrong owner password cannot register in /admins", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({
      userId: ALICE_UID,
      passcode: "wrong_password",
      createdAt: new Date(),
    })
  );
});

test("5. Normal user can list games with platformId filter and update play/like counters on active games, but CANNOT modify title or delete", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("games").doc("game_1").set(validGamePayload("game_1", OWNER_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Aligned list query succeeds
  await assertSucceeds(
    aliceDb.collection("games").where("platformId", "==", "bloxverse").get()
  );

  // Incrementing plays & likes succeeds
  await assertSucceeds(
    aliceDb.collection("games").doc("game_1").update({
      plays: 11,
      likes: 6,
      updatedAt: new Date(),
    })
  );

  // Modifying title as normal user fails
  await assertFails(
    aliceDb.collection("games").doc("game_1").update({
      title: "Hacked Title",
      updatedAt: new Date(),
    })
  );

  // Deleting game as normal user fails
  await assertFails(aliceDb.collection("games").doc("game_1").delete());
});

test("6. User Profile isolation: user can CRUD own profile but cannot read or write another user's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserProfilePayload(ALICE_UID))
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("7. Reviews: authenticated user can create review for existing game and query published reviews, but cannot spoof userId", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("games").doc("game_1").set(validGamePayload("game_1", OWNER_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Valid review creation
  await assertSucceeds(
    aliceDb.collection("reviews").doc("rev_1").set(validReviewPayload("rev_1", "game_1", ALICE_UID))
  );

  // Spoofed userId fails
  await assertFails(
    aliceDb.collection("reviews").doc("rev_2").set(validReviewPayload("rev_2", "game_1", BOB_UID))
  );

  // Query published reviews succeeds
  await assertSucceeds(
    aliceDb.collection("reviews").where("status", "==", "published").get()
  );
});
