# P1 controls і modals: актуальний acceptance checklist

2026-11-14. Source-derived із `MainActivity` та поточних probes; звіти
попередніх checkpoint у [PUBLICATION](PUBLICATION.md).
Це стислий облік UX6/QA1 після реорганізації, не додатковий runtime PASS.
Історичний checkpoint 2026-11-14 мав **9 probes PASS** (перелік унизу).
Таблиці нижче відображають актуальні докази; попередній FAIL/fixture стан
збережено окремо як історію, а не поточну незавершену збірку.
Погоджені SETTINGS_STRUCTURE/GitHub-only/sounds/Device bars залишаються scope.
Продовжувати від актуального git і відкритих пунктів матриці; infrastructure
та outputs використовують актуальне середовище за AGENTS.md.
P0/system callbacks не активувати, навіть якщо sheet відкривається безпечно.

## Історичний попередній checkpoint modal/grid (superseded final run)

- `modal-grid` build **SUCCESS 3 хв 11 с**; початковий library run FAIL
  без достатньої діагностики. Diagnostic test build **SUCCESS 34 с**
  показав count7/column0, поки fixture штучно фокусував tile між списками.
  Це нереалістичний typing route: fixture виправлено, щоб перед кожним
  render фокусувати search. Не оголошувати цей результат product bug.
- `modal-only`: D-pad credits/language **PASS**; injected-touch credits
  **PASS**, але після реального touch-входу в language і D-pad Up
  змінилася Settings сторінка позаду owned modal — **FAIL, concrete
  product focus regression**. Це окремий доведений дефект, на відміну
  від попереднього INPUT_UNVERIFIED.
- Android fix `showSettingChoices`: modal guard і exact origin restoration
  після choose/dismiss тоді було підготовлено; на той момент нова build
  ще тривала без PASS. Пізніший final checkpoint нижче підтвердив fix.
- Cleanup усіх трьох run **PASS**, Home/services baseline preserved.
  SSD log namespaces: `modal-grid`, `grid-diagnostic`, `modal-only`;
  наступний run `modal-grid-fix`. Цей checkpoint не закриває UX6/QA1.

Для кожного modal: відкрити з реального anchor, перевірити initial focus,
Up/Down/Left/Right containment, Close/Cancel/Back, exact anchor restoration,
повторне відкриття. D-pad і injected touch — **окремі routes/results**;
fixture без verified input не дає продуктового PASS/FAIL. Physical remote
також окремий доказ.

