# AI: built-in assistant and full editor control

Lumina Engine has two different AI paths. They solve different problems:

| | Built-in assistant | Remote MCP (Claude / OpenCode) |
| --- | --- | --- |
| Where | AI panel inside the editor | An external AI app |
| What it can do | Chat, explain, write code or blocks that you copy | Read the project and **use the editor tools**: create scenes, elements, events, UI, build the APK... |
| Providers | Pollinations (no key), OpenRouter, Gemini, OpenCode Zen, any OpenAI-compatible server | Any MCP client; Claude and OpenCode are the tested ones |
| Cost | Your provider key (OpenRouter/Gemini have free tiers) | Free OpenCode Zen models, or your own Claude/GPT key |

Most people use both: the built-in panel for quick questions, and the remote connection when they want the AI to build the game.

## 1. Built-in assistant (chat only)

Open the **AI assistant** panel and press the key/settings icon.

1. Choose **Provider**.
2. **Pollinations (free, no key)**: no account, no card, no key. Pick its model and start chatting. This is the only truly keyless option; the anonymous tier is rate-limited.
3. **OpenRouter** (recommended for quality): one free key from <https://openrouter.ai/keys>; many models are marked **FREE**.
4. **OpenCode Zen**: press **Get a key**, sign in at <https://opencode.ai/console>, add a little credit and copy the API key. Paste it in the app.
5. Press **Save & pick model**. The app downloads the live model list.

The key is stored only on the phone and is sent directly to the provider.

### OpenCode Zen and the free models

OpenCode Zen (now also called **Console**) has two kinds of models:

* **Free models** (`big-pickle`, `deepseek-v4.1-flash-free`, `glm-5.3-flash-free`, `mimo-v2.6-flash-free`, `nemotron-*`, ...). Their free usage is reserved for **OpenCode itself**. Calling them directly from another app returns `429 FreeUsageLimitError`.
* **Paid models** (`deepseek-v4.1-flash`, `qwen3.8-flash`, `gemini-3.5-flash-lite`, ...). They are pay-as-you-go and very cheap (for example DeepSeek V4.1 Flash is $0.30 / $1.20 per million input/output tokens). A normal chat costs a fraction of a cent.

So:

* To use the **built-in panel** with Zen, add a small credit and choose a cheap **paid** model. It will last much longer than a Claude/GPT subscription.
* To use the **free Zen models**, connect the editor to OpenCode (section 2). That path is free, needs no API key, and is the one that can actually edit the project.
* If you want free chat **inside the app with no key at all**, choose the **Pollinations** provider. It is not OpenCode, but it needs nothing.

## 2. Full editor control with OpenCode (recommended for free use)

The `server/` folder is a small Cloudflare Worker that relays MCP tool calls to the phone. Deploy it once (free plan):

```bash
cd server
npm install
npx wrangler login
npx wrangler deploy
```

Then in the app open **AI remote control**:

1. Paste the `https://....workers.dev` URL and press **Save**.
2. Turn the switch on and keep the editor open.
3. Press **Copy OpenCode MCP config**.

Put the copied block in `opencode.json` (project root) or `~/.config/opencode/opencode.json`:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "lumina": {
      "type": "remote",
      "url": "https://<your-worker>.workers.dev/v1/mcp/<secret>",
      "enabled": true
    }
  }
}
```

Then:

```bash
opencode
/connect          # choose OpenCode Zen and paste the key, or run: opencode auth login
/models           # pick a FREE model
```

Ask in plain language ("make a 2D platformer with 3 levels", "add a main menu", "fix the jump"). OpenCode calls the Lumina tools and the plan appears in the phone editor. Keep the editor on screen; split-screen next to the chat works.

## 3. Full editor control with Claude or GPT

Deploy the same server, then add the private connector link (`https://<worker>/v1/mcp/<secret>`) as a custom connector:

* **Claude:** Settings → Connectors → Add custom connector → paste the link (no login).
* **ChatGPT / other MCP clients:** use the same link if the client supports remote MCP. If it only makes HTTP requests, use the plain API: `POST https://<worker>/v1/api/<secret>/<tool>` with the JSON arguments as the body.

Press **Copy work command for Claude** and paste it in the chat before your request. The connector already carries the rules, so no extra link is needed.

## Troubleshooting

| Symptom | Meaning / fix |
| --- | --- |
| `429 FreeUsageLimitError` | You picked a free Zen model outside OpenCode. Use OpenCode, or add credit and pick a paid model. |
| "Type the server link first" | Deploy `server/` and paste your `workers.dev` URL. The app has no default server. |
| "The server needs an update" | Deploy the latest `server/`; its API version is older than the app's. |
| AI connects but nothing happens | The editor must be open and the switch on. Check the status dot is green. |
| 401 / invalid key | Re-copy the key from the provider console; do not include spaces. |

The connector URL is a password. Do not paste it in public chats or commit `opencode.json` with the secret in it.
