// flow.log(logcat -v time) + rec_start.txt -> src/timings.json (녹화 시작 기준 초).
// v1: node timings.mjs [take=../apk/video/take2]           -> 평평한 키(cuts.ts 가 지금 읽는 것)
// v2: node timings.mjs <day1 take> <day2 take>              -> 평평한 키(1일차 기준, 호환용) + d1·d2 (video-script.md "편집 쪽에 넘길 것")
//     take 하나만 주고 그 안에 day1/·day2/ 가 있으면(tools/run-demo.sh v2 의 apk/video/v2) 그것을 쓴다.
// 로그가 없으면 대본의 1차 녹화 기준값을 쓴다.
import {existsSync, readFileSync, writeFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
let [take, take2] = process.argv.slice(2);
take ??= join(root, '../apk/video/take2');
if (!take2 && existsSync(join(take, 'day1/flow.log'))) [take, take2] = [join(take, 'day1'), join(take, 'day2')];
const out = join(root, 'src/timings.json');
const v2 = take2 != null;
// v2 는 하루에 footage 하나(scripts/prepare.sh ../apk/video/v2)
const footage = join(root, v2 ? 'public/footage-d1.mp4' : 'public/footage.mp4');
const footage2 = join(root, 'public/footage-d2.mp4');
const SPEED = 30; // tools/run-demo.sh 기본 배속

// 대본 기본값 (지난 1차 녹화: 적어뒀어 약 39초·61초, 요약 약 106~110초)
const defaults = {
  source: 'defaults',
  clockSpeed: SPEED,
  clock0: 2.5,
  tap1: 36.5, ack1: 39, ack1Done: 41,
  tap2: 58.5, ack2: 61, ack2Done: 63,
  gearP: 105, cardShown: 105,
  summaryStart: 106, summaryEnd: 110,
  deleteTap: null,
  footageDuration: 116,
  syncShift: 0,
};

const toSec = (hms) => {
  const [h, m, s] = hms.split(':').map(Number);
  return h * 3600 + m * 60 + s;
};

const probeDuration = (file = footage) => {
  if (!existsSync(file)) return null;
  const r = spawnSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file], {encoding: 'utf8'});
  const d = parseFloat(r.stdout);
  return Number.isFinite(d) ? d : null;
};

// 카드 전환(S0 -> S1)은 화면이 크게 바뀌는 첫 프레임이라 rec_start(최대 1초 오차)를 보정하는 기준으로 쓴다.
const sceneChange = (from, to, file = footage) => {
  if (!existsSync(file)) return null;
  const r = spawnSync('ffmpeg', ['-hide_banner', '-ss', String(from), '-t', String(to - from), '-i', file,
    '-an', '-vf', "select='gt(scene,0.02)',showinfo", '-f', 'null', '-'], {encoding: 'utf8'});
  const m = /pts_time:([\d.]+)/.exec(r.stderr);
  return m ? from + parseFloat(m[1]) : null;
};

/** take 의 PleoryFlow·PleoryDemo 줄을 녹화 시작 기준 초로. 없으면 null. */
const readEvents = (dir) => {
  const flowPath = join(dir, 'flow.log');
  const startPath = join(dir, 'rec_start.txt');
  if (!existsSync(flowPath) || !existsSync(startPath)) return null;
  const recStart = readFileSync(startPath, 'utf8').trim();
  const rec0 = toSec(recStart);
  const ev = [];
  for (const line of readFileSync(flowPath, 'utf8').split('\n')) {
    const m = /^\d\d-\d\d (\d\d:\d\d:\d\d\.\d+) [IWE]\/(PleoryFlow|PleoryDemo)\(\s*\d+\): (.*)$/.exec(line);
    if (m) ev.push({t: toSec(m[1]) - rec0, msg: m[3]});
  }
  const find = (re, after = -Infinity) => ev.find((e) => e.t > after && re.test(e.msg))?.t ?? null;
  const findLast = (re, before = Infinity) => ev.filter((e) => e.t < before && re.test(e.msg)).at(-1)?.t ?? null;
  const xy = (name) => {
    const e = ev.find((x) => new RegExp(`^tap ${name} x=`).test(x.msg));
    const m = e && /x=(\d+) y=(\d+)/.exec(e.msg);
    return m ? {x: Number(m[1]), y: Number(m[2])} : null;
  };
  return {recStart, ev, find, findLast, xy};
};

