import {
  AbsoluteFill,
  Audio,
  Easing,
  Freeze,
  interpolate,
  OffthreadVideo,
  Sequence,
  staticFile,
  useCurrentFrame,
} from 'remotion';
import assets from './assets.json';
import qr from './qrText.json';
import {appClock, buildCuts, place, TAGLINE, type Cam, type PlacedCut, type Tap} from './cuts';
import {COLOR, FONT, FPS, FRAME_H, FRAME_TOP, FRAME_W, SUB_Y} from './theme';

const has = (f: string) => (assets as Record<string, boolean>)[f] === true;
const EASE = Easing.bezier(0.42, 0, 0.18, 1);
const BGM_BASE = 0.3;
const dbToGain = (db: number) => Math.pow(10, db / 20);
const footageOf = (c: PlacedCut) => `footage-d${c.day ?? 1}.mp4`;
const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

const camAt = (c: PlacedCut, frame: number): Cam => {
  const from = c.camFrom ?? {scale: 1, cx: 0.5, cy: 0.5};
  const to = c.camTo ?? from;
  const p = interpolate(frame, [(c.camStart ?? 0) * FPS, (c.camMove ?? c.dur) * FPS], [0, 1], {easing: EASE, ...clamp});
  return {
    scale: from.scale + (to.scale - from.scale) * p,
    cx: from.cx + (to.cx - from.cx) * p,
    cy: from.cy + (to.cy - from.cy) * p,
  };
};

const camTransform = ({scale, cx, cy}: Cam, w = FRAME_W, h = FRAME_H) => {
  const half = 0.5 / scale;
  const x = Math.min(Math.max(cx, half), 1 - half);
  const y = Math.min(Math.max(cy, half), 1 - half);
  const tx = w / 2 - scale * x * w;
  const ty = h / 2 - scale * y * h;
  return `translate(${tx}px, ${ty}px) scale(${scale})`;
};

/** 컷 안에서 재생 속도와, 원본이 모자랄 때 끝 프레임에 멈출 시점 */
const playback = (c: PlacedCut) => {
  const start = c.srcStart ?? 0;
  const span = Math.max(0, (c.srcEnd ?? start) - start);
  if (c.freeze || span <= 0) return {rate: 1, playFrames: 0};
  // 앱 원음이 있는 컷은 1배속을 지키고, 원본이 모자라면 마지막 프레임에 멈춘다.
  const rate = c.appAudio ? 1 : span / c.dur;
  return {rate, playFrames: Math.min(c.frames, Math.round((span / rate) * FPS))};
};

const FootageLayer: React.FC<{c: PlacedCut}> = ({c}) => {
  const start = c.srcStart ?? 0;
  const {rate, playFrames} = playback(c);
  const file = footageOf(c);
  const src = staticFile(file);
  const trim = Math.round(start * FPS);
  if (!has(file)) {
    return (
      <AbsoluteFill style={{background: '#161513', alignItems: 'center', justifyContent: 'center'}}>
        <div style={{color: '#555', fontFamily: FONT, fontSize: 28}}>{file} 없음 · 컷 {c.n}</div>
      </AbsoluteFill>
    );
  }
  const endFrame = Math.round(((c.srcEnd ?? start) - start) * FPS);
  const fade = c.appFadeAt;
  const volume = fade == null ? 1 : (f: number) => interpolate(f, [fade * FPS, (fade + 0.5) * FPS], [1, 0], clamp);
  return (
    <>
      {playFrames > 0 && (
        <Sequence durationInFrames={playFrames} layout="none">
          <OffthreadVideo src={src} trimBefore={trim} playbackRate={rate} muted={!c.appAudio} volume={volume}
            style={{width: '100%', height: '100%'}} />
        </Sequence>
      )}
      {playFrames < c.frames && (
        <Sequence from={playFrames} layout="none">
          <Freeze frame={0}>
            <OffthreadVideo src={src} trimBefore={trim + (playFrames > 0 ? endFrame : 0)} muted
              style={{width: '100%', height: '100%'}} />
          </Freeze>
        </Sequence>
      )}
    </>
  );
};

