import { spawn } from "node:child_process";
import WebSocket from "ws";

const PORT = Number(process.env.COLLAB_PORT || 8801);
const BASE = `http://127.0.0.1:${PORT}`;
let failed = 0;
const check = (name, cond, extra = "") => {
  console.log((cond ? "PASS " : "FAIL ") + name + (cond ? "" : "  " + extra));
  if (!cond) failed++;
};

const dev = spawn("npx", ["wrangler", "dev", "--local", "--port", String(PORT)], {
  stdio: ["ignore", "pipe", "pipe"],
  cwd: new URL("..", import.meta.url).pathname,
});
let log = "";
dev.stdout.on("data", (d) => (log += d));
dev.stderr.on("data", (d) => (log += d));

async function waitUp() {
  for (let i = 0; i < 120; i++) {
    try {
      const r = await fetch(BASE + "/health");
      if (r.ok) return;
    } catch {}
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error("worker did not start:\n" + log);
}

async function json(path, options = {}) {
  const r = await fetch(BASE + path, {
    ...options,
    headers: { "content-type": "application/json", ...(options.headers || {}) },
  });
  return { status: r.status, body: await r.json().catch(() => null) };
}

function socket(room, token, inbox) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://127.0.0.1:${PORT}/v1/rooms/${room}/ws`, {
      headers: { authorization: "Bearer " + token },
    });
    ws.on("open", () => resolve(ws));
    ws.on("message", (raw) => inbox.push(JSON.parse(raw.toString())));
    ws.on("error", reject);
  });
}

async function waitFor(inbox, test, ms = 3000) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    const found = inbox.find(test);
    if (found) return found;
    await new Promise((r) => setTimeout(r, 50));
  }
  return null;
}

