import timingsJson from './timings.json';
import {FPS} from './theme';

export type Timings = typeof timingsJson;
export const timings: Timings = timingsJson;

/** 카메라: 녹화 프레임 위의 시점. cx, cy 는 보이는 창의 중심(0~1), scale 1 = 전체. */
export type Cam = {scale: number; cx: number; cy: number};

export type AudioCue = {src: string; at: number; volume: number};

export type Cut = {
  n: number;
  dur: number; // 편집본 초
  kind: 'black' | 'footage';
  /** 녹화 초. freeze 면 그 프레임에 멈춘다. 아니면 srcStart 에서 srcEnd 까지를 dur 에 맞춰 재생 */
  srcStart?: number;
  srcEnd?: number;
  freeze?: boolean;
  camFrom?: Cam;
  camTo?: Cam;
  /** 카메라 이동이 끝나는 시점(컷 안 초). 없으면 컷 전체 */
  camMove?: number;
  sub?: string[];
  driver?: boolean;
  /** 녹화 원음(앱 TTS)을 쓸지 */
  appAudio?: boolean;
  chip?: boolean;
  /** 탭 표시 원(컷 안 초) */
  tapAt?: number;
  /** BGM 크기(dB, 기준 대비). null 이면 BGM 없음 */
  bgmDb: number | null;
  cues?: AudioCue[];
};

const HOOK = '퇴근길에 떠오른 생각, 집에 오면 기억나세요?';
export const TAGLINE = '말해두면, 퇴근길이 한 장으로 남아요.';

// 앱 화면 위치(녹화 프레임 기준 비율). take2 프레임에서 잰 값.
const FULL: Cam = {scale: 1, cx: 0.5, cy: 0.5};
const TOP_LABEL: Cam = {scale: 1.4, cx: 0, cy: 0}; // 좌상단 "퇴근길 / 기록하고 있어요"
const TALK: Cam = {scale: 1.25, cx: 0.3, cy: 1}; // 말하기 버튼 왼쪽(마이크·라벨)
const TAP_POINT = {x: 0.16, y: 0.78};

export const tapPoint = TAP_POINT;

