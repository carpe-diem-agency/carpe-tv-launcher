# Звуки інтерфейсу Carpe TV

## Погоджений обсяг — 2026-11-14

Власник обрав два конкретні локальні аудіофайли й погодив їх використання
в інтерфейсі.
Категорія Appearance перейменовується на **«Вигляд і звук»** українською та
**«Appearance & sound»** англійською. Потрібні вимикач звуків, гучність із
типовим значенням **70%** і прослуховування обох звуків.

Android реалізацію та QA probe перевірено в межах checkpoint нижче.
Фактичну роботу звуків власник підтвердив після ручного перегляду
2026-11-14: «куль працює можемо це комітити». Це не повний сценарний аудит
усіх діалогів і фізичного пульта. Попередній
modal fixture результат INPUT_UNVERIFIED — окрема історія перевірок,
не доказ працездатності чи дефекту нового sound UI.

## Відповідність подій і ресурсів

| Подія | Вибір власника | Ресурс у застосунку |
| --- | --- | --- |
| Навігація / зміна фокуса | Повний звук, приблизно 197 мс | `app/src/main/res/raw/ui_focus.wav` |
| Підтвердження / click | Перший відрізок тривалістю до 1 с | `app/src/main/res/raw/ui_click.wav` |

App-ресурси підготовлені з вибраних full-volume selections як **48 kHz,
PCM 16-bit, stereo без attenuation**. SHA-256 підготовлених bytes:

- `ui_focus.wav`: `a935cfc537ef1e0cf63f47e7721462e1defe7c16e996e2971e600e89285c5c72`.
- `ui_click.wav`: `047f5303701138bc07572b8541f1ca6af4ef388a3965a8a5ef6e161255c97f13`.

Ці hashes і provenance передані координатором при підготовці ресурсів;
вони не замінюють майбутні packaged-APK checks та прослуховування.

## Гучність і поведінка

- Типові 70% означають runtime gain **0.7** від незатухлого app resource.
  70%-preview вже має gain 0.7; його повторне множення на 0.7 дало б 0.49,
  тому він не використовується як джерело ресурсу з повторною attenuation.
- Налаштування звуку та гучності — app-private preferences. Не змінювати
  системну гучність, stream volume, mute, Home role, Shizuku або служби.
- Рух фокуса й підтвердження використовують свої окремі звуки; один action
  не повинен дублюватися native click та custom click. Автоматичне
  відновлення фокуса й loading callbacks не повинні створювати шумову чергу.
- Гучність 0% і вимкнені звуки блокують playback, включно з preview;
  preferences зберігаються. Preview послідовно програє navigation та click,
  якщо звуки ввімкнено, volume >0 і вікно активне; втрата вікна скасовує
  pending preview. Програмні перевірки цього шляху пройшли (див. нижче).
- Короткі UI звуки не запитують audio focus і не ставлять сторонню музику
  чи відео на паузу. Під час inactive/pause/destroy не лишається програвання,
  queued events або невивільнений player; після resume ресурси доступні
  без дублювання listeners. Lifecycle gates перевірено probe, але відсутність
  чутного впливу на сторонній audio ще потребує реального прослуховування.
- Звуковий feedback не має блокувати D-pad/OK, декодувати важкі ресурси на
  кожне натискання або накопичувати накладені звуки під час rapid navigation.

## Перевірки до готовності

1. Source review: mapping resources, permission/audio-focus відсутність,
   app-only preferences, pause/resume/release, відсутність native/custom
   double-click та подвійної attenuation.
2. Android build/lint і цільовий sound probe на isolated validation package;
   усі caches/build outputs/test artifacts — SSD/APFS згідно з `AGENTS.md`.
3. UI: «Вигляд і звук» / «Appearance & sound», toggle, volume label/range,
   default 70%, 0%, межі, preview обох звуків, D-pad/Back/focus restoration і persistence.
4. Runtime: правильний sample для focus/click, enabled/off/0% gates,
   rapid key repeats, modal controls, programmatic restore, пауза/вихід/
   recreation, відсутність pending playback після destroy.
5. Фактичне прослуховування на TV/пульті: гучність, відсутність дублювання,
   затримки й перехоплення стороннього audio. Сам факт виклику playback API
   у probe не доводить чутний результат або суб’єктивну якість.
6. Окремий packaged-APK resource/hash review і нижченаведений publication
   gate. Поки докази не отримані, sound acceptance залишається відкритим.

## Перевірений checkpoint і ручний перегляд — 2026-11-14

Початкова sound build **SUCCESS за 40 с**, lint **0 errors, 57 warnings**.
Probes `uisounds`, `libraryfocus`, `settingsfocus` — **усі PASS**; cleanup
**PASS**. Після уточнення типу мінімального Application context повторна
build **SUCCESS за 59 с**, lint **0 errors, 57 warnings**; фінальний
`uisounds` **PASS**, cleanup **PASS**. Не зменшуємо warning count до 56:
залишається heuristic `StaticFieldLeak` для тимчасового preview-root View.

Перевірено default 70%, off/0% playback gates, preview, preferences
persistence, lifecycle cleanup та native grid navigation. Позитивні
SoundPool stream IDs/counters свідчать про прийняті playback-виклики,
**не доводять чутність на TV**, якість звуку чи фізичний пульт.

Логи в `/Volumes/CarpeTVBuild/logs/`:

- `validation-build-uisounds-20261114.log`,
  `validation-build-uisounds-final-20261114.log`.
- `probe-uisounds-uisounds-20261114.log`,
  `probe-uisounds-uisounds-final-20261114.log`.
- `validation-probes-summary-uisounds-20261114.json`,
  `validation-probes-summary-uisounds-final-20261114.json`.

Після завершення automated cleanup на прямий запит власника координатор
повторно встановив **лише перевірену validation app**, без test APK, для
ручного перегляду. App-private `welcome_seen=true`; відкрито Main.
Screenshot: `/Volumes/CarpeTVBuild/manual-review/ui-sounds-home.png`.
Після підтвердження власника ручний перегляд завершено: validation app
видалена, попередній лаунчер повернуто явним запуском. `handoff.json`
має phase `owner_approved_cleanup_complete`, `cleanup_required=false`.
Readback підтвердив відсутність validation packages, незмінний Home
`agency.carpediem.tv.publicpreview` і accessibility services `null`.
Home/P0/system settings не змінювалися. Ручне підтвердження працездатності
звуків отримано; повний сценарний аудит усіх фізичних кнопок не заявлено.
