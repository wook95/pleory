import {Composition} from 'remotion';
import {Film, filmSchemaDefaults, type FilmProps} from './Film';
import {buildCuts, totalFrames} from './cuts';
import {FPS} from './theme';

export const RemotionRoot: React.FC = () => (
  <Composition
    id="PleoryFilm"
    component={Film}
    width={1920}
    height={1080}
    fps={FPS}
    durationInFrames={totalFrames(buildCuts(filmSchemaDefaults.withDeleteCut))}
    defaultProps={filmSchemaDefaults}
    calculateMetadata={({props}: {props: FilmProps}) => ({
      durationInFrames: totalFrames(buildCuts(props.withDeleteCut)),
    })}
  />
);
