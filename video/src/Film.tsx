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
import {appClock, buildCuts, place, tapPoint, TAGLINE, type Cam, type PlacedCut} from './cuts';
import {COLOR, FONT, FPS, FRAME_H, FRAME_TOP, FRAME_W, SUB_Y} from './theme';

export type FilmProps = {withDeleteCut: boolean};
export const filmSchemaDefaults: FilmProps = {withDeleteCut: false};

const has = (f: string) => (assets as Record<string, boolean>)[f] === true;
const EASE = Easing.bezier(0.42, 0, 0.18, 1);
const BGM_BASE = 0.3;
const dbToGain = (db: number) => Math.pow(10, db / 20);

const camAt = (c: PlacedCut, frame: number): Cam => {
  const from = c.camFrom ?? {scale: 1, cx: 0.5, cy: 0.5};
  const to = c.camTo ?? from;
  const p = interpolate(frame, [0, (c.camMove ?? c.dur) * FPS], [0, 1], {
    easing: EASE,
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  return {
    scale: from.scale + (to.scale - from.scale) * p,
    cx: from.cx + (to.cx - from.cx) * p,
    cy: from.cy + (to.cy - from.cy) * p,
  };
};

const camTransform = ({scale, cx, cy}: Cam) => {
  const half = 0.5 / scale;
  const x = Math.min(Math.max(cx, half), 1 - half);
  const y = Math.min(Math.max(cy, half), 1 - half);
  const tx = FRAME_W / 2 - scale * x * FRAME_W;
  const ty = FRAME_H / 2 - scale * y * FRAME_H;
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
  const src = staticFile('footage.mp4');
  const trim = Math.round(start * FPS);
  if (!has('footage.mp4')) {
    return (
      <AbsoluteFill style={{background: '#161513', alignItems: 'center', justifyContent: 'center'}}>
        <div style={{color: '#555', fontFamily: FONT, fontSize: 28}}>footage.mp4 없음 · 컷 {c.n}</div>
      </AbsoluteFill>
    );
  }
  const endFrame = Math.round(((c.srcEnd ?? start) - start) * FPS);
  return (
    <>
      {playFrames > 0 && (
        <Sequence durationInFrames={playFrames} layout="none">
          <OffthreadVideo src={src} trimBefore={trim} playbackRate={rate} muted={!c.appAudio}
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

const TapRing: React.FC<{at: number}> = ({at}) => {
  const f = useCurrentFrame() - at * FPS;
  if (f < 0 || f > 0.3 * FPS) return null;
  const p = f / (0.3 * FPS);
  const d = 56 + 40 * p;
  return (
    <div style={{
      position: 'absolute', left: tapPoint.x * FRAME_W - d / 2, top: tapPoint.y * FRAME_H - d / 2,
      width: d, height: d, borderRadius: '50%', border: '3px solid #fff', opacity: 0.9 * (1 - p),
    }} />
  );
};

const FootageCut: React.FC<{c: PlacedCut}> = ({c}) => {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill style={{background: COLOR.bg}}>
      <div style={{
        position: 'absolute', left: (1920 - FRAME_W) / 2, top: FRAME_TOP, width: FRAME_W, height: FRAME_H,
        borderRadius: 14, overflow: 'hidden', background: '#111',
      }}>
        <div style={{position: 'absolute', inset: 0, transformOrigin: '0 0', transform: camTransform(camAt(c, frame))}}>
          <FootageLayer c={c} />
          {c.tapAt != null && <TapRing at={c.tapAt} />}
        </div>
      </div>
    </AbsoluteFill>
  );
};

const fadeInOut = (frame: number, frames: number, fade: number) =>
  interpolate(frame, [0, fade * FPS, frames - fade * FPS, frames], [0, 1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

const Subtitle: React.FC<{c: PlacedCut}> = ({c}) => {
  const frame = useCurrentFrame();
  if (!c.sub || c.kind === 'black') return null;
  return (
    <div style={{
      position: 'absolute', left: 0, right: 0, top: SUB_Y, transform: 'translateY(-50%)',
      textAlign: 'center', fontFamily: FONT, fontSize: 54, lineHeight: 1.32,
      fontWeight: c.driver ? 500 : 600, color: c.driver ? COLOR.driver : COLOR.text,
      textShadow: '0 2px 14px rgba(0,0,0,0.9), 0 0 3px rgba(0,0,0,0.7)',
      opacity: fadeInOut(frame, c.frames, 0.3), letterSpacing: '-0.01em',
    }}>
      {c.sub.map((l) => <div key={l}>{l}</div>)}
    </div>
  );
};

const TimeChip: React.FC<{c: PlacedCut}> = ({c}) => {
  const frame = useCurrentFrame();
  if (!c.chip) return null;
  const {rate, playFrames} = playback(c);
  const start = c.srcStart ?? 0;
  const src = start + (Math.min(frame, playFrames) / FPS) * rate;
  return (
    <div style={{
      position: 'absolute', left: 64, bottom: 52, fontFamily: FONT, fontSize: 28, fontWeight: 500,
      color: COLOR.chip, fontVariantNumeric: 'tabular-nums', letterSpacing: '0.02em',
      opacity: fadeInOut(frame, c.frames, 0.2),
    }}>
      {appClock(src)}
    </div>
  );
};

const Hook: React.FC<{c: PlacedCut}> = ({c}) => {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center'}}>
      <div style={{fontFamily: FONT, fontSize: 68, fontWeight: 600, color: COLOR.text,
        opacity: fadeInOut(frame, c.frames, 0.4), letterSpacing: '-0.015em'}}>
        {c.sub?.[0]}
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
  const fades = interpolate(f, [0, 2 * FPS, total - start - 2 * FPS, total - start], [0, 1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  return BGM_BASE * mix * fades;
};

export const Film: React.FC<FilmProps> = ({withDeleteCut}) => {
  const cuts = place(buildCuts(withDeleteCut));
  const total = cuts.reduce((a, c) => a + c.frames, 0);
  const bgmFrom = cuts.find((c) => c.n === 2)!.from;
  const ambEnd = (() => {
    const c12 = cuts.find((c) => c.n === 12)!;
    return c12.from + c12.frames;
  })();
  const c1 = cuts[0];
  return (
    <AbsoluteFill style={{background: COLOR.bg}}>
      {cuts.map((c) => (
        <Sequence key={c.n} from={c.from} durationInFrames={c.frames} name={`cut ${c.n}`}>
          {c.kind === 'footage' && <FootageCut c={c} />}
          {c.n === 1 && <Hook c={c} />}
          {c.n === 19 && <EndCard c={c} />}
          <Subtitle c={c} />
          <TimeChip c={c} />
          {(c.cues ?? []).filter((q) => has(q.src)).map((q) => (
            <Sequence key={q.src + q.at} from={Math.round(q.at * FPS)} layout="none">
              <Audio src={staticFile(q.src)} volume={q.volume} />
            </Sequence>
          ))}
        </Sequence>
      ))}
      {has('sfx/car-ambience.mp3') && (
        <Sequence from={0} durationInFrames={ambEnd} layout="none" name="ambience">
          <Audio src={staticFile('sfx/car-ambience.mp3')} loop volume={(f) =>
            interpolate(f, [0, 0.8 * FPS, c1.frames - 0.5 * FPS, c1.frames + 1 * FPS, ambEnd - 1.5 * FPS, ambEnd],
              [0, 0.55, 0.55, 0.12, 0.12, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'})} />
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
