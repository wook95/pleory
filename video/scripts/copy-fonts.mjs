// Pretendard (OFL) 를 npm 패키지에서 public/fonts 로 복사한다. 설치 후 오프라인으로 렌더된다.
import {copyFileSync, mkdirSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = join(root, 'node_modules/pretendard/dist/web/static/woff2');
const dst = join(root, 'public/fonts');
mkdirSync(dst, {recursive: true});
for (const w of ['Regular', 'Medium', 'SemiBold', 'Bold']) {
  copyFileSync(join(src, `Pretendard-${w}.woff2`), join(dst, `Pretendard-${w}.woff2`));
}
