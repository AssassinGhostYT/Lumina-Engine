import type { Env } from "./hub";

/**
 * Lumina Store API (free assets).
 *
 * Public:
 *   GET    /store/health
 *   GET    /store/assets
 *   GET    /store/assets/:id
 *   GET    /store/assets/:id/download
 *   GET    /store/assets/:id/preview/:index
 *
 * Accounts (email + password -> API key):
 *   POST   /store/auth/register  { email, password }
 *   POST   /store/auth/login     { email, password }
 *   GET    /store/auth/me        Authorization: Bearer <api key>
 *
 * Publishing (Authorization: Bearer <api key>):
 *   POST   /store/assets         multipart: manifest, package, previews...
 *   GET    /store/mine
 *   DELETE /store/assets/:id
 *
 * Storage uses R2 when bound; otherwise the free KV namespace (max 24 MB per package).
 */

interface AssetRow {
  id: string;
  name: string;
  type: string;
  description: string;
  author: string;
  version: string;
  price: string;
  tags: string;
  previews: string;
  video: string;
  license: string;
  size: number;
  downloads: number;
  object_key: string;
  owner_id: string | null;
  status: string;
  created: number;
  updated: number;
}

interface UserRow {
  id: number;
  email: string;
  pass_hash: string;
  salt: string;
  api_key: string;
  created: number;
}

interface StoredObject {
  body: ReadableStream | ArrayBuffer;
  contentType: string;
  etag?: string;
}

interface Auth {
  admin: boolean;
  user?: UserRow;
}

const json = (body: unknown, status = 200, cache = "no-store") =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": cache },
  });

const bad = (message: string, status = 400) => json({ error: message }, status);

const ID = /^[a-z0-9][a-z0-9-]{0,63}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const MAX_PACKAGE = 50 * 1024 * 1024;
const KV_MAX_PACKAGE = 24 * 1024 * 1024;
const MAX_PREVIEW = 6 * 1024 * 1024;
const DAILY_UPLOADS = 5;
const TYPES = new Set(["model3d", "vehicle", "character", "map", "sound", "sprite2d", "prefab", "other"]);
const encoder = new TextEncoder();

function b64url(input: ArrayBuffer | Uint8Array): string {
  const bytes = input instanceof Uint8Array ? input : new Uint8Array(input);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function randomKey(bytes = 32): string {
  const b = new Uint8Array(bytes);
  crypto.getRandomValues(b);
  return b64url(b);
}

async function hashPassword(password: string, salt: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", encoder.encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt: encoder.encode(salt), iterations: 100_000, hash: "SHA-256" },
    key,
    256,
  );
  return b64url(bits);
}

function normalizeEmail(value: unknown): string {
  return String(value || "").trim().toLowerCase();
}

function extension(file: File): string {
  const name = (file.name || "").toLowerCase();
  if (name.endsWith(".png")) return "png";
  if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "jpg";
  if (name.endsWith(".webp")) return "webp";
  if (name.endsWith(".gif")) return "gif";
  return "";
}

function previewType(key: string): string {
  if (key.endsWith(".png")) return "image/png";
  if (key.endsWith(".jpg") || key.endsWith(".jpeg")) return "image/jpeg";
  if (key.endsWith(".webp")) return "image/webp";
  if (key.endsWith(".gif")) return "image/gif";
  return "application/octet-stream";
}

function toAsset(row: AssetRow, userId?: number): Record<string, unknown> {
  let tags: string[] = [];
  let keys: string[] = [];
  try {
    const t = JSON.parse(row.tags || "[]");
    if (Array.isArray(t)) tags = t.map((x) => String(x)).filter(Boolean).slice(0, 12);
  } catch {
    /* keep empty */
  }
  try {
    const p = JSON.parse(row.previews || "[]");
    if (Array.isArray(p)) keys = p.map((x) => String(x)).filter(Boolean).slice(0, 5);
  } catch {
    /* keep empty */
  }
  return {
    id: row.id,
    name: row.name,
    type: row.type,
    description: row.description,
    author: row.author,
    version: row.version,
    price: row.price || "Free",
    tags,
    previews: keys.map((_, i) => `/store/assets/${encodeURIComponent(row.id)}/preview/${i}`),
    video: row.video,
    license: row.license,
    size: row.size,
    downloads: row.downloads,
    download: `/store/assets/${encodeURIComponent(row.id)}/download`,
    updated: row.updated,
    mine: !!userId && row.owner_id === String(userId),
  };
}

