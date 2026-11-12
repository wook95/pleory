# LLM 프록시

앱이 Claude·Gemini 를 직접 부르지 않고 이 Cloudflare Worker 를 거친다. 진짜 API 키는 Worker secret 에만 있다.

```
앱 ──(APP_TOKEN)──▶ Worker ──(CLAUDE_KEY / GEMINI_KEY)──▶ Anthropic / Google
```

Worker 가 막는 것: 앱 토큰 불일치(401), IP 당 분당 30회 초과(429),
고정 모델(`claude-haiku-5-5`, `gemini-2.5-flash`) 외 호출·스트리밍·도구 사용(403), 출력 2048 토큰 초과.

실행 위치는 `wrangler.toml` 의 `[placement] region = "aws:ap-northeast-2"` 로 고정한다. 고정하지 않으면 한국 요청이
홍콩(HKG)에서 실행돼 Gemini 가 `User location is not supported` 로 막는다(2026-11-12 실측). 이 설정은 wrangler 4.154 이상이 필요하다.

## 배포

```bash
cd proxy
npx wrangler@4.154.0 login
npx wrangler@4.154.0 secret put CLAUDE_KEY
npx wrangler@4.154.0 secret put GEMINI_KEY
npx wrangler@4.154.0 secret put APP_TOKEN    # 예: openssl rand -hex 24
npx wrangler@4.154.0 deploy                  # 출력된 https://pleory-llm-proxy.<계정>.workers.dev 를 기록
```

## 앱 설정 (`local.properties`, 커밋하지 않음)

```
llm.proxyUrl=https://pleory-llm-proxy.<계정>.workers.dev
llm.proxyToken=<APP_TOKEN 과 같은 값>
```

키를 바꿀 때는 `secret put` 만 다시 하면 된다. 앱은 다시 빌드하지 않아도 된다.
