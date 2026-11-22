# Інтерактивні елементи Carpe TV: перевірка P1/P2

2026-11-14. Перелік отримано читанням `MainActivity.java`, `UpdatesActivity.java`,
`SleepTimerDialog.java`, `SystemSetupActivity.java`, `ScreensaverActivity.java`,
`Screensaver.java`, `WallpaperCatalog.java` і всіх XML у `res/layout`.
Шляхи Java нижче відносні до `app/src/main/java/agency/carpediem/tv/prototype/`;
XML — до `app/src/main/res/layout/`. Виклики й IDs точні; номери рядків
навмисно не фіксуються, бо паралельно оптимізується бібліотека.

Це **code-derived очікування**, не нові runtime PASS. Історичні докази в
[NAVIGATION_CHECK](NAVIGATION_CHECK.md), [PERFORMANCE_CHECK](PERFORMANCE_CHECK.md),
[VALIDATION](VALIDATION.md) не переносяться автоматично на нові зміни.
Широкі критерії та gates — у [PUBLICATION](PUBLICATION.md).

Умови майбутніх запусків: лише isolated app, збереження попередньої APK і
відновлення після тесту; без зміни системних налаштувань/служб стіка.
Усі build/test caches та artifacts — **на підключеному зовнішньому SSD**.
На момент передачі SSD не mounted; `/Volumes/PROJECTS` — SMB, не SSD.
До визначення SSD жодна команда build/device test тут не пропонується до запуску.

Позначення маршруту: **Л** — можна тестувати локально в isolated app;
**В** — зовнішній екран/мережа: перевіряти без зміни налаштувань/надсилання;
**С** — системна дія, не активувати зараз. P0 починається лише після
окремого явного дозволу власника. Генеровані кнопки без сталого ID
ідентифікуються категорією, string key і методом створення.

## Навігація, Home, бібліотека

| Entry point / control | Очікувана дія з коду | Безпечний маршрут і що довести |
| --- | --- | --- |
| `activity_launcher.xml`: `nav_home`, `nav_apps`, `nav_settings`; `bindNavigation` | Focus відкриває сторінку; OK відкриває й переводить у content; Right входить у content. Modal guard пригнічує rail focus navigation. | Л: Up/Down в rail, Right/Left, OK, rapid reversal; сторінка/selected rail відповідають фокусу, hidden page не отримує focus. |
| `home_header.xml`, `page_apps.xml`, `page_settings.xml`: `{home,apps,settings}_power` → `showSleepNow` | Спочатку Home; якщо немає SystemAccess — пояснення toast; інакше confirm «Sleep now / Cancel». | Л лише unavailable state або Cancel/Back confirm. **С: позитивне Sleep now не натискати**, воно викликає `SystemAccess.sleepDevice`. |
| Ті самі три headers: `*_wifi` → `openWifi` | Відкриває ACTION_WIFI_SETTINGS; при RuntimeException показує network_unavailable toast. | В: source/intent і повернення без зміни мережі; не перемикати Wi-Fi для offline тесту. |
| `*_bluetooth` → `openBluetooth` | Спочатку ACTION_BLUETOOTH_SETTINGS, потім відомий системний TV accessories slice, за готового доступу privileged openBluetoothSettings, далі generic Settings/toast. | В: лише підтверджений звичайний intent route і повернення без pairing/unpair/toggle. Privileged fallback зараз С; тестувати через fake/intent inspection, без виклику системної служби. |
| `*_timer` → `SleepTimer.show` | Відкриває timer sheet; countdown замінює icon, коли timer активний. | Л: відкрити/закрити, focus/bounds unavailable state; timer execution — С, див. окремий розділ. |
| Home/Apps `item_app.xml`: root tile, tag package; `renderList` | OK → `openApp`; long OK → `showFavoriteMenu`; artwork/label/focus outline — частини тієї самої tile. | Л/В: focus по всіх колонках/краях, rapid movement, long OK menu; OK запускає звичайний installed app без зміни Home role. P1 охоплює launch intent/fallback, missing-app toast, launch failure, app-only pause/resume/focus через Back або явний запуск isolated Carpe TV. Physical Home, OEM routing і системний return/reboot — відкладений P0. |
| `page_apps.xml`: `apps_search` | OK просить IME; TextWatcher змінює query й результати; grid фільтрується, focus/search routes перелінковуються. | Л: match/no-match/clear, кирилиця, довгі/rapid edits, Down/Up, Back спочатку IME, збереження query при сторінках/recreation. Runtime latency і rebind metrics ще потрібні. |
| Home/Apps load-error `apps_retry` (generated ID) | Скидає failure/ready, renderPage, worker `refreshApps`. | Л: інжектувати catalog failure в isolated state; OK дає loading → результат або повторну помилку; focus не губиться. Не симулювати через зміну системних пакетів. |
| Empty/placeholder messages, clocks, greeting, brand mark, counts | Не мають click handler; loading tiles не повинні бути focusable. | Л: в порожньому grid немає фантомних tile; Down із search не тікає на hidden content. Не додавати тести «натиснути clock». |
| Activity Back (`onBackPressed`), page focus/scroll restoration | Закриває IME/повертає із detail до category, category до Home; move mode обробляє Back окремо. | Л: кожна глибина, повторний Back, last tile/scroll; відокремити Android/system Home key від внутрішньої Home-сторінки. |

