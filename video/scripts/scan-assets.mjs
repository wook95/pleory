// public/ 아래 선택 자산(footage, VO, BGM, SFX)이 있는지 src/assets.json 에 적는다.
// 컴포지션은 이 파일만 보고 없는 자산을 건너뛴다 (렌더 중 404 방지).
import {existsSync, writeFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const files = [
  'footage.mp4', 'footage-d1.mp4', 'footage-d2.mp4',
  'vo/driver-1.mp3', 'vo/driver-2.mp3', 'vo/driver-3.mp3', 'vo/driver-4.mp3', 'vo/ramble-1.mp3',
  'bgm.mp3',
  'sfx/car-ambience.mp3', 'sfx/turn-signal.mp3', 'sfx/tap.mp3', 'sfx/gear-park.mp3',
];
const assets = Object.fromEntries(files.map((f) => [f, existsSync(join(root, 'public', f))]));
writeFileSync(join(root, 'src/assets.json'), JSON.stringify(assets, null, 2) + '\n');
console.log('assets:', Object.entries(assets).map(([k, v]) => `${k}=${v ? 'yes' : 'no'}`).join(' '));
