# FitFlow Timer

Temporizador de intervalos **Tabata / EMOM / AMRAP** para Android. La APK se compila y
firma automaticamente en **GitHub Actions** (workflow `.github/workflows/build-apk.yml`).

## Funcionalidades

- Configurables: tiempo de preparacion, tiempo de trabajo, descanso entre rondas,
  numero de rondas, numero de ciclos y descanso entre ciclos.
- Presets: **Tabata** (10 / 20 / 10 x 8), **EMOM** (10 / 40 / 20 x 10, ronda de 60 s)
  y **AMRAP** (10 / 600 x 1), mas modo "A medida".
- **Pitido de cuenta atras en los ultimos 5 segundos** de la preparacion y de cada
  descanso entre rondas, con tonos diferenciados para inicio de trabajo y fin de sesion.
- **Compatible con la musica**: la app nunca pide foco de audio, por lo que Spotify,
  YouTube o cualquier otra app siguen sonando sin pausarse ni atenuarse.
  Ajuste de salida del pitido: `Media` (se mezcla) o `Sistema` (volumen independiente).
- **Rotacion a horizontal** opcional (ajuste de orientacion y boton en pantalla).
- El temporizador sigue funcionando **con la pantalla apagada** (servicio en primer plano
  + wakelock) y se calcula con `SystemClock.elapsedRealtime()`, por lo que no acumula deriva.
- Capa de **asistente de voz preparada**: el motor emite los eventos
  (`WorkStarted`, `HalfTime`, `WorkFinished`, `RestStarted`, `CycleFinished`,
  `SessionFinished`) a traves de la interfaz `Announcer`. En esta version se muestran
  en el panel "Avisos"; conectar un motor TTS es crear una clase `TtsAnnouncer` y
  registrarla en el servicio.

## Estructura

- `core/` - modulo Kotlin/JVM puro (modelo, `PhasePlanner`, `TimerEngine`, voz) con
  tests JUnit 5. No depende del SDK de Android.
- `app/`  - modulo Android (activities, servicio en primer plano, audio, ajustes).

## Compilacion

```bash
./gradlew :core:test :app:assembleRelease
```

El modulo `:app` solo se incluye si hay SDK de Android disponible (`ANDROID_HOME` o
`local.properties`), de modo que los tests de `core` se pueden ejecutar en local sin
instalar el SDK.

La firma de la release usa los secrets del repositorio:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`.
Si no estan presentes, la APK se firma con la clave de depuracion.