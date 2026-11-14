import type { Env } from "./hub";

/**
 * Lumina Store API (free assets for now).
 *
 *   GET  /store/health
 *   GET  /store/assets
 *   GET  /store/assets/:id
 *   GET  /store/assets/:id/download
 *   GET  /store/assets/:id/preview/:index
 *   POST /store/assets                 (admin only: Authorization: Bearer <STORE_ADMIN_TOKEN>)
 *
 * Publishing uses multipart/form-data:
 *   manifest  JSON string with id, name, type, description, author, version, price, tags,
 *             video, license and optional previews (ignored: previews are uploaded separately)
 *   package   the .luminaasset ZIP
 *   previews  up to 5 image files (png/jpg/webp/gif)
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
  created: number;
  updated: number;
}

export interface Asset {
  id: string;
  name: string;
  type: string;
  description: string;
  author: string;
  version: string;
  price: string;
  tags: string[];
  previews: string[];
  video: string;
  license: string;
  size: number;
  downloads: number;
  download: string;
  updated: number;
}

const json = (body: unknown, status = 200, cache = "no-store") =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": cache },
  });

const bad = (message: string, status = 400) => json({ error: message }, status);

const ID = /^[a-z0-9][a-z0-9-]{0,63}$/;
const MAX_PACKAGE = 50 * 1024 * 1024;
const MAX_PREVIEW = 6 * 1024 * 1024;
const TYPES = new Set(["model3d", "vehicle", "character", "map", "sound", "sprite2d", "prefab", "other"]);

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

function toAsset(row: AssetRow): Asset {
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
  };
}

function admin(request: Request, env: Env): boolean {
  const token = env.STORE_ADMIN_TOKEN;
  if (!token) return false;
  const header = request.headers.get("authorization") || "";
  return header === `Bearer ${token}`;
}

async function publish(request: Request, env: Env): Promise<Response> {
  if (!admin(request, env)) return bad("unauthorized", 401);
  if (!env.STORE_DB || !env.STORE_R2) return bad("store_not_configured", 503);

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
  if (pkg.size <= 0 || pkg.size > MAX_PACKAGE) return bad("package size is invalid", 413);

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

  // A .luminaasset is a ZIP: reject anything else before touching R2.
  const head = new Uint8Array(await pkg.slice(0, 4).arrayBuffer());
  if (head[0] !== 0x50 || head[1] !== 0x4b) return bad("package must be a ZIP (.luminaasset)");

  const previewFiles = form.getAll("previews").filter((f): f is File => f instanceof File).slice(0, 5);
  for (const p of previewFiles) {
    if (p.size <= 0 || p.size > MAX_PREVIEW) return bad("preview size is invalid", 413);
    if (!extension(p)) return bad("previews must be PNG, JPG, WEBP or GIF");
  }

  const now = Date.now();
  const pkgKey = `assets/${id}/${version}/${id}-${version}.luminaasset`;
  const previewKeys: string[] = [];
  for (let i = 0; i < previewFiles.length; i++) {
    const p = previewFiles[i];
    const key = `previews/${id}/${i}.${extension(p)}`;
    await env.STORE_R2.put(key, await p.arrayBuffer(), {
      httpMetadata: { contentType: previewType(key), cacheControl: "public, max-age=3600" },
    });
    previewKeys.push(key);
  }

  const old = await env.STORE_DB.prepare("SELECT previews, object_key FROM assets WHERE id = ?").bind(id).first<{ previews: string; object_key: string }>();

  try {
    await env.STORE_R2.put(pkgKey, pkg.stream(), {
      httpMetadata: { contentType: "application/zip", cacheControl: "public, max-age=3600" },
    });
    const tags = Array.isArray(manifest.tags) ? manifest.tags.map((x: unknown) => String(x)).filter(Boolean).slice(0, 12) : [];
    await env.STORE_DB.prepare(
      `INSERT INTO assets (id, name, type, description, author, version, price, tags, previews, video, license, size, downloads, object_key, status, created, updated)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'published', ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         name = excluded.name, type = excluded.type, description = excluded.description,
         author = excluded.author, version = excluded.version, price = excluded.price,
         tags = excluded.tags, previews = excluded.previews, video = excluded.video,
         license = excluded.license, size = excluded.size, object_key = excluded.object_key,
         status = 'published', updated = excluded.updated`,
    )
      .bind(
        id,
        name,
        type,
        String(manifest.description || "").slice(0, 4000),
        String(manifest.author || "").slice(0, 120),
        version,
        String(manifest.price || "Free").slice(0, 40),
        JSON.stringify(tags),
        JSON.stringify(previewKeys),
        String(manifest.video || "").slice(0, 500),
        String(manifest.license || "").slice(0, 500),
        pkg.size,
        pkgKey,
        now,
        now,
      )
      .run();
  } catch (e) {
    // Do not leave an unreferenced package behind.
    try {
      await env.STORE_R2.delete(pkgKey);
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
        await env.STORE_R2.delete(remove);
      } catch {
        /* old objects are only garbage */
      }
    }
  }

  const row = await env.STORE_DB.prepare("SELECT * FROM assets WHERE id = ?").bind(id).first<AssetRow>();
  return json({ ok: true, asset: row ? toAsset(row) : null }, 201);
}

async function serveObject(env: Env, key: string, contentType: string, filename?: string): Promise<Response> {
  if (!env.STORE_R2) return bad("store_not_configured", 503);
  const obj = await env.STORE_R2.get(key);
  if (!obj) return new Response("not found", { status: 404 });
  const headers = new Headers();
  obj.writeHttpMetadata(headers);
  headers.set("etag", obj.httpEtag);
  headers.set("cache-control", "public, max-age=3600");
  if (contentType) headers.set("content-type", contentType);
  if (filename) headers.set("content-disposition", `attachment; filename="${filename.replace(/[^A-Za-z0-9._-]/g, "_")}"`);
  return new Response(obj.body, { headers });
}

export async function handleStore(parts: string[], request: Request, env: Env): Promise<Response> {
  if (parts.length === 0 || parts[0] === "health") {
    return json({ ok: true, service: "lumina-store", db: !!env.STORE_DB, r2: !!env.STORE_R2 });
  }
  if (!env.STORE_DB) return bad("store_not_configured", 503);

  if (parts[0] === "assets" && parts.length === 1) {
    if (request.method === "POST") return publish(request, env);
    if (request.method !== "GET") return new Response("method not allowed", { status: 405, headers: { allow: "GET, POST" } });
    const { results } = await env.STORE_DB.prepare(
      `SELECT * FROM assets WHERE status = 'published' ORDER BY updated DESC LIMIT 200`,
    ).all<AssetRow>();
    return json({ assets: (results || []).map(toAsset) }, 200, "public, max-age=60");
  }

  if (parts[0] !== "assets" || parts.length < 2) return bad("not found", 404);
  const id = decodeURIComponent(parts[1]);
  if (!ID.test(id)) return bad("not found", 404);

  if (parts.length === 2) {
    if (request.method !== "GET") return new Response("method not allowed", { status: 405, headers: { allow: "GET" } });
    const row = await env.STORE_DB.prepare("SELECT * FROM assets WHERE id = ? AND status = 'published'").bind(id).first<AssetRow>();
    if (!row) return bad("not found", 404);
    return json({ asset: toAsset(row) }, 200, "public, max-age=60");
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