## Меню програми та reorder

`MainActivity.showFavoriteMenu`, `dialog_app_actions.xml`: `action_title/icon`
неінтерактивні; кнопки створюються в `action_rows`. Dismiss повертає focus до anchor.

| Умова / дія | Очікування | Безпечна перевірка / evidence gap |
| --- | --- | --- |
| Не favorite: `add_favorite` | Додає package у favorite_order; cached Home invalidated. | Л: додати з library, повернутись Home, persistence; після тесту повернути початковий список. |
| Favorite: `remove_favorite` | Видаляє з favorites; на Home перебудовує сторінку. | Л: видалити середній/останній/єдиний, коректний focus fallback і empty Home; відновити стан. |
| Favorite і кількість >1: `move_app` | Після dismiss запускає `beginMove`; вибрана tile activated і label з ‹ ›. | Л: reorder state visible, modal не лишається; з одним favorite кнопки Move немає. |
| Move: Left/Right ±1, Up/Down ±5 | `moveSelected` міняє order/positions; поза межами нічого не міняє; зберігає focus. | Л: кожна межа/неповний рядок/rapid keys, no detach focus escape. `ReorderProbe` існує, потрібний актуальний run. |
| Move: OK/Enter key-up | Commit лише після armed key-down; `finishMove(true)` зберігає order. | Л: opening long press не має випадково завершити move; persistence після recreation. |
| Move: Back key-up | `finishMove(false)` відновлює попередній order. | Л: cancel після кількох рухів і rapid animation; жодна provisional перестановка не збережена. |
| `app_info` | Android application-details для package, fallback toast. | В: лише показ/Back без Force stop/Clear/Uninstall/permissions. |
| `cancel` або Back/dismiss меню | Не змінює favorites, повертає focus/ring anchor якщо attached. | Л: усі dismiss paths, touch→D-pad; видалений anchor має безпечний fallback (перевірити). |

## Налаштування: категорії та Appearance

Категорії створює `renderSettings`: `appearance`, `screensaver`, `favorites`,
`audit`, `device`, `about`. Focus і click вибирають категорію; Right входить
у detail; Left/Back повертають до вибраної категорії. `settingsEntryFocus`
для Appearance вибирає поточні шпалери, для інших — перший control.
`settings_power` є Up-ціллю першого detail; Down має повертати туди ж.

