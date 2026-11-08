import timingsJson from './timings.json';
import {FPS} from './theme';

export type Timings = typeof timingsJson;
export const timings: Timings = timingsJson;

/** 카메라: 녹화 프레임 위의 시점. cx, cy 는 보이는 창의 중심(0~1), scale 1 = 전체. */
export type Cam = {scale: number; cx: number; cy: number};

/** 컷 안 초(at, 음수면 컷 시작 전)에 시작하는 소리. 컷 경계에서 끊기지 않는다. */
export type AudioCue = {src: string; at: number; volume: number};

/** 탭 표시 원: 컷 안 초, 녹화 프레임 기준 비율 좌표 */
export type Tap = {at: number; x: number; y: number};

export type Cut = {
  n: number;
  dur: number; // 편집본 초
  kind: 'title' | 'footage' | 'qr' | 'end';
  /** 어느 날 녹화인지 (footage-d1.mp4 / footage-d2.mp4) */
  day?: 1 | 2;
  /** 녹화 초. freeze 면 그 프레임에 멈춘다. 아니면 srcStart 에서 srcEnd 까지를 dur 에 맞춰 재생 */
  srcStart?: number;
  srcEnd?: number;
  freeze?: boolean;
  camFrom?: Cam;
  camTo?: Cam;
  /** 카메라 이동 시작·끝(컷 안 초). 없으면 0 과 컷 끝 */
  camStart?: number;
  camMove?: number;
  /** 자막. subs 가 있으면 at(컷 안 초)부터 다음 항목까지 그 줄을 쓴다 */
  sub?: string[];
  subs?: {at: number; lines: string[]}[];
  driver?: boolean;
  /** 녹화 원음(앱 TTS, Gleo 대답)을 쓸지. appFadeAt 이 있으면 그 초부터 0.5초 동안 줄인다 */
  appAudio?: boolean;
  appFadeAt?: number;
  chip?: boolean;
  /** 시각 칩을 이 녹화 초의 시각에 고정(듣는 중에는 저장된 분) */
  chipAt?: number;
  taps?: Tap[];
  /** 화면 우상단 작은 글씨 */
  note?: string;
  /** BGM 크기(dB, 기준 대비). null 이면 BGM 없음 */
  bgmDb: number | null;
  cues?: AudioCue[];
};

const HOOK = ['퇴근길에 떠오른 생각,', '집에 오면 기억나세요?'];
export const TAGLINE = '말해두면, 퇴근길이 한 장으로 남아요.';

// 앱 화면 위치(녹화 프레임 기준 비율). v2 프레임(1674x1202)에서 잰 값.
const FULL: Cam = {scale: 1, cx: 0.5, cy: 0.5};
const TOP_LABEL: Cam = {scale: 1.4, cx: 0, cy: 0}; // 좌상단 "퇴근길 / 기록하고 있어요"
const TALK: Cam = {scale: 1.25, cx: 0.3, cy: 1}; // 말하기 버튼 왼쪽(마이크·라벨)
const TALK_POINT = {x: 0.16, y: 0.78}; // 말하기 탭은 receiver 가 아니라 주입이라 좌표 로그가 없다

// 화면 좌표(2560x1440, `PleoryDemo tap <이름> x= y=`) -> 크롭 프레임 비율 (scripts/prepare.sh 의 crop=1674:1202:857:94)
const CROP = {x: 857, y: 94, w: 1674, h: 1202};
type XY = {x: number; y: number} | null;
const tapOf = (xy: XY, at: number): Tap[] =>
  xy ? [{at, x: (xy.x - CROP.x) / CROP.w, y: (xy.y - CROP.y) / CROP.h}] : [];

const tapCue = (taps: Tap[]): AudioCue[] => taps.map((p) => ({src: 'sfx/tap.mp3', at: p.at, volume: 0.35}));

