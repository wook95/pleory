import {loadFont} from '@remotion/fonts';
import {staticFile} from 'remotion';

export const FPS = 30;
export const FONT = 'Pretendard';

for (const [weight, file] of [
  ['400', 'Regular'],
  ['500', 'Medium'],
  ['600', 'SemiBold'],
  ['700', 'Bold'],
] as const) {
  loadFont({family: FONT, url: staticFile(`fonts/Pretendard-${file}.woff2`), weight});
}

export const COLOR = {
  bg: '#000000',
  text: '#FFFFFF',
  driver: '#C8C8C8',
  chip: '#9A9A9A',
  small: '#8A8A8A',
};

// 녹화 앱 영역 1682x1210 을 1080p 위쪽에 둔다. 아래 띠는 자막 자리.
export const FOOTAGE = {w: 1674, h: 1202}; // 앱 영역 1682x1210 에서 테두리 4px 를 뺐다
export const FRAME_H = 840;
export const FRAME_W = Math.round((FRAME_H * FOOTAGE.w) / FOOTAGE.h);
export const FRAME_TOP = 56;
export const SUB_Y = 978; // 자막 한 줄의 세로 중심
