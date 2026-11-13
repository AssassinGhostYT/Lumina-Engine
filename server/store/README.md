# Lumina Store backend

The Store lives in the same Worker as the remote control. It has no demo data: only packages you
publish appear in the app.

## 1. Create the D1 database and R2 bucket

```bash
cd server
npx wrangler d1 create lumina-store
npx wrangler r2 bucket create lumina-store
```

Copy the `database_id` printed by the first command and uncomment the two bindings in
`wrangler.toml`, replacing `REPLACE_WITH_D1_ID`.

Apply the schema:

```bash
npx wrangler d1 execute lumina-store --remote --file=store/schema.sql
```

## 2. Configure the admin token

```bash
npx wrangler secret put STORE_ADMIN_TOKEN
```

Choose a long random value. Anyone with this token can publish assets, so keep it private.

## 3. Deploy

```bash
npm run typecheck
npm run deploy
```

Check `https://lumina.brayanhernandez385.workers.dev/store/health`: `db` and `r2` must be `true`.

## 4. Publish a real asset

The package is a ZIP with this layout:

```
manifest.json
preview/01.png ... (optional; the API also accepts preview uploads)
content/            <- copied into the project root
LICENSE.txt         (optional)
```

`content/` must use the project layout, for example `content/models/car.glb`,
`content/sounds/coin.wav` or `content/prefabs/car.prefab`.

Publish with curl (`manifest` is a JSON string, `package` is the file, `previews` are optional
PNG/JPG/WEBP/GIF files, max 5):

```bash
curl -X POST https://lumina.brayanhernandez385.workers.dev/store/assets \
  -H "Authorization: Bearer $STORE_ADMIN_TOKEN" \
  -F 'manifest={"id":"my-car","name":"My Car","type":"vehicle","description":"...","author":"Me","version":"1.0","price":"Free","tags":["3d","car"],"video":"","license":"CC0"}' \
  -F "package=@my-car.luminaasset" \
  -F "previews=@preview1.png" \
  -F "previews=@preview2.png"
```

IDs are lowercase letters, numbers and `-` only. Uploading the same id again updates the asset.