/** 탭 표시 원. 카메라 층 안에 있어 화면과 같이 움직인다. 원 크기는 확대 비율만큼 줄여 화면에서 같은 크기로 보이게 한다 */
const TapRing: React.FC<{tap: Tap; scale: number}> = ({tap, scale}) => {
  const f = useCurrentFrame() - tap.at * FPS;
  const len = 0.45 * FPS;
  if (f < 0 || f > len) return null;
  const p = f / len;
  const d = (56 + 40 * p) / scale;
  return (
    <div style={{
      position: 'absolute', left: tap.x * FRAME_W - d / 2, top: tap.y * FRAME_H - d / 2,
      width: d, height: d, borderRadius: '50%', border: `${3 / scale}px solid #fff`, opacity: 0.9 * (1 - p),
      // 밝은 버튼(동의·듣고 있어요) 위에서도 보이게 바깥쪽에 어두운 테를 두른다
      boxShadow: `0 0 0 ${2 / scale}px rgba(0,0,0,0.55), inset 0 0 0 ${2 / scale}px rgba(0,0,0,0.35)`,
    }} />
  );
};

const FootageCut: React.FC<{c: PlacedCut}> = ({c}) => {
  const frame = useCurrentFrame();
  const cam = camAt(c, frame);
  return (
    <AbsoluteFill style={{background: COLOR.bg}}>
      <div style={{
        position: 'absolute', left: (1920 - FRAME_W) / 2, top: FRAME_TOP, width: FRAME_W, height: FRAME_H,
        borderRadius: 14, overflow: 'hidden', background: '#111',
      }}>
        <div style={{position: 'absolute', inset: 0, transformOrigin: '0 0', transform: camTransform(cam)}}>
          <FootageLayer c={c} />
          {(c.taps ?? []).map((p) => <TapRing key={p.at} tap={p} scale={cam.scale} />)}
        </div>
      </div>
      {c.note && (
        <div style={{position: 'absolute', right: (1920 - FRAME_W) / 2 + 20, top: FRAME_TOP + 16, fontFamily: FONT,
          fontSize: 22, color: COLOR.small, textShadow: '0 1px 6px rgba(0,0,0,0.9)'}}>{c.note}</div>
      )}
    </AbsoluteFill>
  );
};

