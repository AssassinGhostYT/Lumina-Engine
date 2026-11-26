import { DurableObject } from "cloudflare:workers";
import type { Env } from "./hub";

export type Role = "owner" | "admin" | "editor" | "viewer";

export interface Member {
  id: string;
  name: string;
  role: Role;
  tokenHash: string;
  joinedAt: number;
  lastSeen: number;
}

export interface ChatMessage {
  id: string;
  memberId: string;
  name: string;
  text: string;
  lang: string;
  at: number;
}

export interface FileOp {
  id: string;
  k: "i" | "d";
  p?: string | null;
  c?: string;
  t?: number;
}

export interface FileState {
  rev: number;
  ops: FileOp[];
  updatedAt: number;
}

interface Meta {
  id: string;
  name: string;
  ownerId: string;
  inviteCode: string;
  createdAt: number;
}

interface Attached {
  memberId: string;
  voice?: boolean;
  muted?: boolean;
}

const MAX_TEXT = 2000;
const MAX_CHAT = 200;
const MAX_OPS_PER_FILE = 50_000;
const MAX_SEED = 3_000_000;
const FILE_PATH = /^[A-Za-z0-9_./ -]{1,300}$/;
const OP_ID = /^[A-Za-z0-9_:.-]{1,200}$/;
const MAX_OP_TEXT = 500_000;
const MAX_VOICE_FRAME = 64_000;
const TARGET_LANG = /^[a-z]{2}$/;
const SOURCE_LANG = /^[a-z]{2}$/;

const canEdit = (role: Role) => role === "owner" || role === "admin" || role === "editor";
const canManage = (role: Role) => role === "owner" || role === "admin";

function randomHex(bytes: number): string {
  const values = crypto.getRandomValues(new Uint8Array(bytes));
  return Array.from(values, (v) => v.toString(16).padStart(2, "0")).join("");
}

function cleanName(value: unknown): string {
  return String(value ?? "").replace(/\s+/g, " ").trim().slice(0, 60);
}

function cleanPath(value: unknown): string {
  const path = String(value ?? "").replace(/\\/g, "/");
  if (!FILE_PATH.test(path) || path.includes("..") || path.startsWith("/")) return "";
  return path;
}

function cleanOp(value: unknown): FileOp | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = typeof raw.id === "string" ? raw.id : "";
  const k = raw.k === "i" || raw.k === "d" ? raw.k : null;
  if (!k || !OP_ID.test(id)) return null;
  const t = typeof raw.t === "number" && Number.isFinite(raw.t) && raw.t >= 0 ? raw.t : undefined;
  if (k === "d") return { id, k, ...(t === undefined ? {} : { t }) };
  const c = typeof raw.c === "string" ? raw.c : "";
  if (!c || c.length > MAX_OP_TEXT) return null;
  const p = raw.p == null ? null : typeof raw.p === "string" ? raw.p : "";
  if (p && !OP_ID.test(p)) return null;
  return { id, k, ...(p ? { p } : {}), c, ...(t === undefined ? {} : { t }) };
}

async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

async function googleTranslate(value: string, source: string, target: string): Promise<string> {
  try {
    const url = new URL("https://translate.googleapis.com/translate_a/single");
    url.searchParams.set("client", "gtx");
    url.searchParams.set("sl", source || "auto");
    url.searchParams.set("tl", target);
    url.searchParams.set("dt", "t");
    url.searchParams.set("q", value);
    const response = await fetch(url.toString(), { headers: { "user-agent": "NebulonEngine/1.0" } });
    if (!response.ok) return "";
    const data = (await response.json()) as unknown;
    const parts = Array.isArray(data) && Array.isArray((data as any)[0]) ? (data as any)[0] : [];
    return parts.map((part: any) => (Array.isArray(part) ? String(part[0] ?? "") : "")).join("").trim();
  } catch {
    return "";
  }
}

