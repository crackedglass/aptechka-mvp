import { useEffect, useRef, useState } from "react";
import { Button, Icon } from "./components";

const exampleRecording = { url: "./media/camera-cctv-preview.mp4", name: "Камера 01 · Комната" };
const exampleMoments = [
  { start: 0, description: "Общий план комнаты с камеры под потолком" },
  { start: 5, description: "Люди за столом и в зоне отдыха" },
  { start: 10, description: "Продолжение наблюдения за комнатой" },
];

const exampleDayEvents = [
  { time: "08:30", title: "Пришла сиделка", detail: "Сиделка вошла в комнату, поздоровалась и уточнила самочувствие." },
  { time: "08:40", title: "Измерили давление", detail: "Сиделка помогла надеть манжету и провести измерение тонометром." },
  { time: "09:00", title: "Позавтракали", detail: "Сиделка принесла завтрак и помогла устроиться за столом." },
  { time: "09:15", title: "Приняли лекарства", detail: "Сиделка передала лекарства по назначению и стакан воды." },
  { time: "11:30", title: "Немного прошлись по комнате", detail: "Сиделка сопровождала бабушку во время небольшой прогулки по комнате." },
  { time: "13:00", title: "Пообедали", detail: "Сиделка подала обед, после еды помогла вернуться в кресло." },
];

function timestamp(seconds: number) {
  const value = Math.max(0, Math.floor(seconds));
  const hours = Math.floor(value / 3600);
  return [ ...(hours ? [hours] : []), Math.floor(value / 60) % 60, value % 60 ]
    .map(part => String(part).padStart(2, "0")).join(":");
}

export function CameraRecording({ onClose }: { onClose: () => void }) {
  const player = useRef<HTMLVideoElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const [recording, setRecording] = useState(exampleRecording);
  const [duration, setDuration] = useState(0);
  const [position, setPosition] = useState(0);
  const [error, setError] = useState("");
  useEffect(() => () => { if (recording.url.startsWith("blob:")) URL.revokeObjectURL(recording.url); }, [recording]);
  const isExample = recording.url === exampleRecording.url;
  // Keep long recordings navigable without rendering thousands of buttons.
  const interval = Math.max(30, Math.ceil(duration / 120 / 30) * 30);
  const segments = isExample
    ? exampleMoments.filter(moment => moment.start < duration)
    : Array.from({ length: Math.ceil(duration / interval) }, (_, i) => ({ start: i * interval, description: `Фрагмент записи ${i + 1}` }));
  const seek = (time: number) => {
    if (!player.current) return;
    player.current.currentTime = time;
    setPosition(time);
  };

  return <section className="camera-page" aria-label="Запись с камеры">
    <div className="camera-page-heading"><button className="text-link" onClick={onClose}><Icon name="back" />К устройствам</button><h1>Камера</h1></div>
    <div className="camera-recording">
      <div className="camera-video-frame">
      {recording ? <video key={recording.url} ref={player} src={recording.url}
        className="camera-player" controls playsInline muted preload="auto" aria-label="Запись с камеры"
        onLoadedMetadata={event => {
          const length = event.currentTarget.duration;
          if (Number.isFinite(length) && length > 0) setDuration(length);
          else setError("Не удалось определить длительность. Выберите другую запись.");
        }}
        onTimeUpdate={event => setPosition(event.currentTarget.currentTime)}
        onError={() => { setDuration(0); setError("Не удалось открыть видео. Попробуйте другую запись в формате MP4 или WebM."); }}
      /> : <div className="camera-player camera-empty">
        <Icon name="camera" /><strong>Выберите запись для просмотра</strong>
        <span>Видео и переходы по таймкодам</span>
      </div>}
      {isExample && <div className="camera-video-label"><span>КАМЕРА 01 · КОМНАТА</span><span>ПРИМЕР · {timestamp(position)}</span></div>}
      </div>
      <div className="camera-recording-toolbar">
        <div><strong>{recording?.name || "Запись с устройства"}</strong>
          <p className="monitor-caption">{isExample ? "Предпросмотр Adobe Stock · 15 секунд" : "Файл открывается только в вашем браузере"}</p></div>
        <Button kind="white" onClick={() => input.current?.click()}>{recording ? "Заменить" : "Выбрать видео"}</Button>
      </div>
      {!isExample && <button className="text-link" onClick={() => { setRecording(exampleRecording); setDuration(0); setPosition(0); setError(""); }}>Вернуть пример записи</button>}
      <input ref={input} type="file" accept="video/*" hidden onChange={event => {
        const file = event.target.files?.[0];
        if (!file) return;
        setDuration(0); setPosition(0); setError("");
        setRecording({ url: URL.createObjectURL(file), name: file.name });
        event.target.value = "";
      }} />
      {error && <p className="camera-recording-error" role="alert">{error}</p>}
      {isExample && <section className="camera-day-events" aria-labelledby="camera-day-title">
        <div className="camera-timecodes-heading"><h3 id="camera-day-title">История событий</h3></div>
        <div className="camera-day-list">{exampleDayEvents.map(event => <details className="camera-day-event" key={event.time}>
          <summary><span className="camera-timecode-range">{event.time}</span><span className="camera-timecode-description">{event.title}</span><Icon name="arrow" /></summary>
          <div className="camera-day-event-details"><p>{event.detail}</p></div>
        </details>)}</div>
      </section>}
      <div className="camera-timecodes-heading"><h3>{isExample ? "Фрагменты текущей записи" : "Таймкоды"}</h3>
        {duration > 0 && <span>{timestamp(position)} / {timestamp(duration)}</span>}</div>
      {duration > 0 ? <div className="camera-timecodes" aria-label="Переход к фрагменту записи">
        {segments.map(({ start, description }, index) => {
          const end = segments[index + 1]?.start ?? duration;
          const active = position >= start && (position < end || index === segments.length - 1);
          return <button type="button" key={start} aria-current={active ? "true" : undefined}
            className={active ? "active" : ""} onClick={() => seek(start)}
            aria-label={`${timestamp(start)} — ${description}`}>
            <span className="camera-timecode-range">{timestamp(start)} — {timestamp(end)}</span><span className="camera-timecode-description">{description}</span>
          </button>;
        })}
      </div> : <p className="monitor-caption">После выбора видео здесь появятся интервалы записи. Нажмите на таймкод, чтобы перейти к нужному моменту.</p>}
    </div>
  </section>;
}