function adminToken(request: Request, env: Env): boolean {
  const token = env.STORE_ADMIN_TOKEN;
  if (!token) return false;
  return (request.headers.get("authorization") || "") === `Bearer ${token}`;
}

async function auth(request: Request, env: Env): Promise<Auth | null> {
  const header = request.headers.get("authorization") || "";
  if (!header.startsWith("Bearer ")) return null;
  const key = header.slice(7).trim();
  if (!key) return null;
  if (adminToken(request, env)) return { admin: true };
  if (!env.STORE_DB) return null;
  const user = await env.STORE_DB.prepare("SELECT * FROM users WHERE api_key = ?").bind(key).first<UserRow>();
  return user ? { admin: false, user } : null;
}

async function quotaUsed(env: Env, userId: number): Promise<number> {
  const dayStart = new Date();
  dayStart.setUTCHours(0, 0, 0, 0);
  const row = await env.STORE_DB!
    .prepare("SELECT COUNT(*) AS n FROM assets WHERE owner_id = ? AND created >= ?")
    .bind(String(userId), dayStart.getTime())
    .first<{ n: number }>();
  return Number(row?.n || 0);
}

async function putObject(
  env: Env,
  key: string,
  value: ReadableStream | ArrayBuffer,
  contentType: string,
  cacheControl: string,
): Promise<void> {
  if (env.STORE_R2) {
    await env.STORE_R2.put(key, value, { httpMetadata: { contentType, cacheControl } });
    return;
  }
  if (env.STORE_KV) {
    if (!(value instanceof ArrayBuffer)) throw new Error("KV requires a buffered value");
    await env.STORE_KV.put(key, value, { metadata: { contentType, cacheControl } });
    return;
  }
  throw new Error("store_not_configured");
}

async function getObject(env: Env, key: string): Promise<StoredObject | null> {
  if (env.STORE_R2) {
    const obj = await env.STORE_R2.get(key);
    if (!obj) return null;
    return {
      body: obj.body,
      contentType: obj.httpMetadata?.contentType || "application/octet-stream",
      etag: obj.httpEtag,
    };
  }
  if (env.STORE_KV) {
    const got = await env.STORE_KV.getWithMetadata(key, "arrayBuffer");
    if (!got.value) return null;
    const meta = (got.metadata || {}) as Record<string, unknown>;
    return {
      body: got.value as ArrayBuffer,
      contentType: typeof meta.contentType === "string" ? meta.contentType : "application/octet-stream",
    };
  }
  return null;
}

async function deleteObjects(env: Env, keys: string[]): Promise<void> {
  if (!keys.length) return;
  if (env.STORE_R2) {
    await env.STORE_R2.delete(keys);
    return;
  }
  if (env.STORE_KV) {
    for (const key of keys) await env.STORE_KV.delete(key);
  }
}

async function serveObject(env: Env, key: string, contentType: string, filename?: string): Promise<Response> {
  const obj = await getObject(env, key);
  if (!obj) return new Response("not found", { status: 404 });
  const headers = new Headers();
  headers.set("content-type", contentType || obj.contentType);
  headers.set("cache-control", "public, max-age=3600");
  if (obj.etag) headers.set("etag", obj.etag);
  if (filename) headers.set("content-disposition", `attachment; filename="${filename.replace(/[^A-Za-z0-9._-]/g, "_")}"`);
  return new Response(obj.body, { headers });
}

// ---------------------------------------------------------------- accounts

async function register(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return new Response("method not allowed", { status: 405, headers: { allow: "POST" } });
  if (!env.STORE_DB) return bad("store_not_configured", 503);
  let body: any;
  try {
    body = await request.json();
  } catch {
    return bad("bad json");
  }
  const email = normalizeEmail(body.email);
  const password = String(body.password || "");
  if (!EMAIL.test(email) || email.length > 160) return bad("invalid email");
  if (password.length < 8 || password.length > 200) return bad("password must be at least 8 characters");

  const exists = await env.STORE_DB.prepare("SELECT id FROM users WHERE email = ?").bind(email).first();
  if (exists) return bad("email_already_registered", 409);

  const salt = randomKey(16);
  const hash = await hashPassword(password, salt);
  const apiKey = randomKey(32);
  try {
    await env.STORE_DB.prepare("INSERT INTO users (email, pass_hash, salt, api_key, created) VALUES (?, ?, ?, ?, ?)")
      .bind(email, hash, salt, apiKey, Date.now())
      .run();
  } catch (e) {
    return bad("could not create account: " + (e instanceof Error ? e.message : String(e)), 500);
  }
  const user = await env.STORE_DB.prepare("SELECT * FROM users WHERE email = ?").bind(email).first<UserRow>();
  return json({ api_key: apiKey, email, quota: { used: 0, limit: DAILY_UPLOADS } }, 201);
}