async function myMemoryTranslate(value: string, source: string, target: string): Promise<string> {
  try {
    const url = new URL("https://api.mymemory.translated.net/get");
    url.searchParams.set("q", value);
    url.searchParams.set("langpair", `${source}|${target}`);
    const response = await fetch(url.toString(), { headers: { "user-agent": "NebulonEngine/1.0" } });
    if (!response.ok) return "";
    const data = (await response.json()) as any;
    if (Number(data?.responseStatus) !== 200) return "";
    const text = String(data?.responseData?.translatedText ?? "").trim();
    if (!text || /MYMEMORY WARNING|INVALID SOURCE|PLEASE SELECT TWO DISTINCT/i.test(text)) return "";
    return text;
  } catch {
    return "";
  }
}

function publicMember(member: Member, online: boolean) {
  return {
    id: member.id,
    name: member.name,
    role: member.role,
    online,
    joinedAt: member.joinedAt,
  };
}

export class Room extends DurableObject<Env> {
  async create(id: string, name: string, ownerName: string) {
    const existing = await this.ctx.storage.get<Meta>("meta");
    if (existing) return { error: "room_exists" as const };

    const cleanRoom = cleanName(name) || "Nebulon team";
    const cleanOwner = cleanName(ownerName) || "Owner";
    const ownerId = randomHex(8);
    const token = randomHex(32);
    const tokenHash = await sha256(token);
    const inviteCode = randomHex(5);
    const now = Date.now();
    const meta: Meta = { id, name: cleanRoom, ownerId, inviteCode, createdAt: now };
    const member: Member = {
      id: ownerId,
      name: cleanOwner,
      role: "owner",
      tokenHash,
      joinedAt: now,
      lastSeen: now,
    };
    await this.ctx.storage.put("meta", meta);
    await this.ctx.storage.put("member:" + ownerId, member);
    await this.ctx.storage.put("token:" + tokenHash, ownerId);
    return {
      room: { id: meta.id, name: meta.name, inviteCode: meta.inviteCode, ownerId: meta.ownerId, createdAt: meta.createdAt },
      me: publicMember(member, true),
      token,
    };
  }

  async join(code: string, name: string) {
    const meta = await this.ctx.storage.get<Meta>("meta");
    if (!meta) return { error: "room_not_found" as const };
    if (String(code ?? "").trim() !== meta.inviteCode) return { error: "bad_invite" as const };

    const clean = cleanName(name);
    if (!clean) return { error: "bad_name" as const };

    const id = randomHex(8);
    const token = randomHex(32);
    const tokenHash = await sha256(token);
    const now = Date.now();
    const member: Member = {
      id,
      name: clean,
      role: "editor",
      tokenHash,
      joinedAt: now,
      lastSeen: now,
    };
    await this.ctx.storage.put("member:" + id, member);
    await this.ctx.storage.put("token:" + tokenHash, id);
    this.broadcastMembers();
    return {
      room: { id: meta.id, name: meta.name, ownerId: meta.ownerId, createdAt: meta.createdAt },
      me: publicMember(member, true),
      token,
    };
  }

  async info(token: string) {
    const member = await this.auth(token);
    if (!member) return { error: "unauthorized" as const };
    return this.infoFor(member);
  }

  async files(token: string) {
    const member = await this.auth(token);
    if (!member) return { error: "unauthorized" as const };
    const entries = await this.ctx.storage.list<FileState>({ prefix: "file:" });
    const files: Record<string, FileState> = {};
    for (const [key, value] of entries) files[key.slice(5)] = value;
    return { files };
  }

  async seed(token: string, input: Record<string, unknown>) {
    const member = await this.auth(token);
    if (!member) return { error: "unauthorized" as const };
    if (!canEdit(member.role)) return { error: "forbidden" as const };

    const source = input && typeof input === "object" ? input : {};
    let total = 0;
    const changed: string[] = [];
    const now = Date.now();
    for (const [rawPath, rawText] of Object.entries(source)) {
      const path = cleanPath(rawPath);
      const text = typeof rawText === "string" ? rawText : "";
      if (!path || !text || text.length > 500_000) continue;
      total += text.length;
      if (total > MAX_SEED) break;
      const key = "file:" + path;
      const existing = await this.ctx.storage.get<FileState>(key);
      if (existing && existing.ops.length > 0) continue;
      const state: FileState = {
        rev: 1,
        ops: [{ id: "seed", k: "i", p: null, c: text }],
        updatedAt: now,
      };
      await this.ctx.storage.put(key, state);
      changed.push(path);
    }
    if (changed.length) this.broadcast({ t: "reset", files: changed });
    return { seeded: changed };
  }

