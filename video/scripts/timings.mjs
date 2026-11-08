// flow.log(logcat -v time) + rec_start.txt -> src/timings.json (녹화 시작 기준 초).
// 로그가 없으면 대본의 1차 녹화 기준값을 쓴다.
import {existsSync, readFileSync, writeFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const take = process.argv[2] ?? join(root, '../apk/video/take2');
const out = join(root, 'src/timings.json');
const footage = join(root, 'public/footage.mp4');
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

const probeDuration = () => {
  if (!existsSync(footage)) return null;
  const r = spawnSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', footage], {encoding: 'utf8'});
  const d = parseFloat(r.stdout);
  return Number.isFinite(d) ? d : null;
};

// 카드 전환(S0 -> S1)은 화면이 크게 바뀌는 첫 프레임이라 rec_start(초 단위, 최대 1초 오차)를 보정하는 기준으로 쓴다.
const sceneChange = (from, to) => {
  if (!existsSync(footage)) return null;
  const r = spawnSync('ffmpeg', ['-hide_banner', '-ss', String(from), '-t', String(to - from), '-i', footage,
    '-an', '-vf', "select='gt(scene,0.02)',showinfo", '-f', 'null', '-'], {encoding: 'utf8'});
  const m = /pts_time:([\d.]+)/.exec(r.stderr);
  return m ? from + parseFloat(m[1]) : null;
};

const flowPath = join(take, 'flow.log');
const startPath = join(take, 'rec_start.txt');
let t = {...defaults};

if (existsSync(flowPath) && existsSync(startPath)) {
  const rec0 = toSec(readFileSync(startPath, 'utf8').trim());
  const ev = [];
  for (const line of readFileSync(flowPath, 'utf8').split('\n')) {
    const m = /^\d\d-\d\d (\d\d:\d\d:\d\d\.\d+) I\/PleoryFlow\(\s*\d+\): (.*)$/.exec(line);
    if (m) ev.push({t: toSec(m[1]) - rec0, msg: m[2]});
  }
  const find = (re, after = -Infinity) => ev.find((e) => e.t > after && re.test(e.msg))?.t ?? null;

  const tap1 = find(/^event TalkTapped/);
  const ack1 = find(/^command Say\(text=적어뒀어\)/, tap1 ?? -Infinity);
  const ack1Done = find(/^tts onDone/, ack1 ?? -Infinity);
  const tap2 = find(/^event TalkTapped/, tap1 ?? -Infinity);
  const ack2 = find(/^command Say\(text=적어뒀어\)/, tap2 ?? -Infinity);
  const ack2Done = find(/^tts onDone/, ack2 ?? -Infinity);
  const gearP = find(/^event GearChanged\(gear=P\)/, tap2 ?? -Infinity);
  const cardShown = find(/^output showCard/, tap2 ?? -Infinity);
  const summaryStart = find(/^summary speak/, cardShown ?? -Infinity);
  const summaryEnd = find(/^event SummaryDone/, summaryStart ?? -Infinity);
  const deleteTap = find(/delete|Delete/, summaryEnd ?? -Infinity);

  const got = {tap1, ack1, ack1Done, tap2, ack2, ack2Done, gearP, cardShown, summaryStart, summaryEnd};
  const missing = Object.entries(got).filter(([, v]) => v == null).map(([k]) => k);
  if (missing.length) console.warn(`timings: 로그에 없음 -> 기본값 사용: ${missing.join(', ')}`);
  for (const [k, v] of Object.entries(got)) if (v != null) t[k] = v;
  t.deleteTap = deleteTap;
  t.source = `flow.log (rec_start ${readFileSync(startPath, 'utf8').trim()})`;
  // 앱 시계: 첫 한마디가 18:57 (run-demo 시나리오). clock0 = 18:40 에 해당하는 녹화 초.
  t.clock0 = t.tap1 - (17 * 60) / SPEED;

  // 화면 보정: 로그의 showCard 와 실제 카드 전환 프레임의 차이를 녹화 시작 오차로 본다.
  const scene = sceneChange(Math.max(0, t.cardShown - 2), t.cardShown + 1);
  if (scene != null) {
    const shift = Math.max(-1.2, Math.min(1.2, scene - t.cardShown));
    t.syncShift = Number(shift.toFixed(3));
    for (const k of ['clock0', 'tap1', 'ack1', 'ack1Done', 'tap2', 'ack2', 'ack2Done', 'gearP', 'cardShown', 'summaryStart', 'summaryEnd', 'deleteTap']) {
      if (t[k] != null) t[k] += shift;
    }
    console.log(`timings: 카드 전환 프레임 ${scene.toFixed(2)}s, 보정 ${shift.toFixed(2)}s`);
  } else {
    console.warn('timings: 카드 전환 프레임을 못 찾음 -> 보정 없음');
  }
} else {
  console.warn(`timings: ${flowPath} 또는 rec_start.txt 없음 -> 대본 기본값`);
}

const dur = probeDuration();
if (dur != null) t.footageDuration = dur;
for (const [k, v] of Object.entries(t)) if (typeof v === 'number') t[k] = Number(v.toFixed(3));
writeFileSync(out, JSON.stringify(t, null, 2) + '\n');
console.log(`wrote ${out}`);
console.log(JSON.stringify(t));
