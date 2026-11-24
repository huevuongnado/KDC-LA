const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_1";
const ADMIN_EMAIL = "huevuongnado@gmail.com";
const SECOND_ADMIN_UID = "admin_uid_2";
const SECOND_ADMIN_EMAIL = "truongthon2@gmail.com";
const ALICE_UID = "alice_uid_1";
const ALICE_EMAIL = "alice@gmail.com";
const BOB_UID = "bob_uid_1";
const BOB_EMAIL = "bob@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

function citizenContext(uid = ALICE_UID, email = ALICE_EMAIL, emailVerified = true) {
  return testEnv.authenticatedContext(uid, {
    email,
    email_verified: emailVerified,
  });
}

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("households").get());
  await assertFails(unauthDb.collection("people").get());
  await assertFails(unauthDb.collection("posts").where("scope", "==", "Toàn thôn").get());
  await assertFails(unauthDb.collection("complaints").get());
  await assertFails(unauthDb.collection("settings").doc("portal").get());
  await assertFails(
    unauthDb.collection("households").doc("LA001").set({
      head: "Test",
      cluster: "Cụm dân cư Hóc Nhiêu",
      status: "Thường trú",
      classify: "Không",
      memberEmails: [],
      memberUids: [],
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Email spoofing with unverified admin email is rejected", async () => {
  const spoofDb = citizenContext("spoof_uid", ADMIN_EMAIL, false).firestore();
  await assertFails(
    spoofDb.collection("households").doc("LA001").set({
      head: "Spoof",
      cluster: "Cụm dân cư Hóc Nhiêu",
      status: "Thường trú",
      classify: "Không",
      memberEmails: [],
      memberUids: [],
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Bootstrapped admin can CRUD households, people, posts, settings, and add secondary admin", async () => {
  const db = adminContext().firestore();
  const past = new Date(Date.now() - 5000);

  // Create household (no `id` field in body because Kotlin uses @DocumentId)
  await assertSucceeds(
    db.collection("households").doc("LA001").set({
      head: "Nguyễn Văn An",
      cluster: "Cụm dân cư Hóc Nhiêu",
      status: "Thường trú",
      classify: "Hộ chính sách",
      address: "",
      memberEmails: [ALICE_EMAIL],
      memberUids: [],
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject ghost field ("Shadow Update Test")
  await assertFails(
    db.collection("households").doc("LA002").set({
      head: "Ghost",
      cluster: "Cụm dân cư Hóc Nhiêu",
      status: "Thường trú",
      classify: "Không",
      memberEmails: [],
      memberUids: [],
      ghostFlag: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Create person in LA001
  await assertSucceeds(
    db.collection("people").doc("NK0001").set({
      name: "Nguyễn Văn An",
      relation: "Chủ hộ",
      dob: "1970-04-12",
      gender: "Nam",
      cccd: "049070001001",
      phone: "0912000001",
      householdId: "LA001",
      cluster: "Cụm dân cư Hóc Nhiêu",
      residence: "Thường trú",
      education: "THPT",
      profession: "Trung cấp",
      religion: "Không",
      partyMember: true,
      job: "Làm nông / Trồng rừng",
      tempStart: "",
      tempEnd: "",
      memberEmails: [ALICE_EMAIL],
      memberUids: [],
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject person referencing non-existent household
  await assertFails(
    db.collection("people").doc("NK9999").set({
      name: "Orphan",
      relation: "Thành viên",
      gender: "Nam",
      householdId: "LA999",
      cluster: "Cụm dân cư Hóc Nhiêu",
      residence: "Thường trú",
      partyMember: false,
      job: "Khác",
      memberEmails: [],
      memberUids: [],
      createdAt: past,
      updatedAt: past,
    })
  );

  // Update person preserving createdAt
  await assertSucceeds(
    db.collection("people").doc("NK0001").update({
      job: "Cán bộ / Công chức",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    db.collection("people").doc("NK0001").update({
      job: "Khác",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Add secondary admin and verify they can read households
  await assertSucceeds(
    db.collection("admins").doc(SECOND_ADMIN_UID).set({
      userId: SECOND_ADMIN_UID,
      email: SECOND_ADMIN_EMAIL,
      addedBy: ADMIN_UID,
      createdAt: past,
    })
  );
  const secondAdminDb = citizenContext(SECOND_ADMIN_UID, SECOND_ADMIN_EMAIL, true).firestore();
  await assertSucceeds(secondAdminDb.collection("households").get());
  await assertSucceeds(secondAdminDb.collection("people").get());
});

test("Citizen can only read linked household/people via scoped query and cannot mutate them", async () => {
  const past = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("households").doc("LA001").set({
      head: "Nguyễn Văn An",
      cluster: "Cụm dân cư Hóc Nhiêu",
      status: "Thường trú",
      classify: "Hộ chính sách",
      address: "",
      memberEmails: [ALICE_EMAIL],
      memberUids: [ALICE_UID],
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("households").doc("LA002").set({
      head: "Trần Thị Bình",
      cluster: "Cụm dân cư Làng Vang",
      status: "Thường trú",
      classify: "Hộ nghèo",
      address: "",
      memberEmails: [BOB_EMAIL],
      memberUids: [BOB_UID],
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("people").doc("NK0001").set({
      name: "Nguyễn Văn An",
      relation: "Chủ hộ",
      dob: "1970-04-12",
      gender: "Nam",
      cccd: "049070001001",
      phone: "0912000001",
      householdId: "LA001",
      cluster: "Cụm dân cư Hóc Nhiêu",
      residence: "Thường trú",
      education: "THPT",
      profession: "Trung cấp",
      religion: "Không",
      partyMember: true,
      job: "Làm nông / Trồng rừng",
      tempStart: "",
      tempEnd: "",
      memberEmails: [ALICE_EMAIL],
      memberUids: [ALICE_UID],
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("people").doc("NK0004").set({
      name: "Trần Thị Bình",
      relation: "Chủ hộ",
      dob: "1962-06-11",
      gender: "Nữ",
      cccd: "049162001004",
      phone: "0912000004",
      householdId: "LA002",
      cluster: "Cụm dân cư Làng Vang",
      residence: "Thường trú",
      education: "Tiểu học",
      profession: "Chưa qua đào tạo",
      religion: "Không",
      partyMember: false,
      job: "Làm nông / Trồng rừng",
      tempStart: "",
      tempEnd: "",
      memberEmails: [BOB_EMAIL],
      memberUids: [BOB_UID],
      createdAt: past,
      updatedAt: past,
    });
  });

  const aliceDb = citizenContext(ALICE_UID, ALICE_EMAIL, true).firestore();

  // Scoped queries succeed
  await assertSucceeds(aliceDb.collection("households").where("memberEmails", "array-contains", ALICE_EMAIL).get());
  await assertSucceeds(aliceDb.collection("people").where("memberEmails", "array-contains", ALICE_EMAIL).get());
  await assertSucceeds(aliceDb.collection("households").doc("LA001").get());
  await assertSucceeds(aliceDb.collection("people").doc("NK0001").get());

  // Unfiltered queries & Bob's PII fail
  await assertFails(aliceDb.collection("households").get());
  await assertFails(aliceDb.collection("people").get());
  await assertFails(aliceDb.collection("households").doc("LA002").get());
  await assertFails(aliceDb.collection("people").doc("NK0004").get());

  // Citizen cannot write households or people
  await assertFails(aliceDb.collection("people").doc("NK0001").update({ job: "Hack", updatedAt: new Date(Date.now() - 1000) }));
});

test("User profile prevents self-assigned admin role escalation", async () => {
  const aliceDb = citizenContext(ALICE_UID, ALICE_EMAIL, true).firestore();
  const past = new Date(Date.now() - 5000);

  // Self-assigned admin fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      name: "Alice",
      role: "admin",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Citizen registration succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      name: "Alice",
      phone: "0912000001",
      cluster: "Cụm dân cư Hóc Nhiêu",
      householdId: "LA001",
      role: "citizen",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Updating own role to admin fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Updating own phone succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      phone: "0988777666",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Posts and complaints enforce scope, ownership, and status transitions", async () => {
  const past = new Date(Date.now() - 5000);
  const adminDb = adminContext().firestore();
  const aliceDb = citizenContext(ALICE_UID, ALICE_EMAIL, true).firestore();
  const bobDb = citizenContext(BOB_UID, BOB_EMAIL, true).firestore();

  await assertSucceeds(
    adminDb.collection("posts").doc("P1").set({
      title: "Họp thôn",
      category: "Lịch họp thôn",
      scope: "Toàn thôn",
      cluster: "",
      content: "Nội dung họp thôn",
      dateIso: "2026-11-23T08:00:00.000Z",
      pinned: true,
      imageData: "",
      allowedEmails: [],
      createdAt: past,
      updatedAt: past,
    })
  );

  // Citizen can list Toàn thôn posts via matching filter, not unfiltered
  await assertSucceeds(aliceDb.collection("posts").where("scope", "==", "Toàn thôn").get());
  await assertFails(aliceDb.collection("posts").get());

  // Citizen creates complaint
  await assertSucceeds(
    aliceDb.collection("complaints").doc("C1").set({
      userId: ALICE_UID,
      userEmail: ALICE_EMAIL,
      senderName: "Alice",
      householdId: "LA001",
      subject: "Biến động nhân khẩu",
      content: "Báo tạm trú mới",
      dateIso: "2026-11-23T08:00:00.000Z",
      status: "Chờ xử lý",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Bob cannot read Alice's complaints
  await assertFails(bobDb.collection("complaints").doc("C1").get());
  await assertFails(bobDb.collection("complaints").get());

  // Alice can list her own complaints
  await assertSucceeds(aliceDb.collection("complaints").where("userId", "==", ALICE_UID).get());

  // Alice cannot mark her own complaint resolved
  await assertFails(
    aliceDb.collection("complaints").doc("C1").update({
      status: "Đã xử lý",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin can resolve Alice's complaint
  await assertSucceeds(
    adminDb.collection("complaints").doc("C1").update({
      status: "Đã xử lý",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Citizen accounts enforce limit(1) lookup, boundUid ownership, self password change, and linked household read", async () => {
  const past = new Date(Date.now() - 5000);
  const adminDb = adminContext().firestore();
  const aliceDb = citizenContext(ALICE_UID, ALICE_EMAIL, true).firestore();
  const bobDb = citizenContext(BOB_UID, BOB_EMAIL, true).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("households").doc("LA001").set({
      head: "Nguyễn Văn An",
      cluster: "Cụm dân cư Hóc Nhiêu",
      status: "Thường trú",
      classify: "Hộ chính sách",
      address: "",
      memberEmails: [],
      memberUids: [],
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("households").doc("LA002").set({
      head: "Trần Thị Bình",
      cluster: "Cụm dân cư Làng Vang",
      status: "Thường trú",
      classify: "Hộ nghèo",
      address: "",
      memberEmails: [],
      memberUids: [],
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("people").doc("NK0001").set({
      name: "Nguyễn Văn An",
      relation: "Chủ hộ",
      dob: "1970-04-12",
      gender: "Nam",
      cccd: "049070001001",
      phone: "0912000001",
      householdId: "LA001",
      cluster: "Cụm dân cư Hóc Nhiêu",
      residence: "Thường trú",
      education: "THPT",
      profession: "Trung cấp",
      religion: "Không",
      partyMember: true,
      job: "Làm nông / Trồng rừng",
      tempStart: "",
      tempEnd: "",
      memberEmails: [],
      memberUids: [],
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("people").doc("NK0004").set({
      name: "Trần Thị Bình",
      relation: "Chủ hộ",
      dob: "1962-06-11",
      gender: "Nữ",
      cccd: "049162001004",
      phone: "0912000004",
      householdId: "LA002",
      cluster: "Cụm dân cư Làng Vang",
      residence: "Thường trú",
      education: "Tiểu học",
      profession: "Chưa qua đào tạo",
      religion: "Không",
      partyMember: false,
      job: "Làm nông / Trồng rừng",
      tempStart: "",
      tempEnd: "",
      memberEmails: [],
      memberUids: [],
      createdAt: past,
      updatedAt: past,
    });
  });

  // Admin creates citizen account for NK0001
  await assertSucceeds(
    adminDb.collection("citizen_accounts").doc("NK0001").set({
      personId: "NK0001",
      householdId: "LA001",
      cluster: "Cụm dân cư Hóc Nhiêu",
      name: "Nguyễn Văn An",
      phone: "0912000001",
      cccd: "049070001001",
      loginKeys: ["0912000001", "049070001001"],
      passwordSalt: "0123456789abcdef0123456789abcdef",
      passwordHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      mustChangePassword: true,
      boundUid: "",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Unauthenticated cannot read or query citizen_accounts
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("citizen_accounts").where("loginKeys", "array-contains", "0912000001").limit(1).get());

  // Citizen CANNOT bulk-list citizen_accounts without limit(1)
  await assertFails(aliceDb.collection("citizen_accounts").get());
  await assertFails(aliceDb.collection("citizen_accounts").limit(5).get());

  // Citizen CAN lookup a single account by phone or CCCD with limit(1)
  await assertSucceeds(aliceDb.collection("citizen_accounts").where("loginKeys", "array-contains", "0912000001").limit(1).get());
  await assertSucceeds(aliceDb.collection("citizen_accounts").where("loginKeys", "array-contains", "049070001001").limit(1).get());

  // Alice binds boundUid to herself and updates her /users profile
  await assertSucceeds(
    aliceDb.collection("citizen_accounts").doc("NK0001").update({
      boundUid: ALICE_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      name: "Nguyễn Văn An",
      phone: "0912000001",
      cluster: "Cụm dân cư Hóc Nhiêu",
      householdId: "LA001",
      linkedPersonId: "NK0001",
      role: "citizen",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Linked citizen can read her own household by doc get() and people in LA001
  await assertSucceeds(aliceDb.collection("households").doc("LA001").get());
  await assertSucceeds(aliceDb.collection("people").where("householdId", "==", "LA001").get());
  // Still cannot read LA002
  await assertFails(aliceDb.collection("households").doc("LA002").get());
  await assertFails(aliceDb.collection("people").where("householdId", "==", "LA002").get());

  // Bob CANNOT hijack Alice's bound citizen account or change her password
  await assertFails(
    bobDb.collection("citizen_accounts").doc("NK0001").update({
      boundUid: BOB_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("citizen_accounts").doc("NK0001").update({
      passwordSalt: "fedcba9876543210fedcba9876543210",
      passwordHash: "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
      mustChangePassword: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice CAN change her own password
  await assertSucceeds(
    aliceDb.collection("citizen_accounts").doc("NK0001").update({
      passwordSalt: "abcdef0123456789abcdef0123456789",
      passwordHash: "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
      mustChangePassword: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin CAN reset Alice's password and unbind boundUid
  await assertSucceeds(
    adminDb.collection("citizen_accounts").doc("NK0001").update({
      passwordSalt: "11111111111111112222222222222222",
      passwordHash: "3333333333333333444444444444444455555555555555556666666666666666",
      mustChangePassword: true,
      boundUid: "",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