| Entry / callback | Безпечна дія й очікування | Наявне покриття / залишок |
| --- | --- | --- |
| Appearance `language_choice` → `showSettingChoices` | Показ System/uk/en, selected badge; Cancel/Back без зміни мови. | Languagestate, modalfocus і modaltouch PASS; окремі D-pad/touch entry/Up/Back routes. Повна recreate/всі choices regression — наступний gate. |
| Saver timeout/provider → `showSettingChoices` | Список, selected, Cancel; зміна лише isolated app preferences із restoration. Aerial missing fallback. | 0.2.45 Xiaomi: D-pad пройшов сім варіантів часу, зберіг «Вимкнено» і 3 хв, перевірив позначки після повторного відкриття, Cancel/Back і фокус. Вибір доступного Aerial Views зберіг джерело. Cancel у джерелах повернув фокус до тієї самої кнопки. «Переглянути зараз» відкрила Aerial Views, а Back повернув фокус до тієї самої кнопки. Таймаут 1 хв відкрив Aerial Views через спостережені 60,305 с після останнього D-pad, а Back повернув фокус на джерело. Вибір недоступного провайдера в цьому меню лишається окремою перевіркою. |
| Authors `authors_media_credits` → `showSaverCredits` → `showSettingsPanel` | Empty/populated credits, scroll, Close/Back → той самий Authors anchor. | Нові Authors credits D-pad і actual injected touch routes PASS; старий INPUT_UNVERIFIED окремий історичний факт. Populated/long content/recreation — pending. |
| Authors code/third-party/font → `showAssetNotice` | Три texts, довгий scroll, Close/Back, modal guard; font title Montserrat · SIL OFL. | Final author_notices PASS: три assets/actual D-pad/long scroll/Back/reopen/Close exact anchor. 0.2.50 Xiaomi: при перестворенні Activity з відкритими сповіщеннями про сторонні компоненти старий діалог від’єднався, а фокус і повторне відкриття працювали. Інші варіанти перестворення з відкритим діалогом лишаються окремими перевірками. |
| Device diagnostics → `showDiagnosticReport` → `showSettingsPanel` | 7-line raw-text QR + explicit text-not-link hint; окрема Report Bug дія → showFeedback default Bug; Close/Back → diagnostics anchor. | 0.2.49 Xiaomi: фактичний QR із сімома рядками, чотири напрямки в діалозі, Close/Back, повторне відкриття, Bug QR з тими самими сімома рядками й повернення до точної кнопки. Дотик, фізичний пульт і відтворення Activity з відкритим діалогом залишаються окремими перевірками. |
| Device Home → `showHomeActions` | Показ status/warning/actions, scroll, Close/Back. | Settingsstructure presentation checkpoint; exhaustive modal containment pending. **Не make/choose/restore Home.** |
| Device Additional → `showSystemActions` | Показ Shizuku status/actions, Close/Back. | Presentation covered locally; exhaustive routes pending. **Не setup/open/grant/start callbacks.** |
| About Feedback → `showFeedback` | 3 topics і screen QR, Close/Back → Feedback; Up→header/Down→anchor. | Feedback all3 QR+diagnostics URI+Back PASS. Owner phone підтвердив GitHub destination/sign-in prompt; form device prefill ще pending. Touch/modal recovery окремі gates; Open on TV без submit лише за безпечного маршруту. |
| App tile long OK → `showFavoriteMenu` | Add/remove favorite, Move conditional, app details, Cancel; dismiss anchor/ring. | 0.2.45 Xiaomi: реальні Add/Move/Remove після довгого OK, збереження порядку після відтворення Activity, видалення останньої плитки та фокус на «Головній». Cancel, App details, дотик і фізичний пульт поза цим прогоном. |
| Move mode → `dispatchKeyEvent` | Arrows ±1/±5, OK commit, Back rollback; неповний рядок/rapid animation. | 0.2.45 Xiaomi: D-pad Left/Right на межах, Back rollback, OK commit, Down до єдиної плитки короткого ряду після commit. Серії швидких переміщень і вертикальні кроки у самому режимі лишаються окремими перевірками. |
| Welcome → `showWelcome` | Explore closes/marks seen; initial focus Explore, bounds. | First-run історично; current dismiss/reopen pending. **Make Home виключено.** |
| Global Power → `showSleepNow` | Unavailable toast або confirm→Cancel/Back; content Home перед confirm. | Історичний coverage, current cancel-only sweep pending. **Positive Sleep now виключено.** |
| Global Timer → `SleepTimerDialog` | Unavailable explanation, Close/Back; fake ready fixture без системного виконання. | Історичний fit; current pending. **Не grant exact alarm/SystemAccess, schedule/cancel особистого таймера.** |
| Audit rows → `showAuditActions` | Дві telemetry choice sheets або TV+ Android details; Google TV own sheet; лише Cancel/Back/read-only details. | Appaudit read-only checkpoint PASS; усі modal boundaries pending. **Disable/restore/stock/Home дії виключені.** |
| HomeChoice fallback / `confirmSystemAction` | Source/fixture inspection або Cancel, якщо safe fixture показує dialog. | Наявність коду не є P0 runtime evidence; **Continue/chooser не запускати.** |

## Controls без modal

| Область | Прийняття | Evidence gap |
| --- | --- | --- |
| Sidebar/headers/content | Rail focus→page, Right/Left, header round trips, відсутність hidden-page focus. Wi-Fi/Bluetooth лише ordinary intent/read-only без системних fallback operations. | Library/settingsfocus PASS не дорівнює всім external header routes. |
| App grid/search/Retry | OK launch normal app, missing/error feedback; match/no-match/clear, search→Retry, query/scroll/recreate; Down до nearest actual tile неповного останнього рядка, self лише у фактично останньому рядку. | Reuse/Retry checkpoint PASS; Down clamp counts6/7/9/10/11 PASS; libraryime show/ASCII2/filter/Back PASS. Soft composition/Done/Cyrillic/physical remote/input latency pending. |
| Оформлення/Інтерфейс/Звуки | 5 wallpapers, auto30, dim/restore60, animation, language; sound toggle/volume70/preview gates. | Wallpapers/uisounds PASS; settingsstructure/clamp/modal routes PASS у межах probes; решта фізичної оцінки відкрита. |
| Saver/cache | Timeout/source/online, preview, downloaded-MB, free cache amount/clear, automatic reserve. | Cachepolicy mock fixtures PASS; actual media decode corruption/I/O failure і повний lifecycle soak pending. |
| Audit refresh | Active-only5s, immediate entry/resume, stop category/page/pause/destroy, coalescing. | Settingsstructure PASS; bounded proof, не години background stability. |
| Device/Authors/About | RAM/storage inline, read-only diagnostics, Authors Back/notice links, developer-site, UpdatesActivity. | Device visual owner-approved; решта owner walkthrough pending. External links/forms не перевірені одним QR decode. |
| Updater | Check/download/cancel/ready/permission label, auto-download toggle, hidden/disabled action focus, Back. | Історичний isolated update; поточний full state/error sweep pending. Реальні permissions/install лише окрема safe procedure. |