| Control / entry point | Expected action | Безпечний маршрут / gap |
| --- | --- | --- |
| `appearance_controls.xml`: `wallpaper_choices` → `wallpaper_alpine`, `wallpaper_aurora`, `wallpaper_como`, `wallpaper_blue`, `wallpaper_plain` | Зберігає `wallpaper`/rotation changed-at, selected marker і label; async decode/background update; gallery entry scroll до selected. | Л: усі 5, швидкі зміни, restart, selected не перша card; screenshots/пік пам’яті на фінальних changes. |
| `dynamic_wallpapers` | App-private rotation enabled/changed-at, reschedule. | Л: toggle, interval advancement через isolated clock/state, pause/recreate; відновити preference. |
| `wallpaper_dim` SeekBar + `dim_value` | Left/Right або touch змінюють app-private dim, label `%`, updateBackground. | Л: min/max/rapid, restart; dim_value — output, не окрема кнопка. |
| `dim_reset` | Повертає `RECOMMENDED_DIM`, SeekBar і background. | Л: після крайніх значень, duplicate OK, persistence. |
| `animations` | Зберігає app-only animations і configureStateFade; motion додатково враховує system animator setting read-only. | Л: on/off, nav/dialog/reorder, відновити; не міняти system animation scale. |
| `language_choice` | `showSettingChoices`: System default (`""`), Українська (`uk`), English (`en`); зміна recreate; focus повертається до selector. | Л: усі 3, поточна option без зайвого recreate, Cancel/Back, довгі labels; не змінювати глобальну мову. |

## Налаштування: Screensaver і Favorites

| Control / string key | Expected action | Безпечний маршрут / gap |
| --- | --- | --- |
| `saver_timeout` (`saver_after`) | Choice: Off, 1, 3, 5 recommended, 10, 15, 30 хв; save/rebuild/focus timeout/reschedule. | Л: усі choices, selected badge/scroll/Cancel, app-private idle behavior; без системного sleep. |
| `saver_provider` (`saver_source`) | Built-in або Aerial Views; якщо Aerial недоступний — toast і без зміни. | Л: builtin, unavailable fallback. Aerial external launch — В і тільки без зміни чужих settings. |
| `saver_online` | Toggle `saverOnline`, refresh usage text. | Л: app-private toggle; preview з online off не вантажить нові медіа, працює bundled/cache. |
| `saver_preview` | `Screensaver.launch`: external Aerial якщо selected/available, інакше builtin `ScreensaverActivity` із fallback toast. | Л: force builtin in isolated app, preview/Back, focus return; download artifacts/cache тільки SSD, не вмикати online без контролю бюджету. |
| `saver_system` | Android `ACTION_DREAM_SETTINGS`, fallback Settings. | В: source/intent/показ і Back; **С: не змінювати system screensaver**. |
| `saver_reserve` | Choice 1024 MiB recommended або 512 MiB; save/rebuild/focus. | Л: isolated preference+text; low-space через injected cache/free-space provider, не заповнювати диск. |
| `saver_clear` | Worker `SaverCache.clear`, update usage; очищає app-private saver namespace. | Л: лише спеціально створений isolated test cache; перевірити pinned/current protection і інші файли; не чистити персональний cache. |
| `saver_credits` | Worker збирає cached titles/author/source/license; AlertDialog або empty message, Close. | Л: empty і populated, long scroll, focus/Back/Close, modal boundary; історично менш покритий за license. |
| `saver_cache_status`, help/usage text | Read-only cache/free-space/waiting status. | Л: correct low-space/offline state, async callback після виходу не пише у чужий detail. |
| Favorites category: `open_library` | `openPage("apps")`. | Л: один OK, library query/scroll semantics; вся редакція favorites відбувається через tile menu. |

## Налаштування: Audit та Device — системні межі

Read-only status/focus і Cancel можна перевіряти. **Не виконувати жодну
enable/disable/restore, Home chooser, grant, pairing, service start або sleep
дію в поточній фазі**, навіть якщо історичний probe її вміє.