async function login(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return new Response("method not allowed", { status: 405, headers: { allow: "POST" } });
  if (!env.STORE_DB) return bad("store_not_configured", 503);
  let body: any;
  try {
    body = await request.json();
  } catch {
    return bad("bad json");
  }
  const email = normalizeEmail(body.email);
  const password = String(body.password || "");
  const user = await env.STORE_DB.prepare("SELECT * FROM users WHERE email = ?").bind(email).first<UserRow>();
  if (!user) return bad("invalid_credentials", 401);
  const hash = await hashPassword(password, user.salt);
  if (hash !== user.pass_hash) return bad("invalid_credentials", 401);
  return json({
    api_key: user.api_key,
    email: user.email,
    quota: { used: await quotaUsed(env, user.id), limit: DAILY_UPLOADS },
  });
}

async function me(request: Request, env: Env): Promise<Response> {
  if (request.method !== "GET") return new Response("method not allowed", { status: 405, headers: { allow: "GET" } });
  const a = await auth(request, env);
  if (!a) return bad("unauthorized", 401);
  if (a.admin) return json({ admin: true, email: "admin", quota: { used: 0, limit: DAILY_UPLOADS } });
  const user = a.user!;
  return json({
    email: user.email,
    quota: { used: await quotaUsed(env, user.id), limit: DAILY_UPLOADS },
  });
}

// ---------------------------------------------------------------- publishing