export const buildCuts = (t: Timings = timings): Cut[] => {
  const d = t.d1;
  const e = t.d2;
  const x1 = d.tapXY as Record<string, XY>;
  const x2 = e.tapXY as Record<string, XY>;
  const qrCloseTap = d.qrClose - 0.12; // `tap qr-close` 는 `qr close` 0.12초 전
  const gleoReply = e.gleoAccepted + 1.55; // Gleo 대답 소리는 accepted 뒤 약 1.9초에 시작한다(2일차 녹화 45.07초). 컷 26 은 그 0.4초 앞에서 연다

  const c2Tap = tapOf(x1.consent, 1.6);
  const c18Taps = [...tapOf(x1.delete, 0.9), ...tapOf(x1['delete-confirm'], 0.9 + d.deleteConfirmTap - d.deleteTap)];
  const c19Tap = tapOf(x1.qr, 1.0);
  const c21Taps = [...tapOf(x1['qr-close'], 0.4), ...tapOf(x1.history, 0.4 + d.historyTap - qrCloseTap)];
  const c22Tap = tapOf(x1['todo-only'], 0.8);
  const c29Tap = tapOf(x2['remind-done'], 1.0);
  const talk = (at: number): Tap[] => [{at, ...TALK_POINT}];

  const cuts: Cut[] = [
    {n: 1, dur: 4, kind: 'title', sub: HOOK, bgmDb: null,
      cues: [{src: 'sfx/turn-signal.mp3', at: 2.0, volume: 0.5}]},
    // ---- 1일차: 퇴근길 ----
    {n: 2, dur: 2, kind: 'footage', day: 1, srcStart: d.consentTap - 1.6, srcEnd: d.consentTap + 0.4,
      camFrom: {scale: 1.06, cx: 0.5, cy: 0.5}, sub: ['처음 한 번, 동의부터'], taps: c2Tap, bgmDb: 0},
    {n: 3, dur: 4, kind: 'footage', day: 1, srcStart: d.clock0 + 0.3, srcEnd: d.clock0 + 4.3,
      camFrom: {...FULL, scale: 1.05}, camTo: TOP_LABEL, camStart: 0.6, camMove: 3.2,
      sub: ['출발하면 알아서 기록해요'], chip: true, bgmDb: 0},
    {n: 4, dur: 2, kind: 'footage', day: 1, srcStart: d.clock0 + 4.3, srcEnd: d.tap1 - 1.7,
      sub: ['화면은 이게 전부예요'], chip: true, bgmDb: 0},
    {n: 5, dur: 2, kind: 'footage', day: 1, srcStart: d.tap1 - 1.7, srcEnd: d.tap1 + 0.3,
      camFrom: FULL, camTo: TALK, camMove: 1.6, sub: ['누르고 떠오르는 대로'], chip: true, taps: talk(1.7), bgmDb: 0},
    // 듣는 중은 녹화에서 대사 길이만큼 유지된다(run-demo v2). 대사는 탭 0.1초 뒤에 시작.
    {n: 6, dur: 3.5, kind: 'footage', day: 1, srcStart: d.tap1 + 0.3, srcEnd: d.tap1 + 3.8, camFrom: TALK,
      sub: ['오늘 팀장 때문에…', '진짜 힘들었다'], driver: true, chip: true, chipAt: d.ack1, bgmDb: -6,
      cues: [{src: 'vo/driver-1.mp3', at: -0.2, volume: 1}]},
    {n: 7, dur: 3, kind: 'footage', day: 1, srcStart: d.tap1 + 3.8, srcEnd: d.tap1 + 6.8, appAudio: true,
      camFrom: TALK, camTo: {...TALK, scale: 1.1}, sub: ['대답은 한마디뿐'], chip: true, bgmDb: -8},
    {n: 8, dur: 2, kind: 'footage', day: 1, srcStart: d.tap1 + 6.8, srcEnd: d.tap2 - 1.0,
      sub: ['화면은 그대로예요'], chip: true, bgmDb: 0},
    {n: 9, dur: 9.3, kind: 'footage', day: 1, srcStart: d.tap2 - 1.0, srcEnd: d.tap2 + 8.3,
      camFrom: FULL, camTo: TALK, camMove: 1.2, taps: talk(1.0), driver: true, chip: true, chipAt: d.ack2, bgmDb: -6,
      // 대사 2: "내일 거래처 서류 다시 보내야 해"(0~2.1초) [pause] "아, 노을 예쁘다"(5.3~8.2초)
      subs: [{at: 1.0, lines: ['내일 거래처 서류', '다시 보내야 해']}, {at: 1.1 + 4.9, lines: ['아, 노을 예쁘다']}],
      cues: [{src: 'vo/driver-2.mp3', at: 1.1, volume: 1}]},
    {n: 10, dur: 3, kind: 'footage', day: 1, srcStart: d.tap2 + 8.3, srcEnd: d.tap2 + 11.3, appAudio: true,
      camFrom: TALK, camTo: FULL, sub: ['할 일·기분 섞여도 돼요'], chip: true, bgmDb: -8},
    {n: 11, dur: 4, kind: 'footage', day: 1, srcStart: d.tap3 - 0.8, srcEnd: d.tap3 + 3.2, appAudio: true,
      camFrom: TALK, taps: talk(0.8), sub: ['음… 그 뭐냐…'], driver: true, chip: true, chipAt: d.ack3, bgmDb: -6,
      cues: [{src: 'vo/driver-3.mp3', at: 0.9, volume: 1}]},
    {n: 12, dur: 3, kind: 'footage', day: 1, srcStart: d.tap3 + 3.2, srcEnd: d.cardShown - 0.1,
      sub: ['집 앞, 기어 P'], chip: true, bgmDb: 1.5, cues: [{src: 'sfx/gear-park.mp3', at: 2.6, volume: 0.5}]},
    {n: 13, dur: 3, kind: 'footage', day: 1, srcStart: d.cardShown - 1.0, srcEnd: d.cardShown + 2.0,
      sub: ['도착하면 한 장으로'], bgmDb: 0},
    // 14·16·17 은 카드가 멈춰 있는 화면이라 정지 프레임에 카메라만 움직인다. 15 만 요약 원음 때문에 실제로 재생한다.
    {n: 14, dur: 4, kind: 'footage', day: 1, srcStart: d.cardShown + 0.6, freeze: true,
      camFrom: {scale: 1.5, cx: 0.3, cy: 0.15}, camTo: {scale: 1.55, cx: 0.33, cy: 0.2},
      sub: ['51분, 23km'], bgmDb: 0},
    {n: 15, dur: 5, kind: 'footage', day: 1, srcStart: d.cardShown + 0.3, srcEnd: d.summaryEnd + 0.3, appAudio: true,
      camFrom: {scale: 1.6, cx: 0.31, cy: 0.69}, sub: ['요점만, 15초 안에'], bgmDb: -8},
    {n: 16, dur: 3, kind: 'footage', day: 1, srcStart: d.summaryEnd + 0.5, freeze: true,
      camFrom: {scale: 1.3, cx: 0.25, cy: 0.25}, camTo: {scale: 1.6, cx: 0.2, cy: 0.24}, camMove: 2.2,
      sub: ['힘든 데서 시작해 노을로'], bgmDb: 0},
    {n: 17, dur: 3, kind: 'footage', day: 1, srcStart: d.summaryEnd + 0.5, freeze: true,
      camFrom: {scale: 1.6, cx: 0.3, cy: 0.35}, camTo: {scale: 1.6, cx: 0.3, cy: 0.62},
      sub: ['말한 시각·장소와 함께'], bgmDb: 0},
    {n: 18, dur: 4, kind: 'footage', day: 1, srcStart: d.deleteTap - 0.9, srcEnd: d.deleteTap + 3.1,
      camFrom: {scale: 1.4, cx: 0.36, cy: 0.62}, camTo: {scale: 1.4, cx: 0.36, cy: 0.5}, camStart: 2.9, camMove: 4,
      sub: ['잘못 들어간 건 지워요'], taps: c18Taps, cues: tapCue(c18Taps), bgmDb: 0},
    {n: 19, dur: 4, kind: 'footage', day: 1, srcStart: d.qrTap - 1.0, srcEnd: d.qrTap + 3.0,
      camFrom: FULL, camTo: {scale: 1.3, cx: 0.5, cy: 0.47}, camStart: 1.3, camMove: 3.6,
      sub: ['폰 카메라로 비추면'], taps: c19Tap, cues: tapCue(c19Tap), bgmDb: 0},
    {n: 20, dur: 3, kind: 'qr', day: 1, srcStart: d.qrOpen + 1.5, freeze: true, sub: ['서버 없이 폰으로'], bgmDb: 0},
    {n: 21, dur: 3, kind: 'footage', day: 1, srcStart: qrCloseTap - 0.4, srcEnd: qrCloseTap + 2.6,
      camFrom: FULL, camTo: {scale: 1.15, cx: 0.5, cy: 0.4}, camStart: 1.8, camMove: 3,
      sub: ['최근 7일 기록'], taps: c21Taps, cues: tapCue(c21Taps), bgmDb: 0},
    {n: 22, dur: 3, kind: 'footage', day: 1, srcStart: d.todoOnlyTap - 0.8, srcEnd: d.todoOnlyTap + 2.2,
      camFrom: {scale: 1.2, cx: 0.8, cy: 0.2}, camTo: {scale: 1.2, cx: 0.45, cy: 0.4}, camStart: 1.0, camMove: 2.8,
      sub: ['남은 할 일만 모아서'], taps: c22Tap, cues: tapCue(c22Tap), bgmDb: 0},
    // ---- 2일차: 출근길 ----
    {n: 23, dur: 3, kind: 'title', sub: ['다음 날 아침'], bgmDb: -4},
    {n: 24, dur: 3, kind: 'footage', day: 2, srcStart: e.clock0 + 0.5, srcEnd: e.clock0 + 3.5,
      camFrom: {...FULL, scale: 1.05}, camTo: TOP_LABEL, camMove: 2.2, sub: ['출근길, 08:10'], chip: true, bgmDb: 0},
    // 대사 4 는 Gleo 에 인식 결과를 넣은 시각(gleoInject)에 끝나게 둔다: "플레오리 앱에서,"(0~1.1초) … "자료"(~3.6초) 뒤 "챙기라고 적어줘"
    {n: 25, dur: 5, kind: 'footage', day: 2, srcStart: e.gleoInject - 5.0, srcEnd: e.gleoInject,
      driver: true, chip: true, chipAt: e.gleoAccepted, bgmDb: -6,
      subs: [{at: 0, lines: ['플레오리 앱에서, 오늘', '오후 세 시 회의 자료']}, {at: 0.3 + 3.65, lines: ['챙기라고 적어줘']}],
      cues: [{src: 'vo/driver-4.mp3', at: 0.3, volume: 1}]},
    {n: 26, dur: 5.7, kind: 'footage', day: 2, srcStart: gleoReply - 0.4, srcEnd: gleoReply + 5.3,
      appAudio: !e.gleoSimulated, note: e.gleoSimulated ? 'Gleo 호출 시뮬레이션' : undefined,
      sub: ['버튼 없이 Gleo 로도'], chip: true, bgmDb: -8},
    {n: 27, dur: 3, kind: 'footage', day: 2, srcStart: gleoReply + 5.3, srcEnd: e.cardShown - 0.1,
      sub: ['회사 앞, 기어 P'], chip: true, bgmDb: 1.5, cues: [{src: 'sfx/gear-park.mp3', at: 2.6, volume: 0.5}]},
    // 리마인드 한 줄은 카드 뒤 약 4.4초에 끝나고 요약이 이어진다 -> 요약 앞에서 원음을 줄인다
    {n: 28, dur: 7, kind: 'footage', day: 2, srcStart: e.cardShown - 0.3, srcEnd: e.cardShown + 6.7,
      appAudio: true, appFadeAt: 4.8,
      camFrom: FULL, camTo: {scale: 1.7, cx: 0.29, cy: 0.29}, camStart: 0.5, camMove: 2.7,
      sub: ['도착하면 어제 할 일부터'], bgmDb: -8},
    {n: 29, dur: 3, kind: 'footage', day: 2, srcStart: e.remindDoneTap - 1.0, srcEnd: e.remindDoneTap + 2.0,
      camFrom: {scale: 1.5, cx: 0.33, cy: 0.33}, camTo: {scale: 1.1, cx: 0.5, cy: 0.5}, camStart: 1.3, camMove: 2.8,
      sub: ['끝낸 일은 다시 안 불러요'], taps: c29Tap, cues: tapCue(c29Tap), bgmDb: 0},
    {n: 30, dur: 5, kind: 'end', bgmDb: 0},
  ];
  // 탭 소리: taps 가 있는데 cues 에 tap 이 없는 컷(말하기·동의)
  for (const c of cuts) {
    if (c.taps && !(c.cues ?? []).some((q) => q.src === 'sfx/tap.mp3')) c.cues = [...(c.cues ?? []), ...tapCue(c.taps)];
    const end = (c.day === 2 ? e.footageDuration : t.footageDuration) - 0.1;
    if (c.srcStart != null) c.srcStart = Math.min(Math.max(0, c.srcStart), end);
    if (c.srcEnd != null) c.srcEnd = Math.min(Math.max(c.srcStart ?? 0, c.srcEnd), end);
  }
  return cuts;
};

export type PlacedCut = Cut & {from: number; frames: number};

export const place = (cuts: Cut[]): PlacedCut[] => {
  let from = 0;
  return cuts.map((c) => {
    const frames = Math.round(c.dur * FPS);
    const p = {...c, from, frames};
    from += frames;
    return p;
  });
};

export const totalFrames = (cuts: Cut[]) => place(cuts).reduce((a, c) => a + c.frames, 0);

/** 앱 시계(run-demo 배속)로 녹화 초를 HH:MM 로. 1일차는 clock0 = 18:40, 2일차는 08:10 */
export const appClock = (src: number, day: 1 | 2 = 1, t: Timings = timings) => {
  const base = day === 2 ? 8 * 60 + 10 : 18 * 60 + 40;
  const clock0 = day === 2 ? t.d2.clock0 : t.d1.clock0;
  const m = Math.max(base, base + Math.floor(((src - clock0) * t.clockSpeed) / 60));
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
};
