# AniBeat — Android (Java, Android SDK)

Нативное приложение-оболочка на **Java** с Android SDK. Внутри — **тот же самый сайт**
(сборка `web/dist` без единого изменения), который отображается системным `WebView`
на реальном origin `https://appassets.androidplatform.net` (через `WebViewAssetLoader`,
как это делает Chrome Custom Tabs / Trusted Web Activity). Благодаря этому
`localStorage`, `IndexedDB`, `fetch` с CORS, `canvas`, clipboard и `navigator.share`
работают в точности так же, как в браузере.

## Что делает нативный слой

| Компонент | Назначение |
|---|---|
| `MainActivity` | WebView, safe-area, тёмная тема, immersive-видео (fullscreen), системная кнопка «Назад» |
| `WebAppBridge` | Мост `window.AniBeatNative`: media session, share-лист, сохранение blob-файлов |
| `PlaybackService` | foreground-сервис + `MediaSessionCompat`: воспроизведение в фоне, уведомление и экран блокировки, аудиофокус |
| `FileSaver` | сохранение скачанных треков в «Загрузки» (MediaStore, Android 10+) |
| `MediaArtwork` | обложка трека для уведомления |

Файл `web/public/android-bridge.js` (подключается в `index.html`) заполняет ровно те
API, которых нет в WebView, и является no-op в обычном браузере — сайт остаётся
идентичным.

## Сборка

```bash
# локально
cd web && npm ci && npm run build
rm -rf ../android/app/src/main/assets && mkdir -p ../android/app/src/main/assets
cp -r dist/. ../android/app/src/main/assets/
cd ../android && gradle assembleRelease -PversionCode=1 -PversionName=1.0.0
```

В CI (`.github/workflows/android.yml`) всё делается автоматически: сборка сайта →
подписанный `assembleRelease` → `apksigner verify` → `handoff/` → GitHub Release → запуск
на эмуляторе Android 14 со скриншотами в `docs/android-screenshots/`.

## Подпись и обновления

Ключ `android/keystore/anibeat-release.jks` (alias `anibeat`) создаётся один раз и
коммитится в репозиторий, поэтому **каждая новая сборка имеет тот же
сертификат и устанавливается поверх предыдущей версии как обновление**
(`versionCode` = номер сборки, `versionName` = `1.0.<build>`).

Пароли можно переопределить секретами репозитория:
`ANIBEAT_STORE_PASSWORD`, `ANIBEAT_KEY_ALIAS`, `ANIBEAT_KEY_PASSWORD`.