async function publish(request: Request, env: Env, a: Auth): Promise<Response> {
  if (!env.STORE_DB) return bad("store_not_configured", 503);
  if (!env.STORE_R2 && !env.STORE_KV) return bad("store_not_configured", 503);

  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    return bad("expected multipart/form-data");
  }
  const raw = form.get("manifest");
  const pkg = form.get("package");
  if (typeof raw !== "string") return bad("manifest is required");
  if (!(pkg instanceof File)) return bad("package is required");
  if (pkg.size <= 0) return bad("package size is invalid", 413);

  const kvOnly = !env.STORE_R2 && !!env.STORE_KV;
  const maxSize = kvOnly ? KV_MAX_PACKAGE : MAX_PACKAGE;
  if (pkg.size > maxSize)
    return bad(kvOnly ? "package is too large (max 24 MB with KV storage)" : "package is too large (max 50 MB)", 413);

  let manifest: any;
  try {
    manifest = JSON.parse(raw);
  } catch {
    return bad("manifest is not valid JSON");
  }
  const id = String(manifest.id || "").trim().toLowerCase();
  const name = String(manifest.name || "").trim();
  const type = String(manifest.type || "other").trim();
  const version = String(manifest.version || "1.0").trim();
  if (!ID.test(id)) return bad("id must be lowercase letters, numbers or - (max 64)");
  if (!name || name.length > 120) return bad("name is required (max 120)");
  if (!TYPES.has(type)) return bad("invalid type");
  if (!version || version.length > 32) return bad("invalid version");

  const head = new Uint8Array(await pkg.slice(0, 4).arrayBuffer());
  if (head[0] !== 0x50 || head[1] !== 0x4b) return bad("package must be a ZIP (.luminaasset)");

  const previewFiles = form.getAll("previews").filter((f): f is File => f instanceof File).slice(0, 5);
  for (const p of previewFiles) {
    if (p.size <= 0 || p.size > MAX_PREVIEW) return bad("preview size is invalid", 413);
    if (!extension(p)) return bad("previews must be PNG, JPG, WEBP or GIF");
  }

  const old = await env.STORE_DB
    .prepare("SELECT owner_id, previews, object_key FROM assets WHERE id = ?")
    .bind(id)
    .first<{ owner_id: string | null; previews: string; object_key: string }>();
  if (old && !a.admin && old.owner_id !== String(a.user!.id)) return bad("forbidden", 403);
  if (!old && !a.admin) {
    const used = await quotaUsed(env, a.user!.id);
    if (used >= DAILY_UPLOADS) return bad("daily_upload_limit", 429);
  }

  const now = Date.now();
  const pkgKey = `assets/${id}/${version}/${id}-${version}.luminaasset`;
  const previewKeys: string[] = [];
  for (let i = 0; i < previewFiles.length; i++) {
    const p = previewFiles[i];
    const key = `previews/${id}/${i}.${extension(p)}`;
    await putObject(env, key, await p.arrayBuffer(), previewType(key), "public, max-age=3600");
    previewKeys.push(key);
  }

  try {
    if (kvOnly) await putObject(env, pkgKey, await pkg.arrayBuffer(), "application/zip", "public, max-age=3600");
    else await putObject(env, pkgKey, pkg.stream(), "application/zip", "public, max-age=3600");

    const tags = Array.isArray(manifest.tags) ? manifest.tags.map((x: unknown) => String(x)).filter(Boolean).slice(0, 12) : [];
    const ownerId = old ? old.owner_id : a.admin ? null : String(a.user!.id);
    const author = String(manifest.author || "Lumina creator").slice(0, 120);
    await env.STORE_DB.prepare(
      `INSERT INTO assets (id, name, type, description, author, version, price, tags, previews, video, license, size, downloads, object_key, owner_id, status, created, updated)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 'published', ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         name = excluded.name, type = excluded.type, description = excluded.description,
         author = excluded.author, version = excluded.version, price = excluded.price,
         tags = excluded.tags, previews = excluded.previews, video = excluded.video,
         license = excluded.license, size = excluded.size, object_key = excluded.object_key,
         owner_id = excluded.owner_id, status = 'published', updated = excluded.updated`,
    )
      .bind(
        id,
        name,
        type,
        String(manifest.description || "").slice(0, 4000),
        author,
        version,
        String(manifest.price || "Free").slice(0, 40),
        JSON.stringify(tags),
        JSON.stringify(previewKeys),
        String(manifest.video || "").slice(0, 500),
        String(manifest.license || "").slice(0, 500),
        pkg.size,
        pkgKey,
        ownerId,
        now,
        now,
      )
      .run();
  } catch (e) {
    try {
      await deleteObjects(env, [pkgKey, ...previewKeys]);
    } catch {
      /* ignore */
    }
    return bad("could not publish asset: " + (e instanceof Error ? e.message : String(e)), 500);
  }

  if (old) {
    const oldKeys: string[] = [];
    try {
      const parsed = JSON.parse(old.previews || "[]");
      if (Array.isArray(parsed)) for (const k of parsed) if (typeof k === "string") oldKeys.push(k);
    } catch {
      /* ignore */
    }
    const keep = new Set(previewKeys);
    const remove = oldKeys.filter((k) => !keep.has(k));
    if (old.object_key && old.object_key !== pkgKey) remove.push(old.object_key);
    if (remove.length) {
      try {
        await deleteObjects(env, remove);
      } catch {
        /* old objects are only garbage */
      }
    }
  }

  const row = await env.STORE_DB.prepare("SELECT * FROM assets WHERE id = ?").bind(id).first<AssetRow>();
  return json({ ok: true, asset: row ? toAsset(row, a.user?.id) : null }, old ? 200 : 201);
}

async function removeAsset(request: Request, env: Env, a: Auth, id: string): Promise<Response> {
  if (!env.STORE_DB) return bad("store_not_configured", 503);
  const row = await env.STORE_DB.prepare("SELECT * FROM assets WHERE id = ?").bind(id).first<AssetRow>();
  if (!row) return bad("not found", 404);
  if (!a.admin && row.owner_id !== String(a.user!.id)) return bad("forbidden", 403);
  const keys = [row.object_key];
  try {
    const parsed = JSON.parse(row.previews || "[]");
    if (Array.isArray(parsed)) for (const k of parsed) if (typeof k === "string") keys.push(k);
  } catch {
    /* ignore */
  }
  await deleteObjects(env, keys);
  await env.STORE_DB.prepare("DELETE FROM assets WHERE id = ?").bind(id).run();
  return json({ ok: true });
}

// ---------------------------------------------------------------- router