Після поточного run додати exact probe results, commit/dirty baseline,
package/device/viewport і cleanup evidence до матриці. Не переносити PASS
на неперевірені рядки цього checklist і не змінювати Home/services для
отримання легшого результату.

### Фінальний modal/grid checkpoint — 2026-11-14

Final build **SUCCESS за 1 хв 11 с**, lint **0 errors / 72 warnings**.
Усі 8 probes **PASS**: `modaltouch`, `modalfocus`, `libraryfocus`,
`screensaver`, `languagestate`, `settingsstructure`, `uisounds`, `settingsfocus`.
Логи й summary на SSD у namespace `modal-grid-final-20261114` під
`/Volumes/CarpeTVBuild/logs/`. Cleanup **PASS**, Home holder і accessibility
services baseline незмінні; P0 не запускався. Source ще uncommitted.

Підтверджений actual-touch language → Up focus escape виправлено guard
і exact-origin restoration у choice sheet та повторно перевірено новим
verified-input route. Старий історичний INPUT_UNVERIFIED лишається окремим
fixture фактом, не переписується новим PASS. D-pad і touch routes credits/
language тепер мають окремі PASS у цьому checkpoint; це не всі modals UX6.

Grid Down clamp до nearest actual tile коротшого останнього рядка
перевірено реальними injected D-pad подіями для **6/7/9/10/11 tiles**, PASS.
Self-route зберігається лише у фактично останньому рядку. Це не вимір
реальної IME latency чи фізичного пульта.

На момент 8-probe checkpoint `LibraryImeProbe`/dispatch уже додано, але
IME build ще тривав; наступний окремий checkpoint нижче підтвердив PASS.
Ці 8 PASS не закривають broad Goal, усі control/dialog cases, phone/forms,
soak або інші відкриті gates матриці.

### Окремий IME checkpoint — 2026-11-14

Test APK build **SUCCESS за 41 с**, `libraryime` **PASS**. Реальна
послідовність: D-pad OK → WindowInsets підтверджує visible IME → injected
hardware ASCII (2 символи назви відомого app) → exact filtered package sets
відповідають baseline → Back приховує IME, query/search focus і Apps
зберігаються → другий Back повертає Home-сторінку. Це внутрішній page route,
не системний Home/P0.

Лог `/Volumes/CarpeTVBuild/logs/probe-libraryime-library-ime-20261114.log`,
summary у SSD namespace `library-ime-20261114`. Cleanup **PASS**, Home/
services baseline незмінні. Final policy/diff-check **PASS**, source ще
uncommitted. Разом із попереднім checkpoint — **9 passing probes**,
але це не повний acceptance усіх controls або broad Goal.

Межі: soft keyboard composition, IME Done, кирилиця й фізичний пульт
не перевірені; input latency **не вимірювалася**. Попереднє «IME PASS ще
немає» описувало стан до цього окремого run; поточний PASS обмежений
переліченим show/hardware-ASCII/filter/Back сценарієм.

## Authors/licenses/recreation: поточні межі після нового checkpoint

- Authors: усі три notice texts і credits (empty/populated/довгі), scrolling,
  Close/Back, повернення до exact Authors anchor; не переносити credits PASS
  на неперевірені code/third-party/font routes.
- Recreation: Authors detail/modal state, language choice cancel/choose,
  focus fallback після перебудови; окремо app process-death/cold launch.