  async chat(token: string, body: Record<string, unknown>) {
    const member = await this.auth(token);
    if (!member) return { error: "unauthorized" as const };
    const text = String(body?.text ?? "").trim().slice(0, MAX_TEXT);
    if (!text) return { error: "empty" as const };
    const lang = String(body?.lang ?? "").slice(0, 8);
    const message: ChatMessage = {
      id: randomHex(6),
      memberId: member.id,
      name: member.name,
      text,
      lang,
      at: Date.now(),
    };
    const history = (await this.ctx.storage.get<ChatMessage[]>("chat")) ?? [];
    history.push(message);
    while (history.length > MAX_CHAT) history.shift();
    await this.ctx.storage.put("chat", history);
    this.broadcast({ t: "chat", msg: message });
    return { msg: message };
  }

  async translate(token: string, text: string, source: string, target: string) {
    const member = await this.auth(token);
    if (!member) return { error: "unauthorized" as const };
    const value = String(text ?? "").trim().slice(0, MAX_TEXT);
    const src = SOURCE_LANG.test(String(source ?? "").toLowerCase()) ? String(source).toLowerCase() : "auto";
    const lang = String(target ?? "").slice(0, 2).toLowerCase();
    if (!value || !TARGET_LANG.test(lang)) return { error: "bad_request" as const };
    if (src !== "auto" && src === lang) return { text: value };

    const google = await googleTranslate(value, src, lang);
    if (google) return { text: google };

    // Google occasionally blocks datacenter IPs; MyMemory is a free fallback.
    const memory = await myMemoryTranslate(value, src === "auto" ? "en" : src, lang);
    if (memory) return { text: memory };

    return { text: value, error: "translate_failed" as const };
  }

  async setRole(token: string, memberId: string, role: string) {
    const member = await this.auth(token);
    if (!member) return { error: "unauthorized" as const };
    if (!canManage(member.role)) return { error: "forbidden" as const };
    const target = await this.ctx.storage.get<Member>("member:" + memberId);
    if (!target) return { error: "not_found" as const };
    if (target.role === "owner" || memberId === member.id) return { error: "cannot_change" as const };
    if (role !== "admin" && role !== "editor" && role !== "viewer") return { error: "bad_role" as const };
    if (member.role === "admin" && role === "admin") return { error: "forbidden" as const };
    target.role = role;
    await this.ctx.storage.put("member:" + target.id, target);
    this.broadcastMembers();
    return { member: publicMember(target, this.isOnline(target.id)) };
  }

  async remove(token: string, memberId: string) {
    const member = await this.auth(token);
    if (!member) return { error: "unauthorized" as const };
    if (!canManage(member.role)) return { error: "forbidden" as const };
    const target = await this.ctx.storage.get<Member>("member:" + memberId);
    if (!target) return { error: "not_found" as const };
    if (target.role === "owner") return { error: "cannot_remove_owner" as const };
    if (member.role === "admin" && target.role === "admin") return { error: "forbidden" as const };
    await this.ctx.storage.delete("member:" + target.id);
    await this.ctx.storage.delete("token:" + target.tokenHash);
    for (const ws of this.ctx.getWebSockets()) {
      const attached = this.attachment(ws);
      if (attached?.memberId === target.id) {
        try {
          ws.close(4001, "removed");
        } catch {
          /* already closed */
        }
      }
    }
    this.broadcastMembers();
    return { removed: target.id };
  }

  async regenerateInvite(token: string) {
    const member = await this.auth(token);
    if (!member) return { error: "unauthorized" as const };
    if (!canManage(member.role)) return { error: "forbidden" as const };
    const meta = await this.ctx.storage.get<Meta>("meta");
    if (!meta) return { error: "room_not_found" as const };
    meta.inviteCode = randomHex(5);
    await this.ctx.storage.put("meta", meta);
    return { inviteCode: meta.inviteCode };
  }

