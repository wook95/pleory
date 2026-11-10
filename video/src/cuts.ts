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

// 앱 화면 위치(녹화 프레임 기준 비율). v3 프레임(1674x1202)에서 잰 값.
const FULL: Cam = {scale: 1, cx: 0.5, cy: 0.5};
const TOP_LABEL: Cam = {scale: 1.4, cx: 0, cy: 0}; // 좌상단 "퇴근길 / 기록하고 있어요"
const TALK: Cam = {scale: 1.25, cx: 0.3, cy: 1}; // 말하기 버튼 왼쪽(마이크·라벨)
const TALK_POINT = {x: 0.16, y: 0.78}; // 말하기 탭은 receiver 가 아니라 주입이라 좌표 로그가 없다
const DETAIL: Cam = {scale: 1.35, cx: 0.72, cy: 0.3}; // 넓은 창 오른쪽 판(묶음 상세)

// 화면 좌표(2560x1440, `PleoryDemo tap <이름> x= y=`) -> 크롭 프레임 비율 (scripts/prepare.sh 의 crop=1674:1202:857:94)
const CROP = {x: 857, y: 94, w: 1674, h: 1202};
type XY = {x: number; y: number} | null;
const tapOf = (xy: XY, at: number): Tap[] =>
  xy ? [{at, x: (xy.x - CROP.x) / CROP.w, y: (xy.y - CROP.y) / CROP.h}] : [];

const tapCue = (taps: Tap[]): AudioCue[] => taps.map((p) => ({src: 'sfx/tap.mp3', at: p.at, volume: 0.35}));

/** 혼잣말 자막: ramble-1.mp3 의 쉼(silencedetect -35dB)에서 갈아 끼운다. at 은 mp3 초 */
const RAMBLE: {at: number; lines: string[]}[] = [
  {at: 0, lines: ['오늘 팀장 때문에…', '진짜 힘들었다']},
  {at: 4.17, lines: ['아 근데,', '노들섬 예쁘네']},
  {at: 6.42, lines: ['이번 주말에 여기', '데이트 한번 올까?']},
  {at: 8.98, lines: ['아 맞다, 내일 거래처', '서류 다시 보내야 되고…']},
  {at: 12.37, lines: ['회의 자료도', '만들어야 되네']},
  {at: 14.44, lines: ['날씨는 왜', '이렇게 흐려…']},
  {at: 16.63, lines: ['기분까지 우울하다']},
  {at: 18.35, lines: ['짬뽕이나 먹어야겠다']},
  {at: 20.12, lines: ['요즘 주식은 어떻게', '되고 있으려나…']},
];