export async function handleStore(parts: string[], request: Request, env: Env): Promise<Response> {
  if (parts.length === 0 || parts[0] === "health") {
    return json({
      ok: true,
      service: "lumina-store",
      db: !!env.STORE_DB,
      r2: !!env.STORE_R2,
      kv: !!env.STORE_KV,
      accounts: true,
    });
  }
  if (!env.STORE_DB) return bad("store_not_configured", 503);

  if (parts[0] === "auth" && parts.length === 2) {
    if (parts[1] === "register") return register(request, env);
    if (parts[1] === "login") return login(request, env);
    if (parts[1] === "me") return me(request, env);
    return bad("not found", 404);
  }

  if (parts[0] === "mine" && parts.length === 1) {
    if (request.method !== "GET") return new Response("method not allowed", { status: 405, headers: { allow: "GET" } });
    const a = await auth(request, env);
    if (!a || a.admin || !a.user) return bad("unauthorized", 401);
    const { results } = await env.STORE_DB.prepare(
      "SELECT * FROM assets WHERE owner_id = ? AND status = 'published' ORDER BY updated DESC LIMIT 200",
    )
      .bind(String(a.user.id))
      .all<AssetRow>();
    return json({ assets: (results || []).map((r) => toAsset(r, a.user!.id)) }, 200, "no-store");
  }

  if (parts[0] === "assets" && parts.length === 1) {
    if (request.method === "POST") {
      const a = await auth(request, env);
      if (!a) return bad("unauthorized", 401);
      return publish(request, env, a);
    }
    if (request.method !== "GET") return new Response("method not allowed", { status: 405, headers: { allow: "GET, POST" } });
    const a = await auth(request, env);
    const { results } = await env.STORE_DB.prepare(
      "SELECT * FROM assets WHERE status = 'published' ORDER BY updated DESC LIMIT 200",
    ).all<AssetRow>();
    return json({ assets: (results || []).map((r) => toAsset(r, a?.user?.id)) }, 200, a ? "no-store" : "public, max-age=60");
  }

  if (parts[0] !== "assets" || parts.length < 2) return bad("not found", 404);
  const id = decodeURIComponent(parts[1]);
  if (!ID.test(id)) return bad("not found", 404);

  if (parts.length === 2) {
    if (request.method === "DELETE") {
      const a = await auth(request, env);
      if (!a) return bad("unauthorized", 401);
      return removeAsset(request, env, a, id);
    }
    if (request.method !== "GET") return new Response("method not allowed", { status: 405, headers: { allow: "GET, DELETE" } });
    const a = await auth(request, env);
    const row = await env.STORE_DB.prepare("SELECT * FROM assets WHERE id = ? AND status = 'published'").bind(id).first<AssetRow>();
    if (!row) return bad("not found", 404);
    return json({ asset: toAsset(row, a?.user?.id) }, 200, a ? "no-store" : "public, max-age=60");
  }

  if (parts.length === 3 && parts[2] === "download") {
    if (request.method !== "GET") return new Response("method not allowed", { status: 405, headers: { allow: "GET" } });
    const row = await env.STORE_DB.prepare("SELECT object_key, version FROM assets WHERE id = ? AND status = 'published'").bind(id).first<{ object_key: string; version: string }>();
    if (!row) return bad("not found", 404);
    await env.STORE_DB.prepare("UPDATE assets SET downloads = downloads + 1 WHERE id = ?").bind(id).run();
    return serveObject(env, row.object_key, "application/zip", `${id}-${row.version}.luminaasset`);
  }

  if (parts.length === 4 && parts[2] === "preview") {
    if (request.method !== "GET") return new Response("method not allowed", { status: 405, headers: { allow: "GET" } });
    const index = Number(parts[3]);
    if (!Number.isInteger(index) || index < 0 || index > 4) return bad("not found", 404);
    const row = await env.STORE_DB.prepare("SELECT previews FROM assets WHERE id = ? AND status = 'published'").bind(id).first<{ previews: string }>();
    if (!row) return bad("not found", 404);
    let keys: string[] = [];
    try {
      const parsed = JSON.parse(row.previews || "[]");
      if (Array.isArray(parsed)) keys = parsed.map((x) => String(x));
    } catch {
      /* ignore */
    }
    const key = keys[index];
    if (!key) return bad("not found", 404);
    return serveObject(env, key, previewType(key));
  }

  return bad("not found", 404);
}