export const buildCuts = (withDeleteCut: boolean, t: Timings = timings): Cut[] => {
  const end = t.footageDuration - 0.1;
  const s0 = t.clock0 + 0.3;
  const cuts: Cut[] = [
    {n: 1, dur: 5, kind: 'black', sub: [HOOK], bgmDb: null,
      cues: [{src: 'sfx/turn-signal.mp3', at: 1.6, volume: 0.5}]},
    {n: 2, dur: 4, kind: 'footage', srcStart: s0, srcEnd: s0 + 4, camFrom: {...FULL, scale: 1.05}, camTo: FULL,
      sub: ['퇴근길, 18:40'], chip: true, bgmDb: 0},
    {n: 3, dur: 4, kind: 'footage', srcStart: s0 + 4, srcEnd: s0 + 8, camFrom: FULL, camTo: TOP_LABEL, camMove: 2.4,
      sub: ['출발하면 알아서 기록을 시작해요'], chip: true, bgmDb: 0},
    {n: 4, dur: 3, kind: 'footage', srcStart: s0 + 8, srcEnd: t.tap1 - 3.8, camFrom: FULL, camTo: FULL,
      sub: ['화면은 이게 전부예요'], chip: true, bgmDb: 0},
    {n: 5, dur: 4, kind: 'footage', srcStart: t.tap1 - 3.8, srcEnd: t.tap1 + 0.2, camFrom: FULL, camTo: TALK, camMove: 2,
      sub: ['18:57 · 정체 구간'], chip: true, tapAt: 3.8, bgmDb: 0, cues: [{src: 'sfx/tap.mp3', at: 3.8, volume: 0.35}]},
    {n: 6, dur: 5, kind: 'footage', srcStart: t.tap1 + 0.15, freeze: true, camFrom: TALK, camTo: TALK,
      sub: ['오늘 팀장 때문에 진짜 힘들었다'], driver: true, chip: true, bgmDb: -6,
      cues: [{src: 'vo/driver-1.mp3', at: 0.4, volume: 1}]},
    {n: 7, dur: 4, kind: 'footage', srcStart: t.ack1 - 0.1, srcEnd: t.ack1 + 3.9, camFrom: TALK, camTo: {...TALK, scale: 1.1},
      sub: ['대답은 한마디뿐'], appAudio: true, chip: true, bgmDb: -8},
    {n: 8, dur: 4, kind: 'footage', srcStart: t.ack1 + 3.9, srcEnd: t.tap2 - 2.8, camFrom: FULL, camTo: FULL,
      sub: ['화면은 그대로예요'], chip: true, bgmDb: 0},
    {n: 9, dur: 3, kind: 'footage', srcStart: t.tap2 - 2.8, srcEnd: t.tap2 + 0.2, camFrom: FULL, camTo: TALK, camMove: 1.6,
      sub: ['19:08'], chip: true, tapAt: 2.8, bgmDb: 0, cues: [{src: 'sfx/tap.mp3', at: 2.8, volume: 0.35}]},
    // 운전자 대사 2 (8.2초) 가 0.4초 여유를 두고 들어가도록 대본의 6초에서 9초로 늘렸다.
    {n: 10, dur: 9, kind: 'footage', srcStart: t.tap2 + 0.15, freeze: true, camFrom: TALK, camTo: TALK,
      sub: ['내일 거래처 서류 다시 보내야 해', '아 노을 예쁘다'], driver: true, chip: true, bgmDb: -6,
      cues: [{src: 'vo/driver-2.mp3', at: 0.4, volume: 1}]},
    {n: 11, dur: 4, kind: 'footage', srcStart: t.ack2 - 0.1, srcEnd: t.ack2 + 3.9, camFrom: TALK, camTo: FULL,
      sub: ['할 일과 기분이 섞여도 괜찮아요'], appAudio: true, chip: true, bgmDb: -8},
    {n: 12, dur: 4, kind: 'footage', srcStart: t.ack2 + 3.9, srcEnd: t.cardShown - 2.5, camFrom: FULL, camTo: FULL,
      sub: ['집 앞, 기어 P'], chip: true, bgmDb: 1.5, cues: [{src: 'sfx/gear-park.mp3', at: 3.0, volume: 0.5}]},
    {n: 13, dur: 5, kind: 'footage', srcStart: t.cardShown - 2.5, srcEnd: t.cardShown + 2.5, camFrom: FULL, camTo: FULL,
      sub: ['도착하면 한 장으로'], bgmDb: 0},
    // 14~17 은 카드가 멈춰 있는 화면이라 정지 프레임에 카메라만 움직인다. 15 만 요약 원음 때문에 실제로 재생한다.
    {n: 14, dur: 7, kind: 'footage', srcStart: t.cardShown + 0.6, freeze: true,
      camFrom: {scale: 1.5, cx: 0.333, cy: 0.2}, camTo: {scale: 1.5, cx: 0.42, cy: 0.2}, camMove: 7,
      sub: ['51분, 23km'], bgmDb: 0},
    {n: 15, dur: 10, kind: 'footage', srcStart: t.cardShown + 0.5, srcEnd: t.cardShown + 10.5,
      camFrom: {scale: 1.6, cx: 0.3, cy: 1}, camTo: {scale: 1.6, cx: 0.3, cy: 1}, appAudio: true, bgmDb: -8},
    {n: 16, dur: 6, kind: 'footage', srcStart: t.summaryEnd + 1.2, freeze: true,
      camFrom: {scale: 1.3, cx: 0.3, cy: 0.3}, camTo: {scale: 1.5, cx: 0.3, cy: 0.3}, camMove: 2.2,
      sub: ['힘든 데서 시작해 노을로'], bgmDb: 0},
    {n: 17, dur: 7, kind: 'footage', srcStart: t.summaryEnd + 1.2, freeze: true,
      camFrom: {scale: 1.6, cx: 0.3, cy: 0.33}, camTo: {scale: 1.6, cx: 0.3, cy: 0.58}, camMove: 7,
      sub: ['말한 그대로, 세 줄로'], bgmDb: 0},
  ];
  if (withDeleteCut && t.deleteTap != null) {
    cuts.push({n: 18, dur: 3, kind: 'footage', srcStart: t.deleteTap - 1.5, srcEnd: t.deleteTap + 1.5,
      camFrom: {scale: 1.4, cx: 0.5, cy: 0.5}, camTo: {scale: 1.4, cx: 0.5, cy: 0.5},
      sub: ['틀리게 들었으면 지우면 돼요'], tapAt: 1.5, bgmDb: 0, cues: [{src: 'sfx/tap.mp3', at: 1.5, volume: 0.35}]});
  }
  cuts.push({n: 19, dur: 5, kind: 'black', bgmDb: 0});
  for (const c of cuts) {
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

/** 앱 시계(run-demo 배속)로 녹화 초를 HH:MM 로 바꾼다. 18:40 = clock0 */
export const appClock = (src: number, t: Timings = timings) => {
  const min = 18 * 60 + 40 + Math.floor(((src - t.clock0) * t.clockSpeed) / 60);
  const m = Math.max(18 * 60 + 40, min);
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
};