// 컷 17: 왼쪽은 QR 모달 정지 프레임(QR 카드 부분), 오른쪽은 폰 틀 안에 QR 을 디코드한 글자 그대로(src/qrText.json)
const QR_REGION = {x: 0.095, y: 0.19, w: 0.44, h: 0.62}; // 녹화 프레임 비율. 흰 QR 카드(0.12~0.51, 0.23~0.77)만, 오른쪽 모달 글자는 뺀다
const QrCut: React.FC<{c: PlacedCut}> = ({c}) => {
  const frame = useCurrentFrame();
  const o = interpolate(frame, [0, 0.3 * FPS], [0, 1], clamp);
  const boxH = 700;
  const k = boxH / (QR_REGION.h * FRAME_H); // 녹화 프레임(FRAME_W x FRAME_H 로 그린 것)을 키우는 배율
  const boxW = Math.round(QR_REGION.w * FRAME_W * k);
  const top = FRAME_TOP + (FRAME_H - boxH) / 2;
  const left = Math.round((1920 - (boxW + 120 + 440)) / 2);
  const lines = qr.text.split('\n');
  return (
    <AbsoluteFill style={{background: COLOR.bg}}>
      <div style={{position: 'absolute', left, top, width: boxW, height: boxH, borderRadius: 14, overflow: 'hidden', background: '#111'}}>
        <div style={{position: 'absolute', width: FRAME_W, height: FRAME_H, transformOrigin: '0 0',
          transform: `scale(${k}) translate(${-QR_REGION.x * FRAME_W}px, ${-QR_REGION.y * FRAME_H}px)`}}>
          <FootageLayer c={c} />
        </div>
      </div>
      <div style={{position: 'absolute', left: left + boxW + 120, top: top - 6, width: 440, opacity: o, fontFamily: FONT}}>
        <div style={{fontSize: 22, color: COLOR.small, marginBottom: 14, letterSpacing: '0.01em'}}>QR 에 담긴 글자</div>
        <div style={{width: 440, height: boxH - 30, borderRadius: 44, border: '2px solid #4A4A4A', background: '#0E0E0E',
          padding: '58px 30px 30px', boxSizing: 'border-box', position: 'relative'}}>
          <div style={{position: 'absolute', top: 22, left: '50%', width: 80, height: 8, marginLeft: -40, borderRadius: 4, background: '#2A2A2A'}} />
          {lines.map((l, i) => l === '' ? <div key={i} style={{height: 10}} /> : (
            <div key={i} style={{fontSize: i === 0 ? 22 : 17, fontWeight: i === 0 || /^\[/.test(l) ? 600 : 400, lineHeight: 1.4,
              color: i === 0 ? COLOR.text : '#D6D6D6', marginBottom: i === 0 ? 8 : 1, wordBreak: 'keep-all'}}>{l}</div>
          ))}
        </div>
      </div>
    </AbsoluteFill>
  );
};

const fadeInOut = (frame: number, frames: number, fade: number) =>
  interpolate(frame, [0, fade * FPS, frames - fade * FPS, frames], [0, 1, 1, 0], clamp);

const Subtitle: React.FC<{c: PlacedCut}> = ({c}) => {
  const frame = useCurrentFrame();
  if (c.kind === 'title' || c.kind === 'end') return null;
  const t = frame / FPS;
  let lines = c.sub;
  let o = fadeInOut(frame, c.frames, 0.3);
  if (c.subs) {
    const i = c.subs.filter((s) => s.at <= t).length - 1;
    if (i < 0) return null;
    lines = c.subs[i].lines;
    const from = c.subs[i].at * FPS;
    const to = i + 1 < c.subs.length ? c.subs[i + 1].at * FPS : c.frames;
    o = fadeInOut(frame - from, to - from, i + 1 < c.subs.length ? 0.15 : 0.3);
  }
  if (!lines) return null;
  return (
    <div style={{
      position: 'absolute', left: 0, right: 0, top: SUB_Y, transform: 'translateY(-50%)',
      textAlign: 'center', fontFamily: FONT, fontSize: 54, lineHeight: 1.32,
      fontWeight: c.driver ? 500 : 600, color: c.driver ? COLOR.driver : COLOR.text,
      textShadow: '0 2px 14px rgba(0,0,0,0.9), 0 0 3px rgba(0,0,0,0.7)',
      opacity: o, letterSpacing: '-0.01em',
    }}>
      {lines.map((l) => <div key={l}>{l}</div>)}
    </div>
  );
};

const TimeChip: React.FC<{c: PlacedCut}> = ({c}) => {
  const frame = useCurrentFrame();
  if (!c.chip) return null;
  const {rate, playFrames} = playback(c);
  const start = c.srcStart ?? 0;
  const src = c.chipAt ?? start + (Math.min(frame, playFrames) / FPS) * rate;
  return (
    <div style={{
      position: 'absolute', left: 64, bottom: 52, fontFamily: FONT, fontSize: 28, fontWeight: 500,
      color: COLOR.chip, fontVariantNumeric: 'tabular-nums', letterSpacing: '0.02em',
      opacity: fadeInOut(frame, c.frames, 0.2),
    }}>
      {appClock(src, c.day ?? 1)}
    </div>
  );
};

const Title: React.FC<{c: PlacedCut}> = ({c}) => {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center'}}>
      <div style={{fontFamily: FONT, fontSize: 68, fontWeight: 600, color: COLOR.text, lineHeight: 1.4, textAlign: 'center',
        opacity: fadeInOut(frame, c.frames, 0.4), letterSpacing: '-0.015em'}}>
        {(c.sub ?? []).map((l) => <div key={l}>{l}</div>)}
      </div>
    </AbsoluteFill>
  );
};

const EndCard: React.FC<{c: PlacedCut}> = ({c}) => {
  const frame = useCurrentFrame();
  const o = fadeInOut(frame, c.frames, 0.4);
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', fontFamily: FONT, opacity: o}}>
      <div style={{fontSize: 64, fontWeight: 600, color: COLOR.text, letterSpacing: '-0.015em'}}>{TAGLINE}</div>
      <div style={{marginTop: 72, fontSize: 44, fontWeight: 700, color: COLOR.text, letterSpacing: '0.04em'}}>Pleory</div>
      <div style={{position: 'absolute', bottom: 64, fontSize: 22, fontWeight: 400, color: COLOR.small, letterSpacing: '0.02em'}}>
        Pleos Connect · 에뮬레이터 시연 · Voice: ElevenLabs
      </div>
    </AbsoluteFill>
  );
};

