# Nebulon Store backend

The Store lives in the same Worker as the remote control. It has no demo data: only packages you
publish appear in the app. Accounts and metadata use D1; packages and previews use KV (free, no card)
or R2 when you have it enabled.

## 1. Create D1 and KV

```bash
cd server
npx wrangler d1 create lumina-store
npx wrangler kv namespace create STORE_KV
```

Copy the `database_id` and KV `id` printed by those commands and uncomment the bindings in
`wrangler.toml`, replacing `REPLACE_WITH_D1_ID` and `REPLACE_WITH_KV_ID`.

Apply the schema:

```bash
npx wrangler d1 execute lumina-store --remote --file=store/schema.sql
```

If you already created the old assets-only schema before accounts existed, apply the columns once:

```bash
npx wrangler d1 execute lumina-store --remote --command="ALTER TABLE assets ADD COLUMN owner_id TEXT"
```

`store/schema.sql` is idempotent for fresh installs.

R2 is optional. If you later enable R2, bind it as `STORE_R2` and the Worker uses it automatically.
Without R2 the Worker stores packages in KV; the package limit is 24 MB because KV values are capped
at 25 MB. Free KV limits: 1 GB total, 100k reads/day, 1k writes/day.

## 2. Optional admin token

```bash
npx wrangler secret put STORE_ADMIN_TOKEN
```

The app no longer asks for this token: creators sign in with an email/password account and publish
with their API key. The admin token is only for recovery/curl and can publish or remove any asset.

## 3. Deploy

```bash
npm run typecheck
npm run deploy
```

Check `https://lumina.brayanhernandez385.workers.dev/store/health`: `db` and either `kv` or `r2`
must be `true`.

## 4. Accounts

```bash
curl -X POST https://lumina.brayanhernandez385.workers.dev/store/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"creator@example.com","password":"at-least-8-chars"}'
```

The response contains `api_key`. Keep it private; it signs publishing/deleting requests.

```bash
curl -X POST https://lumina.brayanhernandez385.workers.dev/store/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"creator@example.com","password":"at-least-8-chars"}'

curl https://lumina.brayanhernandez385.workers.dev/store/auth/me \
  -H "Authorization: Bearer $API_KEY"
```

Rules: free assets only, 5 new assets per creator per day, packages max 24 MB with KV (50 MB with
R2), max 5 previews of 6 MB each. Updating an existing asset does not count against the daily quota.

## 5. Publish a real asset

The package is a ZIP with this layout:

```
manifest.json
preview/01.png ... (optional; the API also accepts preview uploads)
content/            <- copied into the project root
LICENSE.txt         (optional)
```

`content/` must use the project layout, for example `content/models/car.glb`,
`content/sounds/coin.wav` or `content/prefabs/car.prefab`.

From the app: open **Store -> publish icon -> sign in -> choose package + previews -> Publish**.

With curl (`manifest` is a JSON string, `package` is the file, `previews` are optional
PNG/JPG/WEBP/GIF files, max 5):

```bash
curl -X POST https://lumina.brayanhernandez385.workers.dev/store/assets \
  -H "Authorization: Bearer $API_KEY" \
  -F 'manifest={"id":"my-car","name":"My Car","type":"vehicle","description":"...","author":"Me","version":"1.0","price":"Free","tags":["3d","car"],"video":"","license":"CC0"}' \
  -F "package=@my-car.luminaasset" \
  -F "previews=@preview1.png" \
  -F "previews=@preview2.png"
```

IDs are lowercase letters, numbers and `-` only. Uploading the same id again updates your asset.
A creator can only update or delete their own assets. `GET /store/mine` lists the signed-in creator's
assets, and `DELETE /store/assets/:id` removes one.
