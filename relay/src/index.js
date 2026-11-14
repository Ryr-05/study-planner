// Noctil AI relay. When Gemini is out of free messages, the app sends the same chat here.
// It tries Groq (Llama) first and Cloudflare Workers AI as the backup, and returns the model's
// JSON reply. Keys live only here. Nothing is stored or logged.

const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
// Groq's free models, best first. Each has its own limit, so the second picks up when the first is busy.
// (Groq no longer offers Llama chat models; Cloudflare's Llama is the last backup below.)
const GROQ_MODELS = ["openai/gpt-oss-120b", "openai/gpt-oss-20b"];
const CF_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
const MAX_BODY_CHARS = 80_000;
const MAX_MESSAGES = 30;
const JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/") return reply({ ok: true, name: "noctil-relay" });
    if (request.method !== "POST" || url.pathname !== "/chat") return reply({ error: "not found" }, 404);

    const verified = await verifyAppCheck(request.headers.get("X-Firebase-AppCheck"), env).catch(() => false);
    if (env.REQUIRE_APP_CHECK === "true" && !verified) return reply({ error: "unverified app" }, 401);

    const text = await request.text();
    if (text.length > MAX_BODY_CHARS) return reply({ error: "too large" }, 413);
    let body;
    try { body = JSON.parse(text); } catch { return reply({ error: "bad json" }, 400); }
    const messages = clean(body.messages);
    if (!messages) return reply({ error: "bad messages" }, 400);

    // 1) Groq, if a key is set. Any failure (quota, outage) falls through to Workers AI.
    let groqError = env.GROQ_API_KEY ? null : "no key";
    for (const model of env.GROQ_API_KEY ? GROQ_MODELS : []) {
      try {
        const r = await fetch(GROQ_URL, {
          method: "POST",
          headers: { Authorization: `Bearer ${env.GROQ_API_KEY}`, "Content-Type": "application/json" },
          body: JSON.stringify({ model, messages, temperature: 0.3, max_tokens: 2000, reasoning_effort: "low", response_format: { type: "json_object" } }),
        });
        if (r.ok) {
          const d = await r.json();
          const content = d?.choices?.[0]?.message?.content;
          if (typeof content === "string" && content.trim()) return reply({ provider: `groq ${model}`, content, verified });
          groqError = "empty reply";
        } else {
          // Just the status and Groq's short error text, never the key.
          const err = await r.json().catch(() => ({}));
          groqError = `${r.status} ${err?.error?.code || err?.error?.type || ""} ${(err?.error?.message || "").slice(0, 160)}`.trim();
        }
      } catch (e) { groqError = "network"; }
    }

    // 2) Cloudflare Workers AI (free daily allowance), no key needed.
    try {
      const out = await env.AI.run(CF_MODEL, { messages, temperature: 0.3, max_tokens: 1500, response_format: { type: "json_object" } });
      const r = out?.response;
      const content = typeof r === "string" ? r : r ? JSON.stringify(r) : "";
      if (content.trim()) return reply({ provider: "workers-ai", content, verified, groq: groqError });
    } catch (e) { /* both busy */ }

    return reply({ error: "busy" }, 503);
  },
};

function reply(obj, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });
}

/** Only system/user/assistant text messages, capped in number and size. */
function clean(list) {
  if (!Array.isArray(list) || list.length === 0) return null;
  const out = [];
  for (const m of list.slice(-MAX_MESSAGES)) {
    if (!m || typeof m.content !== "string" || !["system", "user", "assistant"].includes(m.role)) return null;
    out.push({ role: m.role, content: m.content.slice(0, 40_000) });
  }
  return out;
}

let jwksCache = null;
let jwksAt = 0;

/** Checks a Firebase App Check token: RS256, from this Firebase project, not expired. */
async function verifyAppCheck(token, env) {
  if (!token || !env.FIREBASE_PROJECT_NUMBER) return false;
  const [h, p, s] = token.split(".");
  if (!h || !p || !s) return false;
  const header = JSON.parse(b64text(h));
  const payload = JSON.parse(b64text(p));
  const project = env.FIREBASE_PROJECT_NUMBER;
  const now = Math.floor(Date.now() / 1000);
  if (header.alg !== "RS256") return false;
  if (payload.iss !== `https://firebaseappcheck.googleapis.com/${project}`) return false;
  const aud = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
  if (!aud.includes(`projects/${project}`)) return false;
  if (!payload.exp || payload.exp < now) return false;
  if (!jwksCache || Date.now() - jwksAt > 6 * 3600 * 1000) {
    jwksCache = await (await fetch(JWKS_URL)).json();
    jwksAt = Date.now();
  }
  const jwk = (jwksCache.keys || []).find((k) => k.kid === header.kid);
  if (!jwk) return false;
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  return crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64bytes(s), new TextEncoder().encode(`${h}.${p}`));
}

function b64bytes(s) {
  const b = atob(s.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(s.length / 4) * 4, "="));
  return Uint8Array.from(b, (c) => c.charCodeAt(0));
}

function b64text(s) {
  return new TextDecoder().decode(b64bytes(s));
}
