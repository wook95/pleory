// Pleory LLM 프록시(Cloudflare Worker). 진짜 Claude·Gemini 키는 Worker secret 에만 있고 APK 에는 들어가지 않는다.
// 앱은 APP_TOKEN 만 갖는다. 토큰이 새도 이 Worker 로는 고정된 모델·출력 상한·IP 호출 제한 안에서만 부를 수 있다.
// 요청·응답 본문(발화)은 로그로 남기지 않는다.

const CLAUDE_MODEL = "claude-haiku-5-5";
const GEMINI_MODEL = "gemini-2.5-flash";
const MAX_TOKENS = 2048;
const MAX_BODY_BYTES = 32_000;

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    // 앱 토큰 위치: Claude Java SDK 는 Authorization: Bearer(2026-11-12 실측, x-api-key 아님), Gemini REST 는 x-goog-api-key
    const bearer = req.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
    const token = bearer ?? req.headers.get("x-api-key") ?? req.headers.get("x-goog-api-key");
    if (!env.APP_TOKEN || token !== env.APP_TOKEN) return text(401, "unauthorized");

    const { success } = await env.RL.limit({ key: req.headers.get("cf-connecting-ip") ?? "unknown" });
    if (!success) return text(429, "rate limited");

    // 앱의 warmUp(models.retrieve). 과금되지 않는다
    if (req.method === "GET" && url.pathname === `/v1/models/${CLAUDE_MODEL}`) {
      return toClaude(req, env, url.pathname, undefined);
    }
    if (req.method === "POST" && url.pathname === "/v1/messages") {
      const body = await readJson(req);
      if (!body) return text(400, "bad body");
      if (body.model !== CLAUDE_MODEL || body.stream || body.tools || !(body.max_tokens <= MAX_TOKENS)) {
        return text(403, "request not allowed");
      }
      return toClaude(req, env, url.pathname, JSON.stringify(body));
    }
    if (req.method === "POST" && url.pathname === `/v1beta/models/${GEMINI_MODEL}:generateContent`) {
      const body = await readJson(req);
      if (!body || body.tools) return text(400, "bad body");
      body.generationConfig = { ...body.generationConfig, maxOutputTokens: MAX_TOKENS };
      return fetch(`https://generativelanguage.googleapis.com${url.pathname}`, {
        method: "POST",
        headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_KEY },
        body: JSON.stringify(body),
      });
    }
    return text(404, "not found");
  },
};

function toClaude(req, env, path, body) {
  const headers = { "x-api-key": env.CLAUDE_KEY, "content-type": "application/json" };
  for (const h of ["anthropic-version", "anthropic-beta"]) {
    const v = req.headers.get(h);
    if (v) headers[h] = v;
  }
  return fetch(`https://api.anthropic.com${path}`, { method: req.method, headers, body });
}

async function readJson(req) {
  const raw = await req.text();
  if (raw.length > MAX_BODY_BYTES) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function text(status, msg) {
  return new Response(msg, { status });
}
