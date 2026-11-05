/**
 * Go Ananas! Cloud Function.
 * When a phone uploads a scan with a disease, the farm owner and the agricultural
 * technician of the same farm receive a push notification. When three or more scans
 * of the same disease come from one block within 14 days, everyone on the farm
 * receives an outbreak alert.
 */
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

const REVIEWERS = ["Farm owner", "Agricultural technician"];
const OUTBREAK_THRESHOLD = 3;
const WINDOW_MS = 14 * 24 * 60 * 60 * 1000;

async function tokensFor(farmCode, roles) {
  let q = db.collection("users").where("farmCode", "==", farmCode);
  if (roles) q = q.where("role", "in", roles);
  const snap = await q.get();
  return snap.docs.map((d) => d.get("fcmToken")).filter(Boolean);
}

async function send(tokens, title, body, data = {}) {
  if (!tokens.length) return;
  await admin.messaging().sendEachForMulticast({ tokens, notification: { title, body }, data });
}

exports.onNewScan = onDocumentCreated("farms/{farm}/scans/{scanId}", async (event) => {
  const scan = event.data && event.data.data();
  if (!scan || scan.isHealthy) return;
  const farm = event.params.farm;
  const where = scan.block ? ` in ${scan.block}` : "";
  const pct = Math.round((scan.confidence || 0) * 100);

  // 1. notify the farm owner and technician
  await send(
    await tokensFor(farm, REVIEWERS),
    `${scan.predictedClass} detected${where}`,
    `${scan.userName || "A farm worker"} scanned a plant (${pct}% confidence). Open the dashboard to review it.`,
    { scanId: event.params.scanId }
  );

  // 2. outbreak alert for the whole farm
  if (!scan.block) return;
  const since = Date.now() - WINDOW_MS;
  const similar = await db.collection("farms").doc(farm).collection("scans")
    .where("predictedLabel", "==", scan.predictedLabel)
    .where("block", "==", scan.block)
    .where("createdAt", ">=", since)
    .count().get();
  const n = similar.data().count;
  if (n === OUTBREAK_THRESHOLD) {   // send once, when the threshold is reached
    await send(
      await tokensFor(farm, null),
      `Possible ${scan.predictedClass} outbreak`,
      `${n} plants in ${scan.block} were found with ${scan.predictedClass} in the last 14 days.`
    );
  }
});