| Control / entry point | Expected action з коду | Дозволена зараз межа |
| --- | --- | --- |
| Audit `audit_access_action` | `SystemAccess.request`. | С: не натискати; source review/видимість пояснення. |
| Audit rows `audit:<package>` (`audit_app_row.xml`), `AppAudit.PACKAGES` | Xiaomi analytics/statistics/TV Plus/Google TV; status read-only, click → package actions. | Л: read-only state/row focus; menu open→Cancel за відсутності mutation. Exact packages бере `AppAudit.PACKAGES`, не довільний список. |
| Telemetry actions: `system_disable`/`system_enable`, `system_app_settings` | Перший → `confirmSystemAction(disable/restore,pkg)`; другий → Android app details. | С: mutation forbidden; В details лише показ/Back. Missing app → unavailable toast. |
| Google TV `showStockLauncherActions`: `stock_action_primary` | Якщо enabled → Choose Home, якщо disabled → stock restore confirmation. | С: не активувати; menu warning/focus/Cancel можна оцінити без змін. |
| Stock menu `system_app_settings`, `cancel` | Android details або dismiss. | В без mutation; Л Cancel/Back. |
| `confirmSystemAction`: Cancel / `system_continue` | Positive викликає `runSystemAction(stock/disable/restore,pkg)`. | Л Cancel/Back only; С Continue. Фінальний confirm текст не означає authorization. |
| Audit `audit_refresh` | Worker оновлює memory/package/access statuses. | Л read-only refresh, stale callbacks/lifecycle; жодної зміни package state. |
| Audit `audit_all_apps` | Manage applications Settings, fallback generic Settings. | В показ/Back, не чистити/зупиняти/видаляти apps. |
| Audit `choose_home`, conditional `stock_restore` | Home chooser або stock restore confirmation. | С: не активувати. |
| Device specs / `home_status`, `home_remote_warning`, `shizuku_status` | Read-only model/Android/API/output/UI/version/Home/access status. | Л: значення, warning visible коли потрібно, bounds/long model; не плутати physical mode з UI size. |
| Device `make_home`, `choose_home` (коли HomeEntry існує) | `HomeChoice.request/chooseOther`. | С: P0, не активувати. |
| Device `android_settings` | Generic Android Settings. | В лише показ/Back, без системних змін. |
| Device `setup_title` | Запускає `SystemSetupActivity`. | С: майстер лише source/UI review без запуску pairing/service; Close/Back безпечні. |
| Device `shizuku_open`, `shizuku_grant` | Відкрити manager / request system access. | С: жодного grant/start/setup зараз; missing-manager state можна інжектувати в isolated тесті. |

## About, Feedback, licenses

Рішення власника 2026-11-14: Feedback залишається **лише через GitHub**;
альтернативний канал не потрібний у scope. Public/privacy copy review
(Issue публічний, потрібен GitHub account, не додавати private data) і fit
залишаються потрібними; вибір каналу не закриває QR/browser/phone gates.

| Control / entry point | Expected action | Безпечний маршрут / gap |
| --- | --- | --- |
| About brand/version/company | Неінтерактивні назва, runtime appVersion, CarpeDiem Agency. | Л: точність, hierarchy, translations/bounds; жодного dead-focus stop. |
| `report_problem` → `showFeedback` | Modal feedback; initial Bug focus; background rail guarded; dismiss після window focus повертає Feedback. | Л: D-pad entry, touch→D-pad, repeated open/Back, 720p/1080p, modal bounds. |
| Feedback `feedback_bug`, `feedback_idea`, `feedback_other` | Selected topic і QR URL `https://github.com/carpe-diem-agency/carpe-tv-launcher/issues/new?template={bug,idea,other}.yml`; bitmap cached per topic. | Л: три QR decode після фінального layout, selected state description; fallback URL при WriterException. Код зараз **не prefill-ить технічний context**, templates мають власні поля. |
| Feedback `open_issue` | Dismiss і external browser з поточним issue URL. | В: exact form/sign-in handoff без submit; публічний Issue не створювати без прямої вказівки. |
| Feedback `close` / Back | Dismiss та pendingModalFocus restoration. | Л: наступні Up→header і Down→Feedback; Settings не має перемикатись у Home. |
| Feedback QR і privacy text | Не click controls; account-required/privacy warning, scan телефоном. | В: phone scan потрібний; автоматичний decode не доводить реальне phone usability. |
| About `source_code` | External repository URL. | В: exact URL/browser unavailable fallback, Back focus. |
| About `developer_site` | External `https://carpediem.agency/`. | В: source URL/open/Back; окремого website QR у цьому handler немає. |
| About `updates` | `UpdatesActivity`. | Л: entry/Back/focus; updater actions нижче. |
| `code_license`, `third_party_notices`, `font_license` | `showAssetNotice`: Apache-2.0.txt, readable THIRD-PARTY-NOTICES.md, Montserrat-OFL.txt; scrollable body + Close. | Л: усі 3, long scroll, missing-asset message, D-pad trapped, Close/Back focus; manual license touch→D-pad regression залишається потрібним. |
| Shared `showSettingChoices` | Scrollable options, selected check badge, selected initial focus, Cancel; choose dismiss перед callback. | Л: language/saver delay/provider/reserve та audit menus; first/last boundary, scrolling/Cancel/Back. Системні callbacks не викликати. |
| Welcome `welcome_explore` | Позначає welcome_seen і dismiss. | Л: fresh isolated app, initial focus Explore, Back/reopen/cold start semantics. |
| Welcome `make_home` | Dismiss і `HomeChoice.request`, не позначає welcome seen до успіху. | С: P0, не натискати; visible/bounds тільки. |