/** BGM 크기: 컷마다 dB, 경계에서 0.4초 동안 이어 준다. 시작 2초 페이드 인, 끝 2초 페이드 아웃 */
const bgmVolume = (cuts: PlacedCut[], start: number, total: number) => (f: number) => {
  const g = f + start;
  const i = cuts.findIndex((c) => g >= c.from && g < c.from + c.frames);
  if (i < 0) return 0;
  const gain = (k: number) => (cuts[k]?.bgmDb == null ? 0 : dbToGain(cuts[k].bgmDb!));
  const ramp = 0.4 * FPS;
  const into = g - cuts[i].from;
  const mix = i > 0 && into < ramp ? gain(i - 1) + (gain(i) - gain(i - 1)) * EASE(into / ramp) : gain(i);
  const fades = interpolate(f, [0, 2 * FPS, total - start - 2 * FPS, total - start], [0, 1, 1, 0], clamp);
  return BGM_BASE * mix * fades;
};

/** 차 소리: 훅에서 크게, 1일차 주행 동안 낮게, 카드에서 꺼지고, "다음 날 아침"에서 다시 들어와 2일차 도착까지 */
const ambienceVolume = (cuts: PlacedCut[]) => {
  const at = (n: number) => cuts.find((c) => c.n === n)!;
  const end = (n: number) => at(n).from + at(n).frames;
  const s = FPS;
  const keys: [number, number][] = [
    [0, 0], [0.8 * s, 0.55], [end(1) - 0.5 * s, 0.55], [end(1) + 1 * s, 0.12],
    [end(8) - 0.5 * s, 0.12], [end(8) + 1 * s, 0],
    [at(20).from, 0], [at(20).from + 1.2 * s, 0.35], [end(20) + 1 * s, 0.12],
    [end(24) - 0.5 * s, 0.12], [end(24) + 1 * s, 0],
  ];
  return (f: number) => interpolate(f, keys.map((k) => k[0]), keys.map((k) => k[1]), clamp);
};

export const Film: React.FC = () => {
  const cuts = place(buildCuts());
  const total = cuts.reduce((a, c) => a + c.frames, 0);
  const bgmFrom = cuts.find((c) => c.n === 2)!.from;
  const cues = cuts.flatMap((c) => (c.cues ?? []).filter((q) => has(q.src)).map((q) => ({...q, from: c.from + Math.round(q.at * FPS)})));
  return (
    <AbsoluteFill style={{background: COLOR.bg}}>
      {cuts.map((c) => (
        <Sequence key={c.n} from={c.from} durationInFrames={c.frames} name={`cut ${c.n}`}>
          {c.kind === 'footage' && <FootageCut c={c} />}
          {c.kind === 'qr' && <QrCut c={c} />}
          {c.kind === 'title' && <Title c={c} />}
          {c.kind === 'end' && <EndCard c={c} />}
          <Subtitle c={c} />
          <TimeChip c={c} />
        </Sequence>
      ))}
      {cues.map((q) => (
        <Sequence key={q.src + q.from} from={q.from} layout="none" name={q.src}>
          <Audio src={staticFile(q.src)} volume={q.volume} />
        </Sequence>
      ))}
      {has('sfx/car-ambience.mp3') && (
        <Sequence from={0} durationInFrames={total} layout="none" name="ambience">
          <Audio src={staticFile('sfx/car-ambience.mp3')} loop volume={ambienceVolume(cuts)} />
        </Sequence>
      )}
      {has('bgm.mp3') && (
        <Sequence from={bgmFrom} layout="none" name="bgm">
          <Audio src={staticFile('bgm.mp3')} loop volume={bgmVolume(cuts, bgmFrom, total)} />
        </Sequence>
      )}
    </AbsoluteFill>
  );
};