export const buildCuts = (t: Timings = timings): Cut[] => {
  const d = t.d1;
  const e = t.d2;
  const x1 = d.tapXY as Record<string, XY>;
  const x2 = e.tapXY as Record<string, XY>;
  const qrCloseTap = d.qrClose - 0.12; // `tap qr-close` 는 `qr close` 0.12초 전
  const gleoReply = e.gleoAccepted + 1.55; // Gleo 대답 소리는 accepted 뒤 약 1.9~2.0초에 시작한다(v3 녹화 1.96초). 컷 23 은 accepted + 1.15초, 소리 0.8초 앞에서 연다
  // 2일차 리마인드 TTS 는 로그(remindStart)보다 늦게 소리가 난다(합성 시간. v3 재녹화 0.9초, 부하 높던 첫 녹화 3.4초).
  // 컷 25 는 카드가 뜬 뒤 소리 전까지를 느리게 보이고, 컷 26 은 소리부터 리마인드 두 문장만 쓴다.
  const remindVoice = e.remindStart + 0.9;

  const rambleCue = -0.2; // 듣는 중은 탭 0.1초 뒤 시작, 컷 6 은 탭 0.3초 뒤에 연다
  const c2Tap = tapOf(x1.consent, 1.6);
  const c12Tap = tapOf(x1.group, 1.0);
  const c13Tap = tapOf(x1.tab, 0.6);
  const c14Taps = [...tapOf(x1.delete, 0.9), ...tapOf(x1['delete-confirm'], 0.9 + d.deleteConfirmTap - d.deleteTap)];
  const c15Taps = [...tapOf(x1['back-summary'], 0.6), ...tapOf(x1.trip, 0.6 + d.tripTap - d.backSummaryTap)];
  const c16Tap = tapOf(x1.qr, 1.0);
  const c18Taps = [...tapOf(x1['qr-close'], 0.4), ...tapOf(x1.history, 0.4 + d.historyTap - qrCloseTap)];
  const c19Tap = tapOf(x1['todo-only'], 0.8);
  const c27Tap = tapOf(x2['remind-done'], 1.0);
  const talk = (at: number): Tap[] => [{at, ...TALK_POINT}];

  const cuts: Cut[] = [
    {n: 1, dur: 4, kind: 'title', sub: HOOK, bgmDb: null,
      cues: [{src: 'sfx/turn-signal.mp3', at: 2.0, volume: 0.5}]},
    // ---- 1일차: 퇴근길 ----
    {n: 2, dur: 2, kind: 'footage', day: 1, srcStart: d.consentTap - 1.6, srcEnd: d.consentTap + 0.4,
      camFrom: {scale: 1.06, cx: 0.5, cy: 0.5}, sub: ['처음 한 번, 동의부터'], taps: c2Tap, bgmDb: 0},
    {n: 3, dur: 3, kind: 'footage', day: 1, srcStart: d.clock0 + 0.3, srcEnd: d.clock0 + 3.3,
      camFrom: {...FULL, scale: 1.05}, camTo: TOP_LABEL, camStart: 0.4, camMove: 2.6,
      sub: ['출발하면 알아서 기록해요'], chip: true, bgmDb: 0},
    {n: 4, dur: 2, kind: 'footage', day: 1, srcStart: d.clock0 + 3.3, srcEnd: d.tap1 - 1.7,
      sub: ['화면은 이게 전부예요'], chip: true, bgmDb: 0},
    {n: 5, dur: 2, kind: 'footage', day: 1, srcStart: d.tap1 - 1.7, srcEnd: d.tap1 + 0.3,
      camFrom: FULL, camTo: TALK, camMove: 1.6, sub: ['누르고, 아무 말이나'], chip: true, taps: talk(1.7), bgmDb: 0},
    // 듣는 중은 녹화에서 혼잣말 길이(ramble-1.mp3 + 0.1초)만큼 유지되고, 끝나면 인식 결과가 들어가 "적어뒀어"(ack1)
    {n: 6, dur: d.ack1 - 0.2 - (d.tap1 + 0.3), kind: 'footage', day: 1, srcStart: d.tap1 + 0.3, srcEnd: d.ack1 - 0.2,
      camFrom: TALK, camTo: {...TALK, scale: 1.12}, driver: true, chip: true, chipAt: d.ack1, bgmDb: -8,
      subs: RAMBLE.map((r) => ({at: Math.max(0, r.at + rambleCue), lines: r.lines})),
      cues: [{src: 'vo/ramble-1.mp3', at: rambleCue, volume: 1}]},
    {n: 7, dur: 3, kind: 'footage', day: 1, srcStart: d.ack1 - 0.2, srcEnd: d.ack1 + 2.8, appAudio: true,
      camFrom: {...TALK, scale: 1.12}, camTo: FULL, sub: ['대답은 한마디뿐'], chip: true, bgmDb: -8},
    {n: 8, dur: 3, kind: 'footage', day: 1, srcStart: d.ack1 + 2.8, srcEnd: d.cardShown - 1.0,
      sub: ['집 앞, 기어 P'], chip: true, bgmDb: 1.5, cues: [{src: 'sfx/gear-park.mp3', at: 2.6, volume: 0.5}]},
    // 카드가 뜨자마자 요약 TTS 가 시작되고(cardShown + 0.06), 대표 문장은 약 1.8초 뒤 규칙 문장에서 Claude 문장으로 바뀐다
    {n: 9, dur: 2, kind: 'footage', day: 1, srcStart: d.cardShown - 1.0, srcEnd: d.cardShown + 1.0, appAudio: true,
      sub: ['도착하면 한 장으로'], bgmDb: -8},
    {n: 10, dur: d.summaryEnd + 0.4 - (d.cardShown + 1.0), kind: 'footage', day: 1,
      srcStart: d.cardShown + 1.0, srcEnd: d.summaryEnd + 0.4, appAudio: true,
      camFrom: FULL, camTo: {scale: 1.5, cx: 0.27, cy: 0.8}, camStart: 0.2, camMove: 1.8,
      sub: ['요점만, 15초 안에'], bgmDb: -8},
    {n: 11, dur: 5, kind: 'footage', day: 1, srcStart: d.summaryEnd + 0.6, freeze: true,
      camFrom: {scale: 1.75, cx: 0.29, cy: 0.29}, camTo: {scale: 1.75, cx: 0.29, cy: 0.5}, camStart: 0.8, camMove: 4.6,
      sub: ['감정, 일, 아이디어로'], bgmDb: 0},
    {n: 12, dur: 3.4, kind: 'footage', day: 1, srcStart: d.groupWorkTap - 1.0, srcEnd: d.groupWorkTap + 2.4,
      camFrom: FULL, camTo: DETAIL, camStart: 1.4, camMove: 3.3,
      sub: ['누르면 자세히'], taps: c12Tap, cues: tapCue(c12Tap), bgmDb: 0},
    {n: 13, dur: 2.5, kind: 'footage', day: 1, srcStart: d.tabIdeaTap - 0.6, srcEnd: d.tabIdeaTap + 1.9,
      camFrom: DETAIL, sub: ['생각은 생각대로'], taps: c13Tap, cues: tapCue(c13Tap), bgmDb: 0},
    {n: 14, dur: 4.5, kind: 'footage', day: 1, srcStart: d.deleteTap - 0.9, srcEnd: d.deleteTap + 3.6,
      camFrom: DETAIL, camTo: {scale: 1.5, cx: 0.74, cy: 0.38}, camMove: 1.2,
      sub: ['필요 없는 건 지워요'], taps: c14Taps, cues: tapCue(c14Taps), bgmDb: 0},
    {n: 15, dur: 3.4, kind: 'footage', day: 1, srcStart: d.backSummaryTap - 0.6, srcEnd: d.backSummaryTap + 2.8,
      camFrom: {scale: 1.2, cx: 0.6, cy: 0.4}, camTo: {scale: 1.4, cx: 0.3, cy: 0.72}, camStart: 1.0, camMove: 3.0,
      sub: ['주행 기록은 아래에'], taps: c15Taps, cues: tapCue(c15Taps), bgmDb: 0},
    {n: 16, dur: 4, kind: 'footage', day: 1, srcStart: d.qrTap - 1.0, srcEnd: d.qrTap + 3.0,
      camFrom: FULL, camTo: {scale: 1.3, cx: 0.5, cy: 0.47}, camStart: 1.3, camMove: 3.6,
      sub: ['폰 카메라로 비추면'], taps: c16Tap, cues: tapCue(c16Tap), bgmDb: 0},
    {n: 17, dur: 3.5, kind: 'qr', day: 1, srcStart: d.qrOpen + 1.5, freeze: true, sub: ['서버 없이 폰으로'], bgmDb: 0},
    {n: 18, dur: 3, kind: 'footage', day: 1, srcStart: qrCloseTap - 0.4, srcEnd: qrCloseTap + 2.6,
      camFrom: FULL, camTo: {scale: 1.15, cx: 0.5, cy: 0.4}, camStart: 1.8, camMove: 3,
      sub: ['최근 7일 기록'], taps: c18Taps, cues: tapCue(c18Taps), bgmDb: 0},
    {n: 19, dur: 3, kind: 'footage', day: 1, srcStart: d.todoOnlyTap - 0.8, srcEnd: d.todoOnlyTap + 2.2,
      camFrom: {scale: 1.2, cx: 0.8, cy: 0.2}, camTo: {scale: 1.2, cx: 0.45, cy: 0.4}, camStart: 1.0, camMove: 2.8,
      sub: ['남은 할 일만 모아서'], taps: c19Tap, cues: tapCue(c19Tap), bgmDb: 0},
    // ---- 2일차: 출근길 ----
    {n: 20, dur: 3, kind: 'title', sub: ['다음 날 아침'], bgmDb: -4},
    {n: 21, dur: 3, kind: 'footage', day: 2, srcStart: e.clock0 + 0.5, srcEnd: e.clock0 + 3.5,
      camFrom: {...FULL, scale: 1.05}, camTo: TOP_LABEL, camMove: 2.2, sub: ['출근길, 08:10'], chip: true, bgmDb: 0},
    // 대사 4 는 Gleo 에 인식 결과를 넣은 시각(gleoInject)에 끝나게 둔다: "플레오리 앱에서,"(0~1.1초) … "자료"(~3.6초) 뒤 "챙기라고 적어줘"
    {n: 22, dur: 5, kind: 'footage', day: 2, srcStart: e.gleoInject - 5.0, srcEnd: e.gleoInject,
      driver: true, chip: true, chipAt: e.gleoAccepted, bgmDb: -6,
      subs: [{at: 0, lines: ['플레오리 앱에서, 오늘', '오후 세 시 회의 자료']}, {at: 0.3 + 3.65, lines: ['챙기라고 적어줘']}],
      cues: [{src: 'vo/driver-4.mp3', at: 0.3, volume: 1}]},
    {n: 23, dur: 6.1, kind: 'footage', day: 2, srcStart: gleoReply - 0.4, srcEnd: gleoReply + 5.7,
      appAudio: !e.gleoSimulated, note: e.gleoSimulated ? 'Gleo 호출 시뮬레이션' : undefined,
      sub: ['버튼 없이 Gleo 로도'], chip: true, bgmDb: -8},
    {n: 24, dur: 3, kind: 'footage', day: 2, srcStart: gleoReply + 5.7, srcEnd: e.cardShown - 0.1,
      sub: ['회사 앞, 기어 P'], chip: true, bgmDb: 1.5, cues: [{src: 'sfx/gear-park.mp3', at: 2.6, volume: 0.5}]},
    {n: 25, dur: 2.5, kind: 'footage', day: 2, srcStart: e.cardShown - 0.3, srcEnd: remindVoice,
      camFrom: FULL, camTo: {scale: 1.7, cx: 0.29, cy: 0.29}, camStart: 0.5, camMove: 2.5,
      sub: ['도착하면 어제 할 일부터'], bgmDb: -4},
    // 리마인드 두 문장("어제 퇴근길에 할 일 둘 말했어. 거래처 서류 다시 보내기 외 1개.") 뒤 요약은 줄인다
    {n: 26, dur: 4.6, kind: 'footage', day: 2, srcStart: remindVoice, srcEnd: remindVoice + 4.6,
      appAudio: true, appFadeAt: 4.35, camFrom: {scale: 1.7, cx: 0.29, cy: 0.29},
      sub: ['도착하면 어제 할 일부터'], bgmDb: -8},
    {n: 27, dur: 3, kind: 'footage', day: 2, srcStart: e.remindDoneTap - 1.0, srcEnd: e.remindDoneTap + 2.0,
      camFrom: {scale: 1.5, cx: 0.33, cy: 0.33}, camTo: {scale: 1.1, cx: 0.5, cy: 0.5}, camStart: 1.3, camMove: 2.8,
      sub: ['끝낸 일은 다시 안 불러요'], taps: c27Tap, cues: tapCue(c27Tap), bgmDb: 0},
    {n: 28, dur: 5, kind: 'end', bgmDb: 0},
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