## Updater (`UpdatesActivity`, `UpdateManager`)

Усі artifact/feed/download fixtures і build caches для майбутніх тестів мають
бути на SSD. Не використовувати production feed як неконтрольований
інсталяційний тест. Debug `test_feed` дозволяється кодом лише debug variant.

| Control / ID | Expected action / visibility | Безпечний маршрут / gap |
| --- | --- | --- |
| `back` (generated Button) | `finish`, app Back теж закриває Activity. | Л: entry із About, Back/focus, foreground tick видаляється onPause. |
| `update_check` | `UpdateManager.check(feed)`; disabled під час busy і ready. | Л із локальним fixture feed після SSD: empty/current/available/error/malformed metadata, repeated OK. |
| `update_download` | `UpdateManager.download`; visible лише available. | Л isolated fixture: progress, hash/size/package/version/signature rejection; bounded network/disk use, жодного production APK. |
| `update_cancel` | `UpdateManager.cancel`; visible лише downloading. | Л: cancel/retry/resume app, orphan partial/state cleanup; Back не означає cancel. |
| `update_install` | `UpdateManager.install`; visible ready; label Install або Allow install залежно від canRequestPackageInstalls. | Л source/ready UI і error fixtures. **С: не надавати Android install permission**; actual APK install лише в явно погодженій isolated процедурі, не в broad control sweep. |
| `update_automatic` | Toggle app-private autoDownload; якщо ввімкнути в available — одразу download. | Л тільки controlled local feed: on/off persistence і side effect; не toggle на live available без download budget. |
| Status/details/progress/result icon | Read-only, state-dependent text, icon current/success; render переносить focus із hidden/disabled action на доступну. | Л: checking/preparing/downloading/verifying/installing/ready/available/idle/error, long release notes, focus during transitions. |
| Android PackageInstaller / install-permission UI | Platform-owned confirmation, не auto-install; receivers завершують state. | С/окремий release gate: без permissions change у цьому sweep. Історичний debug local update не доводить production GitHub/signing flow. |

## Timer (`SleepTimerDialog`) та System Setup