const warnMissing = (label, got) => {
  const missing = Object.entries(got).filter(([, v]) => v == null).map(([k]) => k);
  if (missing.length) console.warn(`timings ${label}: 로그에 없음: ${missing.join(', ')}`);
};

/** v1 take(한마디 2) 또는 v2 1일차(한마디 3): 한마디·카드·요약 키. */
const parseDrive = ({find}, utterances = 2) => {
  const tap1 = find(/^event TalkTapped/);
  const ack1 = find(/^command Say\(text=적어뒀어\)/, tap1 ?? -Infinity);
  const ack1Done = find(/^tts onDone/, ack1 ?? -Infinity);
  const tap2 = find(/^event TalkTapped/, tap1 ?? -Infinity);
  const ack2 = find(/^command Say\(text=적어뒀어\)/, tap2 ?? -Infinity);
  const ack2Done = find(/^tts onDone/, ack2 ?? -Infinity);
  const tap3 = utterances < 3 ? null : find(/^event TalkTapped/, tap2 ?? -Infinity);
  const ack3 = tap3 == null ? null : find(/^command Say\(text=적어뒀어\)/, tap3);
  const ack3Done = ack3 == null ? null : find(/^tts onDone/, ack3);
  const last = tap3 ?? tap2 ?? -Infinity;
  const gearP = find(/^event GearChanged\(gear=P\)/, last);
  const cardShown = find(/^output showCard/, last);
  const summaryStart = find(/^summary speak/, cardShown ?? -Infinity);
  const summaryEnd = find(/^event SummaryDone/, summaryStart ?? -Infinity);
  return {tap1, ack1, ack1Done, tap2, ack2, ack2Done, tap3, ack3, ack3Done, gearP, cardShown, summaryStart, summaryEnd};
};

const parseDay1 = (L) => {
  const drive = parseDrive(L, 3);
  const qrOpen = L.find(/^qr open /);
  const t = {
    clock0: L.find(/^demo start day=1/),
    consentTap: L.find(/^tap consent x=/),
    ...drive,
    deleteTap: L.find(/^tap delete x=/),
    deleteConfirmTap: L.find(/^tap delete-confirm x=/),
    qrTap: L.find(/^tap qr x=/),
    qrOpen,
    qrClose: L.find(/^qr close/, qrOpen ?? -Infinity),
    historyTap: L.find(/^tap history x=/),
    todoOnlyTap: L.find(/^tap todo-only x=/),
  };
  warnMissing('d1', t);
  t.tapXY = Object.fromEntries(['consent', 'delete', 'delete-confirm', 'qr', 'qr-close', 'history', 'todo-only'].map((n) => [n, L.xy(n)]));
  return t;
};

const parseDay2 = (L) => {
  const gleoAccepted = L.find(/^gleo ADD_DRIVE_NOTES? accepted/);
  const t = {
    clock0: L.find(/^demo start day=2/),
    gleoInject: L.findLast(/^gleo (inject A|simulated)/, gleoAccepted ?? Infinity),
    gleoAccepted,
    gleoResponse: L.find(/^gleo response sent fn=/, gleoAccepted ?? -Infinity),
    gearP: L.find(/^event GearChanged\(gear=P\)/, gleoAccepted ?? -Infinity),
    cardShown: L.find(/^output showCard/, gleoAccepted ?? -Infinity),
  };
  t.remindStart = L.find(/^summary speak/, t.cardShown ?? -Infinity);
  t.summaryEnd = L.find(/^event SummaryDone/, t.remindStart ?? -Infinity);
  t.remindDoneTap = L.find(/^tap remind-done x=/);
  warnMissing('d2', t);
  // B(직접 intent)면 컷 26 에 Gleo 대답 소리가 없다 — 편집이 "Gleo 호출 시뮬레이션" 표시를 넣는다
  t.gleoSimulated = L.ev.some((e) => /^gleo simulated/.test(e.msg));
  t.tapXY = {'remind-done': L.xy('remind-done')};
  t.source = `flow.log (rec_start ${L.recStart})`;
  return t;
};