- Для нових cases потрібні verified input і новий runtime результат;
  D-pad трійка notices і Authors framework recreation тепер PASS (див. checkpoint нижче).
  Process death/modal-open recreation/touch licenses/physical remote лишаються
  **pending**; перевірки не доводять повний UX6/QA1/Goal.

### Authors notices і recreation checkpoint — 2026-11-14

Початковий test build **SUCCESS 27 с**, namespace `authors-20261114`;
modal run FAIL «Notice scroll view missing»: fixture вибрав перший
прихований native placeholder. Виправлено вибір visible body, додано
positive asset markers і bounded scroll wait. Це fixture correction,
**production code цього checkpoint не змінювався**.

Фінальний test build **SUCCESS 30 с**, namespace `authors-fixture-20261114`;
`modalfocus` **PASS**, включно з `author_notices`: усі 3 code/third-party/
font notices відкриті actual D-pad, перевірені asset markers і long scroll,
Back/reopen/Close повертають exact Authors anchor; credits/language routes
також PASS. Окремий `authorsstate` **PASS**, namespace
`authors-state-20261114`: child-context/focus після framework recreation,
Back повертає About entry. Логи/summary — `/Volumes/CarpeTVBuild/logs/`
у відповідних namespaces.

Cleanup усіх runs **PASS**, Home/services baseline preserved;
policy/diff-check **PASS**, source ще uncommitted. Це не підтверджує
process death, recreation **з відкритою modal**, touch license routes або
фізичний пульт — ці gates залишаються відкритими. Не поширювати Authors
PASS на всі modals чи broad Goal.

Власником погоджені три уточнення перенесено координатором до canonical
handoff goal file та AGENTS: approved settings/GitHub-only/sounds/Device
bars scope, продовження від git+відкритих matrix items із розділенням
доказів, infrastructure/outputs тільки SSD/APFS. Objective text системного
Goal engine доступний лише для читання (status можна змінювати окремим
інструментом); це обмеження не означає завершення Goal.

## Source-only inventory reconciliation — 2026-11-15

Звірено current MainActivity/UpdatesActivity/layouts із прийнятою структурою.
Named routes, які не можна губити в broad «Device/About/Updater» рядках:

- `device_diagnostics` raw text + hint → Report Bug → `showFeedback` Bug
  default; це окрема дія від raw QR, не URL-підміна canonical payload.
- About developer text є clickable website link; Authors child має окремий
  `authors_back` → About exact entry, source-code external link, 3 notices
  і media credits. Нове verified Authors process death підтвердило один
  child case; interrupted modal case cleanup досі не proven.
- Device `android_settings` залишається окремою external дією поза Home/
  Additional sheets; лише safe show/Back, без mutation. Home/Additional
  presentation не дозволяє запускати їхні системні callbacks.
- Sound `ui_sounds_enabled`, `ui_sounds_volume` і `ui_sounds_preview`: off/0% gate
  також для preview; focus перехід між switch/slider/preview + touch→D-pad
  є окремими routes від простого uisounds API counter PASS.
- Updater explicit IDs `update_check`, `update_download`, `update_install`,
  `update_cancel`, `update_automatic`, окрема Back button: перевіряти
  available→download→cancel і ready→permission/install label transitions,
  hidden/disabled focus fallback; automatic toggle в available одразу
  запускає download. Використовувати controlled feed без permission change.
- Новий shared `prepareTouchActivationFocus` і explicit nav/category links
  candidate24 успадковує з undeployed23. Main-button/category touch→D-pad
  маршрут, що не відкриває Home випадково, потребує regression по кожній
  family; один Authors host PASS не покриває всі generated actions.

Це source-derived coverage gaps, **не нові runtime PASS**. Останній
verified installed main22; candidate24 compiled, ADB targets0, interrupted
modal cleanup NOT PROVEN. Усі нові safe routes runtime pending, broad
UX6/QA1 checklist не завершений. Product source/build/device не змінювалися.

## 0.2.30 · джерело заставки

`modalfocus` підтвердив послідовність дій через ін’єктовані кнопки D-pad: відкрити «Джерело», пройти до видимого «Скасувати», натиснути OK і повернути фокус на джерело. Провайдер залишився попереднім. EN/UK, більший текст, розміри та scroll виміряно в локальній конфігурації програми. Фізичний пульт і зміна провайдера лишаються окремими сценаріями.