| Entry / control | Expected action | Дозволена межа |
| --- | --- | --- |
| Timer unavailable access: `sleep_setup` | Dismiss, `SystemAccess.request`. | С: не натискати; hint/bounds/initial focus/Close перевіряти. |
| Timer exact-alarm unavailable: `sleep_permission` | Dismiss, Android REQUEST_SCHEDULE_EXACT_ALARM. | С: не натискати/не надавати permission. |
| Timer ready: `15`, `30`, `60`, `90`, `120` | `SleepTimer.schedule(minutes)`, dismiss і indicator update. | С: не schedule-ити реальний timer; лише fake dependencies/UI fixture для click logic. |
| Timer `sleep_cancel` (якщо active), `sleep_close` | Cancel видаляє timer; Close лише dismiss; tick прибирається onDismiss. | Л Close/Back; не cancel-ити особистий timer. Cancel перевіряти тільки isolated fake timer. |
| SystemSetup `setup_pair` | `beginPairing`, local HTTP server + QR/phone wizard. | С: не відкривати pairing/session; read source та fixtures без системних змін. |
| SystemSetup `setup_open_settings` (conditional) | `openDeveloperSettings`. | С: не змінювати developer/wireless debugging settings. |
| SystemSetup `setup_start` | `startServiceOnDevice`. | С: не запускати service/ADB operation. |
| SystemSetup `shizuku_grant` | `SystemAccess.request`. | С: не grant. |
| SystemSetup `close`, Back | `finish`; lifecycle має закрити pairing resources. | Л тільки без pairing або fake session; startup/status/long text можна source review. |

## Built-in screensaver surface (`ScreensaverActivity`)

Немає кнопок/меню поверх медіа. На key **up** будь-якої не-volume клавіші
Activity завершується; key-down споживається, щоб OK не відкривав app позаду.
Volume up/down/mute передаються super без dismiss. Touch up теж dismiss.
Фото змінюється за таймером, відео — completion/error/timeout; credit text
не має click handler.

Безпечний Л-маршрут: isolated builtin + online off → preview → D-pad/OK/Back
окремими runs → Settings focus; touch-up; volume event через fake/test API
без зміни системної гучності. Перевірити pause/destroy cancel decode/transfer,
без звуку/audio focus, corrupted media fallback і відсутність click-through.
Не використовувати Home/power/sleep key для цього P1 тесту. Зовнішній
Aerial Views — окрема В-гілка, не замінює builtin evidence.

## Поточні evidence gaps і порядок виконання sweep

1. Inventory покриває всі explicit click/long-click/check/seek handlers у
   перелічених Activity та XML controls; при додаванні нового handler
   оновлювати рядок. Динамічні package tiles і всі choice options тестуються
   як параметризовані controls, не лише один представник.
2. Після SSD/SDK/device readiness: спочатку Л routes (search/state/settings,
   dialogs/favorites/reorder), далі controlled error/offline fixtures, updater
   state UI і builtin saver; жоден **С** callback не викликається автоматично.
3. Історичні probes не закривають весь inventory. Особливо бракує повного
   Cancel/Back/focus для всіх modal, saver credits, favorites edge cases,
   updater hidden-action focus, реальної IME, phone QR і projector readability.
4. Для кожного run фіксувати commit/variant/package, safe state setup і
   restoration, expected/actual, viewport/device, PASS/FAIL/не виконано та
   причину. Наявний code handler не є доказом працездатності.
5. Фізичний пульт, phone scan, projector/4K/інший TV лишаються фактичними
   evidence gates. Build/lint/policy PASS не закривають runtime/UX вимоги.
6. Знайдені відхилення стають конкретними дефектами з regression route;
   після виправлення потрібні reviewer і user-simulator checks. Після P1/P2
   координатор запитує дозвіл на P0; не активує відкладені controls самостійно.

### Підтверджені source findings, ще без runtime результату

- **Saver cache recovery:** `SaverCache` прибирає orphan media лише в
  конструкторі. Якщо `.json` видалений/зіпсований у живому процесі,
  `entries()` не повертає відповідний `.media`, тому `clear()` та eviction
  його не бачать; `SaverSync` може пропустити повторне завантаження через
  наявний media file. Потрібні фікс reconciliation і deterministic cache
  probe: missing/corrupt metadata у тому самому cache instance → clear/evict/
  повторний sync, із перевіркою pinned protection і byte/file limits.
- **Wallpaper A→B→A race:** координатор підтвердив source race із generation
  invalidation при поверненні до вже loaded A, поки B декодується. Android
  агент готує generation fix і probe. Приймання: після контрольованого
  завершення B фактичний bitmap/key/selected marker лишаються A; також
  швидкий A→B→C, destroy і stale decode cleanup. Цей запис не є runtime PASS.