const shiftAll = (obj, shift) => {
  for (const [k, v] of Object.entries(obj)) if (typeof v === 'number' && k !== 'clockSpeed' && k !== 'syncShift' && k !== 'footageDuration') obj[k] = v + shift;
};

let t = {...defaults};
const L1 = readEvents(take);
if (L1) {
  const d1 = v2 ? parseDay1(L1) : null;
  const drive = d1 ?? parseDrive(L1);
  for (const k of Object.keys(defaults)) if (drive[k] != null) t[k] = drive[k];
  t.deleteTap = drive.deleteTap ?? L1.find(/delete|Delete/, drive.summaryEnd ?? -Infinity);
  t.source = `flow.log (rec_start ${L1.recStart})`;
  // 앱 시계: v2 는 `demo start` 줄, v1 은 첫 한마디가 18:57 인 것으로 거꾸로 셈. clock0 = 18:40 에 해당하는 녹화 초.
  t.clock0 = d1?.clock0 ?? t.tap1 - (17 * 60) / SPEED;
  if (!v2) warnMissing('v1', parseDrive(L1));

  // 화면 보정: 로그의 showCard 와 실제 카드 전환 프레임의 차이를 녹화 시작 오차로 본다.
  const scene = sceneChange(Math.max(0, t.cardShown - 2), t.cardShown + 1);
  if (scene != null) {
    const shift = Math.max(-1.2, Math.min(1.2, scene - t.cardShown));
    t.syncShift = Number(shift.toFixed(3));
    shiftAll(t, shift);
    if (d1) shiftAll(d1, shift);
    console.log(`timings: 카드 전환 프레임 ${scene.toFixed(2)}s, 보정 ${shift.toFixed(2)}s`);
  } else {
    console.warn('timings: 카드 전환 프레임을 못 찾음 -> 보정 없음');
  }
  if (d1) {
    t.d1 = d1;
    const L2 = readEvents(take2);
    if (L2) {
      const d2 = parseDay2(L2);
      // 2일차도 같은 방식: 로그의 showCard 와 footage-d2 의 S0 -> 카드 전환 프레임 차이로 녹화 시작 오차를 지운다.
      const scene2 = d2.cardShown == null ? null : sceneChange(Math.max(0, d2.cardShown - 2), d2.cardShown + 1, footage2);
      d2.syncShift = 0;
      if (scene2 != null) {
        const shift2 = Math.max(-1.2, Math.min(1.2, scene2 - d2.cardShown));
        shiftAll(d2, shift2);
        d2.syncShift = Number(shift2.toFixed(3));
        console.log(`timings d2: 카드 전환 프레임 ${scene2.toFixed(2)}s, 보정 ${shift2.toFixed(2)}s`);
      } else {
        console.warn('timings d2: 카드 전환 프레임을 못 찾음 -> 보정 없음');
      }
      d2.footageDuration = probeDuration(footage2);
      t.d2 = d2;
    }
    else console.warn(`timings: ${take2} 의 flow.log·rec_start.txt 없음 -> d2 없음`);
  }
} else {
  console.warn(`timings: ${take} 의 flow.log 또는 rec_start.txt 없음 -> 대본 기본값`);
}

const dur = probeDuration();
if (dur != null) t.footageDuration = dur;
const round = (o) => { for (const [k, v] of Object.entries(o)) if (typeof v === 'number') o[k] = Number(v.toFixed(3)); };
round(t);
if (t.d1) round(t.d1);
if (t.d2) round(t.d2);
writeFileSync(out, JSON.stringify(t, null, 2) + '\n');
console.log(`wrote ${out}`);
console.log(JSON.stringify(t));
