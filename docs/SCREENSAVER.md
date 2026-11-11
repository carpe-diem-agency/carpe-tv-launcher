# Screensaver / Заставка

## English

Open **Settings → Screensaver**. Choose Off or 1, 3, 5, 10, 15, 30 minutes (default: 5).
The timer runs only while Carpe TV is resumed and owns window focus. Remote input resets it; another app or a dialog pauses it. It does not run over YouTube, Netflix or other apps. No background service, wake lock, alarms or new permissions are used. Optional media downloads run only during an active built-in screensaver session.

**Carpe TV · photos and videos** works without an external app or internet. It uses the existing offline wallpaper collection, changes photos every minute and moves the clock between corners. Images keep their proportions. Any ordinary remote button dismisses it; volume buttons continue controlling sound. It does not request audio focus, so background music can continue. Android still controls screen sleep and can start its own screensaver earlier.

**Aerial Views · video landscapes** is optional and requires [Aerial Views](https://github.com/theothernt/AerialViews) installed and configured separately. Carpe TV invokes its documented preview Activity, not a private DreamService API. Its video sources, playback and caching remain managed by Aerial Views. No GPL code or third-party video collection is copied into this project. If it is removed, disabled or cannot launch, offline photos are the fallback.

**Android screensaver settings** opens the system page, falling back to general Android settings if unavailable. Firmware may hide third-party screensaver selection or timeout controls. Carpe TV does not change secure settings or request write-settings access. Turning our timer Off does not turn off the Android screensaver or device sleep.

## Українською

Відкрий **Налаштування → Заставка**. Доступні вимкнення та 1, 3, 5, 10, 15, 30 хвилин; початково — 5 хвилин.

Таймер працює лише у відкритому Carpe TV з активним фокусом. Пульт скидає очікування; інша програма або діалог призупиняє його. Заставка не накриває YouTube чи Netflix.

Вбудовані фото працюють офлайн без Aerial Views, змінюються щохвилини та показують годинник. Звичайна кнопка пульта повертає назад; гучність регулюється без закриття. Заставка не перехоплює звук фонової музики й не заважає системному сну.

Для відеопейзажів можна вибрати окремо встановлену **Aerial Views**. Її джерела та кеш налаштовуються в самій Aerial Views. Якщо вона недоступна, відкриються наші офлайн-фото. Відео Apple та інших авторів у наш APK не включені.

Кнопка **Налаштування заставки Android** відкриває системні параметри. На деяких прошивках вибір заставки й таймера прихований. Системна заставка може запускатися раніше нашої; вимкнення таймера Carpe TV не вимикає заставку Android чи сон.

## Online collection (0.2.6)

Online downloads are optional; disabling them keeps existing cached media available offline. Clearing the cache then leaves only built-in photos. When enabled, a resumed built-in screensaver refreshes a small catalog at most daily, then downloads at most three missing items per session. It never streams a remote video directly. Existing downloads are reused and play offline; there is no background fetch schedule outside the screensaver.

- Wikimedia Commons: landscape JPEG thumbnails up to 1920px and a reviewed [CC0 snowy landscape video](https://commons.wikimedia.org/wiki/File:Aerial_view_of_snowy_landscape.webm), selecting a 1080p VP9 derivative instead of the 185 MB original. Photos are filtered to CC0, CC BY 3.0 or CC BY 4.0. Each downloaded item retains its author, source-page URL and license URL. This does not assume that every Commons file is unrestricted.
- NASA Image and Video Library: NASA/JSC Earth/horizon photos via its public API. Explicit copyright metadata is excluded. Reviewed clean Earthrise video: [NASA SVS 3936](https://svs.gsfc.nasa.gov/3936/), credit NASA/Goddard SVS / Ernie Wright. [NASA media guidelines](https://www.nasa.gov/nasa-brand-center/images-and-media/).
- No Apple, Jetson Creative or Pexels catalog is mirrored. Aerial Views remains a separate optional provider.

Storage is limited to 500 MiB and 24 complete items in `cache/screensaver-media-v1`. Photos are capped at 12 MiB, videos at 80 MiB / 1080p and four minutes. HTTPS hosts and every redirect are allowlisted. Responses without a trustworthy Content-Length remain subject to the same streaming byte cap. Media is validated before publication.

The default free-space reserve is 1024 MiB; 512 MiB is selectable for this device. Available bytes are checked before downloading and each MiB during transfer. Low space removes oldest unused cache entries and refuses a new file if the reserve cannot be met. Active playback and one most-recent known-good item are protected. Only this private media-cache namespace is cleaned; other apps, downloads and user files are untouched. Partial files are removed after failure/cancellation and recovered after a process restart. The bounded catalog is retained separately as small metadata, so deletion of Android cache can cause media to be downloaded again.

Downloaded media shows attribution on screen; **Sources and credits** lists the source and license URLs. Video is muted, explicitly requests no audio focus, and disables the native player's keep-screen-on behavior. On pause, transfers are cancelled, decoding is invalidated and the video player/pins are released. Already-blocked network reads have an 8-second timeout; no new media is published after cancellation.

## Онлайн-добірка

У **Налаштування → Заставка** можна вимкнути дозавантаження, подивитися розмір кешу й вільне місце, вибрати резерв 1024/512 МБ, очистити завантажені файли та переглянути авторів.

Каталог перевіряється раз на добу під час роботи власної заставки. За один її запуск додається до трьох нових файлів; уже завантажені не качаються повторно. Без мережі працює кеш або вбудовані фото. Відео відтворюється без звуку й не перехоплює фонову музику.

При нестачі місця видаляються лише старі файли нашого кешу. Поточне відео та останній придатний файл захищені. Якщо резерву все одно не вистачає, нове завантаження не починається. Завантаження зупиняються при виході із заставки.