try {
  await waitUp();

  const created = await json("/v1/rooms", { method: "POST", body: JSON.stringify({ name: "Test de colaborador", ownerName: "Brayan" }) });
  check("create room", created.status === 200 && created.body.room.name === "Test de colaborador" && created.body.token && created.body.room.inviteCode);
  const room = created.body.room.id;
  const owner = created.body.token;
  const invite = created.body.room.inviteCode;

  const info = await json("/v1/rooms/" + room, { headers: { authorization: "Bearer " + owner } });
  check("owner sees invite and members", info.status === 200 && info.body.members.length === 1 && info.body.members[0].role === "owner" && info.body.room.inviteCode === invite);

  const joined = await json(`/v1/rooms/${room}/join`, { method: "POST", body: JSON.stringify({ code: invite, name: "Collab" }) });
  check("join with invite", joined.status === 200 && joined.body.me.name === "Collab" && joined.body.me.role === "editor" && joined.body.token);
  const collab = joined.body.token;
  const collabId = joined.body.me.id;
  check("new member does not get the invite code", !joined.body.room.inviteCode);

  const badJoin = await json(`/v1/rooms/${room}/join`, { method: "POST", body: JSON.stringify({ code: "nope", name: "X" }) });
  check("bad invite rejected", badJoin.status === 403 && badJoin.body.error === "bad_invite");

  const ownerInbox = [];
  const collabInbox = [];
  const wsOwner = await socket(room, owner, ownerInbox);
  const wsCollab = await socket(room, collab, collabInbox);
  const welcome = await waitFor(ownerInbox, (m) => m.t === "welcome");
  check("websocket welcome", !!welcome && welcome.members.length === 2);
  const members = await waitFor(collabInbox, (m) => m.t === "members" && m.members.length === 2);
  check("members broadcast", !!members && members.members.some((m) => m.online));

  const op = { id: "c:1", k: "i", p: null, c: "hola" };
  wsCollab.send(JSON.stringify({ t: "op", file: "scripts/player.cs", op, site: "collab" }));
  const relayed = await waitFor(ownerInbox, (m) => m.t === "op" && m.file === "scripts/player.cs");
  check("file op relayed", !!relayed && relayed.op.id === "c:1" && relayed.from === collabId);

  const files = await json(`/v1/rooms/${room}/files`, { headers: { authorization: "Bearer " + owner } });
  check("file ops stored", files.status === 200 && files.body.files["scripts/player.cs"].ops.length === 1 && files.body.files["scripts/player.cs"].rev === 1);

  const chat = await json(`/v1/rooms/${room}/chat`, { method: "POST", headers: { authorization: "Bearer " + owner }, body: JSON.stringify({ text: "Hola equipo", lang: "es" }) });
  check("chat stored", chat.status === 200 && chat.body.msg.text === "Hola equipo");
  const incoming = await waitFor(collabInbox, (m) => m.t === "chat" && m.msg.text === "Hola equipo");
  check("chat broadcast", !!incoming && incoming.msg.name === "Brayan");

  const promoted = await json(`/v1/rooms/${room}/members/${collabId}`, { method: "POST", headers: { authorization: "Bearer " + owner }, body: JSON.stringify({ role: "viewer" }) });
  check("owner changes role", promoted.status === 200 && promoted.body.member.role === "viewer");
  await new Promise((r) => setTimeout(r, 200));
  wsCollab.send(JSON.stringify({ t: "op", file: "scripts/player.cs", op: { id: "c:2", k: "i", p: "c:1", c: "!" }, site: "collab" }));
  const denied = await waitFor(collabInbox, (m) => m.t === "error" && m.message === "read_only");
  check("viewer cannot edit", !!denied);

  const back = await json(`/v1/rooms/${room}/members/${collabId}`, { method: "POST", headers: { authorization: "Bearer " + owner }, body: JSON.stringify({ role: "editor" }) });
  check("owner restores editor", back.status === 200 && back.body.member.role === "editor");

  const regen = await json(`/v1/rooms/${room}/invite`, { method: "POST", headers: { authorization: "Bearer " + owner } });
  check("regenerate invite", regen.status === 200 && regen.body.inviteCode && regen.body.inviteCode !== invite);
  const oldInvite = await json(`/v1/rooms/${room}/join`, { method: "POST", body: JSON.stringify({ code: invite, name: "Old" }) });
  check("old invite stops working", oldInvite.status === 403 && oldInvite.body.error === "bad_invite");

  const info2 = await json("/v1/rooms/" + room, { headers: { authorization: "Bearer " + collab } });
  check("member list has two members", info2.status === 200 && info2.body.members.length === 2 && !info2.body.room.inviteCode);

  const removed = await json(`/v1/rooms/${room}/members/${collabId}`, { method: "DELETE", headers: { authorization: "Bearer " + owner } });
  check("owner removes member", removed.status === 200 && removed.body.removed === collabId);
  await new Promise((r) => setTimeout(r, 300));
  const afterRemove = await json("/v1/rooms/" + room, { headers: { authorization: "Bearer " + collab } });
  check("removed token is unauthorized", afterRemove.status === 401);

  const seeded = await json(`/v1/rooms/${room}/seed`, { method: "POST", headers: { authorization: "Bearer " + owner }, body: JSON.stringify({ files: { "main.json": "{\"name\":\"Nebulon\"}", "code/Game.cs": "class Game {}" } }) });
  check("seed files", seeded.status === 200 && seeded.body.seeded.length === 2);
  const seededFiles = await json(`/v1/rooms/${room}/files`, { headers: { authorization: "Bearer " + owner } });
  check("seed did not overwrite edited file", seededFiles.body.files["scripts/player.cs"].ops.length === 1 && seededFiles.body.files["main.json"].ops[0].c === "{\"name\":\"Nebulon\"}");

  wsOwner.close();
  wsCollab.close();
} catch (e) {
  console.log("ERROR", e);
  failed++;
} finally {
  dev.kill("SIGTERM");
}
console.log(failed ? `\n${failed} FAILED` : "\nALL OK");
process.exit(failed ? 1 : 0);
