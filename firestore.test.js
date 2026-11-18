const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated users cannot read or write any user profile, parlay, or prop", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("saved_parlays").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("saved_props").get()
  );
});

test("Authenticated user can create and update their own profile, parlay, and prop with valid payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Reemz Quant",
      preferredFormat: "AMERICAN",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("saved_parlays")
      .doc("parlay_abc123")
      .set({
        userId: ALICE_UID,
        title: "BOS ML + Tatum Points MORE 27.5",
        leagueCodes: "NBA",
        correlatedProb: 0.56,
        independentProb: 0.44,
        avgRho: 0.54,
        correlatedEvPercent: 0.12,
        marketAmericanOdds: 115,
        primaryThesis: "Scoring volume drives team win margin.",
        legSummaries: ["BOS ML (-160)", "J. Tatum MORE 27.5 PTS (-115)"],
        sameGame: true,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("saved_props")
      .doc("prop_nba_4065648")
      .set({
        userId: ALICE_UID,
        playerName: "Jayson Tatum",
        teamAbbr: "BOS",
        leagueCode: "NBA",
        matchup: "BOS @ MIL",
        statLabel: "Points",
        lineValue: 27.5,
        projectedValue: 29.4,
        recommendedSide: "MORE",
        winProb: 0.57,
        evPercent: 0.06,
        moreOdds: -115,
        lessOdds: -115,
        headshotUrl: "https://a.espncdn.com/i/headshots/nba/players/full/4065648.png",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Valid list queries on user-scoped subcollections
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("saved_parlays").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("saved_props").get()
  );
});

test("Shadow updates, @DocumentId 'id' injection, future timestamps, and cross-user access are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("saved_props")
      .doc("prop_1")
      .set({
        userId: ALICE_UID,
        playerName: "Josh Allen",
        teamAbbr: "BUF",
        leagueCode: "NFL",
        matchup: "KC @ BUF",
        statLabel: "Pass Yds",
        lineValue: 254.5,
        projectedValue: 271.2,
        recommendedSide: "MORE",
        winProb: 0.58,
        evPercent: 0.08,
        moreOdds: -112,
        lessOdds: -118,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Cross-user read & write must fail
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("saved_props").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("saved_props")
      .doc("prop_1")
      .delete()
  );

  // Ghost field & @DocumentId 'id' injection must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("saved_props")
      .doc("prop_2")
      .set({
        id: "prop_2",
        userId: ALICE_UID,
        playerName: "Josh Allen",
        teamAbbr: "BUF",
        leagueCode: "NFL",
        matchup: "KC @ BUF",
        statLabel: "Pass Yds",
        lineValue: 254.5,
        projectedValue: 271.2,
        recommendedSide: "MORE",
        winProb: 0.58,
        evPercent: 0.08,
        moreOdds: -112,
        lessOdds: -118,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Future timestamp tampering must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("saved_props")
      .doc("prop_3")
      .set({
        userId: ALICE_UID,
        playerName: "Josh Allen",
        teamAbbr: "BUF",
        leagueCode: "NFL",
        matchup: "KC @ BUF",
        statLabel: "Pass Yds",
        lineValue: 254.5,
        projectedValue: 271.2,
        recommendedSide: "MORE",
        winProb: 0.58,
        evPercent: 0.08,
        moreOdds: -112,
        lessOdds: -118,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: now,
      })
  );
});