  async fetch(request: Request): Promise<Response> {
    if (request.headers.get("Upgrade") !== "websocket") return new Response("expected a websocket", { status: 426 });
    const token = this.bearer(request);
    const member = await this.auth(token);
    if (!member) return new Response("unauthorized", { status: 401 });

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ memberId: member.id, voice: false } satisfies Attached);
    member.lastSeen = Date.now();
    await this.ctx.storage.put("member:" + member.id, member);

    const info = await this.infoFor(member);
    const chat = (await this.ctx.storage.get<ChatMessage[]>("chat")) ?? [];
    server.send(JSON.stringify({ t: "welcome", ...info, chat, voice: this.voiceMembers(), voiceMuted: this.voiceMuted() }));
    this.broadcastMembers();
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string") {
      if (message.byteLength > MAX_VOICE_FRAME) return;
      const attached = this.attachment(ws);
      if (!attached || !attached.voice) return;
      const member = await this.ctx.storage.get<Member>("member:" + attached.memberId);
      if (!member) {
        ws.close(4001, "removed");
        return;
      }
      const from = new TextEncoder().encode(attached.memberId);
      if (from.length > 255) return;
      const frame = new Uint8Array(1 + from.length + message.byteLength);
      frame[0] = from.length;
      frame.set(from, 1);
      frame.set(new Uint8Array(message), 1 + from.length);
      for (const other of this.ctx.getWebSockets()) {
        if (other === ws || other.readyState !== 1) continue;
        if (!this.attachment(other)?.voice) continue;
        try {
          other.send(frame.buffer);
        } catch {
          /* closed */
        }
      }
      return;
    }
    let data: any;
    try {
      data = JSON.parse(message);
    } catch {
      return;
    }
    const attached = this.attachment(ws);
    if (!attached) return;
    const member = await this.ctx.storage.get<Member>("member:" + attached.memberId);
    if (!member) {
      ws.close(4001, "removed");
      return;
    }
    if (data.t === "ping") {
      ws.send(JSON.stringify({ t: "pong" }));
      return;
    }
    if (data.t === "voice_join" || data.t === "voice_leave") {
      const wanted = data.t === "voice_join";
      if (!!attached.voice === wanted) return;
      ws.serializeAttachment({ ...attached, voice: wanted, muted: false } satisfies Attached);
      this.broadcastVoice();
      return;
    }
    if (data.t === "voice_mute") {
      if (!attached.voice) return;
      const muted = data.muted === true;
      if (!!attached.muted === muted) return;
      ws.serializeAttachment({ ...attached, muted } satisfies Attached);
      this.broadcastVoice();
      return;
    }
    if (data.t === "op") {
      if (!canEdit(member.role)) {
        ws.send(JSON.stringify({ t: "error", message: "read_only" }));
        return;
      }
      const path = cleanPath(data.file);
      const op = cleanOp(data.op);
      if (!path || !op) {
        ws.send(JSON.stringify({ t: "error", message: "bad_op" }));
        return;
      }
      const key = "file:" + path;
      const state = (await this.ctx.storage.get<FileState>(key)) ?? { rev: 0, ops: [], updatedAt: Date.now() };
      if (state.ops.length >= MAX_OPS_PER_FILE) {
        ws.send(JSON.stringify({ t: "error", message: "file_full" }));
        return;
      }
      state.rev += 1;
      state.ops.push(op);
      state.updatedAt = Date.now();
      await this.ctx.storage.put(key, state);
      this.broadcast({
        t: "op",
        file: path,
        rev: state.rev,
        site: String(data.site ?? "").slice(0, 40),
        op,
        from: member.id,
        name: member.name,
      });
      return;
    }
    if (data.t === "chat") {
      await this.chatMessage(member, String(data.text ?? ""), String(data.lang ?? ""));
    }
  }

  async webSocketClose(ws: WebSocket, code: number, reason: string): Promise<void> {
    const attached = this.attachment(ws);
    try {
      if (attached?.voice) ws.serializeAttachment({ ...attached, voice: false, muted: false } satisfies Attached);
    } catch {
      /* closed */
    }
    try {
      ws.close(code, reason);
    } catch {
      /* ignore */
    }
    this.broadcastVoice();
    this.broadcastMembers();
  }

  async webSocketError(ws: WebSocket): Promise<void> {
    const attached = this.attachment(ws);
    try {
      if (attached?.voice) ws.serializeAttachment({ ...attached, voice: false, muted: false } satisfies Attached);
    } catch {
      /* closed */
    }
    this.broadcastVoice();
    this.broadcastMembers();
  }

  private async auth(token: string): Promise<Member | null> {
    if (!token || token.length < 24) return null;
    const tokenHash = await sha256(token);
    const memberId = await this.ctx.storage.get<string>("token:" + tokenHash);
    if (!memberId) return null;
    const member = await this.ctx.storage.get<Member>("member:" + memberId);
    if (!member) return null;
    return member;
  }

  private bearer(request: Request): string {
    const header = request.headers.get("authorization") ?? "";
    if (/^Bearer\s+/i.test(header)) return header.replace(/^Bearer\s+/i, "").trim();
    return new URL(request.url).searchParams.get("token") ?? "";
  }

  private attachment(ws: WebSocket): Attached | null {
    const value = ws.deserializeAttachment();
    return value && typeof value === "object" && typeof (value as Attached).memberId === "string" ? (value as Attached) : null;
  }

  private async infoFor(member: Member) {
    const meta = await this.ctx.storage.get<Meta>("meta");
    if (!meta) return { error: "room_not_found" as const };
    const entries = await this.ctx.storage.list<Member>({ prefix: "member:" });
    const members = Array.from(entries.values()).map((m) => publicMember(m, this.isOnline(m.id)));
    members.sort((a, b) => (a.role === "owner" ? -1 : b.role === "owner" ? 1 : a.joinedAt - b.joinedAt));
    return {
      room: {
        id: meta.id,
        name: meta.name,
        ownerId: meta.ownerId,
        createdAt: meta.createdAt,
        ...(canManage(member.role) ? { inviteCode: meta.inviteCode } : {}),
      },
      me: publicMember(member, true),
      members,
    };
  }

  private isOnline(memberId: string): boolean {
    return this.ctx.getWebSockets().some((ws) => this.attachment(ws)?.memberId === memberId);
  }

  private voiceMembers(): string[] {
    const ids = new Set<string>();
    for (const ws of this.ctx.getWebSockets()) {
      if (ws.readyState !== 1) continue;
      const attached = this.attachment(ws);
      if (attached?.voice) ids.add(attached.memberId);
    }
    return Array.from(ids);
  }

  private voiceMuted(): string[] {
    const ids = new Set<string>();
    for (const ws of this.ctx.getWebSockets()) {
      if (ws.readyState !== 1) continue;
      const attached = this.attachment(ws);
      if (attached?.voice && attached.muted) ids.add(attached.memberId);
    }
    return Array.from(ids);
  }

  private broadcastVoice() {
    this.broadcast({ t: "voice", members: this.voiceMembers(), muted: this.voiceMuted() });
  }

  private broadcast(value: unknown) {
    const text = JSON.stringify(value);
    for (const ws of this.ctx.getWebSockets()) {
      try {
        ws.send(text);
      } catch {
        /* closed */
      }
    }
  }

  private broadcastMembers() {
    this.ctx.getWebSockets();
    this.ctx.storage.list<Member>({ prefix: "member:" }).then((entries) => {
      const members = Array.from(entries.values()).map((m) => publicMember(m, this.isOnline(m.id)));
      members.sort((a, b) => (a.role === "owner" ? -1 : b.role === "owner" ? 1 : a.joinedAt - b.joinedAt));
      this.broadcast({ t: "members", members });
    });
  }

  private async chatMessage(member: Member, raw: string, lang: string) {
    const text = raw.trim().slice(0, MAX_TEXT);
    if (!text) return;
    const message: ChatMessage = {
      id: randomHex(6),
      memberId: member.id,
      name: member.name,
      text,
      lang: lang.slice(0, 8),
      at: Date.now(),
    };
    const history = (await this.ctx.storage.get<ChatMessage[]>("chat")) ?? [];
    history.push(message);
    while (history.length > MAX_CHAT) history.shift();
    await this.ctx.storage.put("chat", history);
    this.broadcast({ t: "chat", msg: message });
  }
}
