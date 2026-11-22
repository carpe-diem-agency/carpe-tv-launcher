# Погоджена структура налаштувань Carpe TV

Погоджено власником 2026-11-14 після перегляду повної фінальної схеми.
**Уся наведена структура прийнята до реалізації**, це не wishlist і не
перелік непогоджених пропозицій. Документ задає цільову структуру P1;
наявність цього опису не означає, що зміни вже реалізовані або перевірені.
Поточне охоплення доказів — у [PUBLICATION](PUBLICATION.md).

Системна інтеграція P0 залишається на паузі. Групування наявних Home і
Shizuku callbacks в окремі sheets — лише зміна інформаційної архітектури
P1, без зміни їхньої системної логіки й без дозволу запускати системні дії.
Жодного merge, public push чи release цей документ не дозволяє.

## 1. Вигляд і звук

Один екран, три видимі групи. Додаткових підменю між ними не потрібно.

| Група | Елементи й погоджена поведінка |
| --- | --- |
| **Оформлення** | Галерея шпалер; типовий анімований синій фон, доки користувач не вибрав інший; автоматична зміна кожні 30 хв; затемнення фону, рекомендовано 60%; компактна дія відновлення рекомендованого затемнення поруч із цим налаштуванням. |
| **Інтерфейс** | Анімації (також керують рухом вибраного синього фону); мова застосунку. |
| **Звуки** | Увімкнення звуків; гучність, типово 70%; прослуховування погоджених navigation і click sounds. Mapping/gain і publication gate зберігаються за [UI_SOUNDS](UI_SOUNDS.md). |

English category label: **Appearance & sound**. Український label:
**Вигляд і звук**. Зміни залишаються app-only; системну гучність, мову,
роздільну здатність або animator scale не змінювати.

## 2. Заставка

Погоджений вміст:

- Джерело заставки: наявні built-in Carpe TV та Aerial Views choices.
- Час до заставки: наявні timeout choices.
- Дозавантаження фото й відео.
- Статус обсягу завантажених медіа в МБ.
- **Переглянути зараз**.
- **Звільнити місце · {X} МБ** — дія над app-private cache із показом
  відповідного поточного обсягу. Під час реалізації не втрачати protection
  активних/pinned media та bounded cache behavior.

Посилання **«Налаштування заставки Android» прибрати** з цього екрана.
Резерв вільного місця стає **автоматичною політикою**, ручного selector
1024/512 МБ більше немає. Це не дозвіл заповнювати диск або змінювати
чужі файли; автоматичну політику й фактичний free-space guard треба
перевірити в коді та tests, не вигадувати нові пороги в UI.
**Джерела та автори** перенести в About → **Автори й ліцензії**.

Заставка залишається foreground-функцією Carpe TV; жодного нового
керування системним screensaver/sleep або іншими застосунками не погоджено.

## 3. Обрані програми

Зберегти пояснення long OK для додавання/редагування обраного та дію
**Відкрити бібліотеку**. Існуючі favorite/reorder callbacks і контекст
бібліотеки не змінюються через цю реорганізацію.

## 4. Програми

Перейменувати категорію **«Програми й фон» → «Програми»**. Вміст:

1. Аналітика Xiaomi.
2. Статистика Xiaomi.
3. Xiaomi TV+.
4. Google TV.
5. Дія керування всіма програмами в Android.

Зберегти status/action semantics чотирьох наявних system-package rows.
Не додавати disable Google TV або довільне керування системними пакетами.
З цього екрана прибрати RAM/disk panel, system-access status/setup,
ручне **Оновити стан**, Home actions і stock-restore shortcut. Їх наявні
системні callbacks не запускаються в ході P1 acceptance.

Автооновлення read-only стану — кожні **5 с лише поки цей екран активний**:

- Негайний refresh при вході й resume, якщо активна категорія «Програми».
- Stop при зміні категорії, виході зі сторінки Settings, pause і destroy.
- Coalescing: не накопичувати конкурентні/queued scans; stale callbacks
  після виходу не повинні змінювати чужу панель.
- Повернення на екран відновлює один цикл, не дублює timers/listeners.

## 5. Пристрій

Один компактний екран, **RAM/storage не відкривають окремого підменю**.

### Діагностика

- Версія Carpe TV та version code.
- Модель пристрою.
- Android version і build.
- Фізичний відеовихід із частотою Гц.
- Окремо UI size у пікселях — не підміняти ним physical output.
- Стан developer mode як read-only факт.
- QR з raw diagnostic text для перенесення даних без набору пультом.

Діагностика та QR не повинні самі змінювати developer options чи інші
settings. Не додавати мережеві адреси, serial, pairing codes, tokens або
інші приватні ідентифікатори до QR/prefill без окремого обґрунтованого
рішення; погоджена діагностика перелічена вище.

### Канонічний DiagnosticReport

`DiagnosticReport.capture(...).text()` задає єдину raw-схему з **7 рядків**
для QR пристрою та `device` prefill у GitHub Issue. Raw payload англійською,
локалізований екранний `displayText()` відображає ті самі дозволені поля.
Схема поточного helper:

```text
Carpe TV: {versionName} ({versionCode}, {buildType})
Device: {manufacturer model}
Android: {release} (API {sdkInt})
Build: {Build.DISPLAY}
Output: {width}x{height} @ {rate} Hz
Interface: {widthPixels}x{heightPixels}
Developer options: {enabled|disabled|unknown}
```

`Output` береться з physical Display.Mode; якщо недоступний — `unknown`,
а не здогад із logical UI pixels. Manufacturer додається до model, лише
якщо model уже не починається з нього. String fields обмежені 96 символами,
control characters замінені пробілами; порожні значення — `unknown`.
Developer mode лише читається. Helper не читає serial, Android ID,
мережеві адреси чи акаунти. Source review цих обмежень не замінює
перевірку фактично згенерованого QR/URL.

### Ресурси на тому самому екрані

RAM і сховище: **використано / всього / вільно**, inline bars із зеленою,
жовтою й червоною індикацією навантаження. Показувати значення з одиницями,
щоб колір не був єдиним носієм інформації. Конкретні числові межі кольорів
мають бути послідовною реалізаційною політикою, а не новими пунктами меню.
Уточнення власника: прості заголовки **«ОЗП»** і **«Сховище»**, поруч
числа використано/всього та bar; не роздувати назви заголовків додатковими
словами. Поточний screenshot відповідає цьому рішенню.

### Наявні системні дії в окремих sheets

- **Home** — згрупувати наявні Home-related actions/status/warnings в одному
  sheet. Умови доступності й original callbacks зберегти; не стверджувати,
  що Home/OEM routing перевірені. Не виконувати choose/make/restore Home.
- **Додатково** — згрупувати наявні Shizuku/system-access actions в окремому
  sheet. Зберегти current callbacks і попередження; не запускати pairing,
  grant, service start або системні зміни.

Це P1 presentation/IA; реальне виконання й перевірка системної інтеграції
чекає окремого дозволу P0 наприкінці.

## 6. Про лаунчер

Статична шапка: назва, версія, розробник; назва розробника/сайт має
відкривати офіційний сайт. Три основні дії:

1. **Оновлення** — наявний updater flow.
2. **Зворотний зв’язок** — **лише GitHub** за рішенням власника; три теми:
   Помилка, Ідея, Інше. Додати погоджений diagnostic prefill до Issue form,
   зберегти local QR і browser handoff. Не надсилати Issue автоматично.
   Account requirement, public Issue і privacy пояснити в copy; перевірити
   encoding/довжину QR, fit і правильний template для кожної теми.
3. **Автори й ліцензії** — один entry point із наявними матеріалами:
   джерела/автори заставок, ліцензія застосунку, сторонні компоненти,
   ліцензія шрифту й посилання на GitHub.

Видалення окремих довгих списків із верхнього About не означає видалення
notice/license content. Довгі тексти залишаються scrollable та доступними
пультом; Close/Back повертає focus до правильного entry point.

## Приймання й облік доказів

- Звірити весь вміст із погодженою структурою, українські й англійські
  labels, видимість груп і conditional rows; кожна дія має визначений шлях.
- D-pad/OK/Back/Left, entry/return focus, recreation, modal bounds і
  no-match/loading/error behavior мають зберігатися після переміщень.
- Перевірити audit timer lifecycle/coalescing, diagnostic values/raw QR,
  RAM/storage bars, cache amount/free action та automatic reserve.
- Окремо перевірити feedback diagnostic prefill/QR/privacy та доступність
  перенесених credits/licenses; не створювати публічних Issues без запиту.
- Наявний WIP modal probe із **INPUT_UNVERIFIED** лишається окремим
  історичним fixture результатом. Він не доводить дефект production dialogs
  і не дозволяє пропустити нову перевірку grouped sheets.
- Build/lint/probes, screenshots та ручний owner review поточної структури
  мають бути новими фактичними доказами. Погоджена схема не є PASS.
- Усі build/test infrastructure/artifacts — лише SSD/APFS за `AGENTS.md`;
  isolated validation package, Home/accessibility baseline незмінні.

### Поточні докази реалізації (попередній runtime checkpoint)

Source звірено з прийнятою схемою та `DiagnosticReport` 7-line contract.
Label **«Оновлення»** залишає наявний entry point `UpdatesActivity`;
новий updater flow цією реорганізацією не додається. Локальна YAML
validation усіх трьох Issue templates та `scripts/check_repository.py`
**PASS** (результати координатора); це не Android build/device PASS.

`DiagnosticReport.issueUrl` дозволяє лише `bug.yml`, `idea.yml`, `other.yml`
та URL-encode-ить canonical report як query parameter `device`.
Усі три локальні forms мають поле `id: device`: required у Bug,
optional у Idea/Other. Координатор звірив механізм GitHub issue-form URL
prefill з офіційною документацією. **Оновлені live Idea/Other templates
ще не опубліковані**, тому локальна відповідність не доводить prefill
на актуальній публічній формі. Public push/release не виконується.

Початкова settings build **SUCCESS за 4 хв 16 с**, lint **0 errors,
72 warnings**. На isolated validation package:

- `settingsstructure` **PASS**: активний audit refresh на реальному стіку
  з інтервалом 5 с, coalescing, focus, category/page exit, pause/destroy/
  resume lifecycle; raw diagnostic QR декодовано з фактичного UI.
- `appaudit` **PASS** у межах read-only/system-row coverage цього probe.
- Регресія `settingsfocus`, `uisounds`, `wallpapers`, `libraryfocus` —
  **усі PASS**, cleanup **PASS**.
- Перший Feedback run не отримав потрібного screenshot (`NotFound`). Після
  settling/animation fixture і dense-QR raster/display fix QR build
  **SUCCESS за 6 хв 45 с**, lint **0 errors, 72 warnings**;
  повторний **Feedback PASS**: усі три фактично
  відрендеровані Issue QR декодовані з diagnostic URI, Back повертає focus.
  External browser, телефон, sign-in і submit цим не перевірялися.

Докази на SSD: `/Volumes/CarpeTVBuild/logs/probe-*-settings-20261114.log`;
regression run — `settings-regression-20261114` у тому самому logs namespace.
Захоплено 5 actual 1920×1080 screenshots у
`/Volumes/CarpeTVBuild/settings-review-20261114/` від `01-appearance.png`
до `05-authors.png`; координатор візуально оглянув усі п’ять.
Capture cleanup **PASS**, Home/accessibility baseline незмінні.
На Device bars виміряно по pixels: RAM 712..1471 із ширини 1120 — 67.8%,
відповідає 1300/1917; storage 712..1572 — 76.8%, відповідає 3204/4174.
Обидві смуги жовті за implementation thresholds 65%/85%. Це point-in-time
readings, не фіксовані характеристики вільних ресурсів.

Device screenshot показано власнику, **це не owner approval**. Повторний
`settingsstructure` повторно **PASS**, фінальний cleanup **PASS**.
Final `settings-qr` summary: `feedback=PASS`, `settingsstructure=PASS`,
`cleanup_errors=[]`, Home/services preserved. Policy і diff-check **PASS**.
Реалізацію підтверджено локальним runtime checkpoint і screenshots;
повний owner walkthrough/фінальне погодження **PENDING**. IA1 загалом
та phone/live-template gates не закриті. Source ще не закомічено.
Історичний modal INPUT_UNVERIFIED лишається незалежним
результатом fixture; не підміняти його новими PASS інших probes.

### Уточнення погодження власника

Після screenshot власник підтвердив «о це вже крутіше... рілі так я хотів».
Це approval саме Device memory/storage visual (ОЗП/Сховище, used/total,
пропорційні bars). Погодження всіх меню, phone/live forms і всього IA1
з цього повідомлення не випливає; решта owner walkthrough відкрита.

### Погоджений компактний формат ресурсів — наступне оновлення

Власник погодив уточнення Device resource cards: короткі **«ОЗП»** і
**«Сховище»**, використано/всього в **ГБ з одним локалізованим десятковим
знаком**, кольорова пропорційна смуга й окремий рядок **«Вільно»** з
адаптивними МБ/ГБ. Прибрати слово «зайнято» та великі числа в МБ з
головного used/total рядка. Це прийнята зміна presentation, не нове підменю.
Попередні MB screenshots лишаються історичним доказом старого формату.

Android реалізація та SettingsStructure probe адаптація тривають.
Запланована owner-visible версія **0.2.10 (17)**; build/test/deployment
цього формату **ще не PASS**. Оновлення основного app виконує координатор
лише після перевірок і backup поточної 0.2.9 на SSD; Home/services/P0
незмінні. Інші state/performance gates не закриваються цією зміною.

Компактний формат локально перевірено: validation build SUCCESS1m37s,
lint0errors72warnings, SettingsStructure localized GB/free **PASS**.
До цієї ж ще не встановленої **0.2.10 (17)** власник додав зміну іконки
категорії «Пристрій» із gear на **TV vector**. Виявлений окремий
inactive-library scroll loss після Settings recreation виправляється;
final UI/state validation і deployment ще pending. Backup 0.2.9 на SSD
вже завершено. Успіх format probe не доводить готовності state fix або
фактичного встановлення нової версії.

Final validation для 0.2.10: build **SUCCESS1m4s**, namespace
`compact-final-0210-20261114`; inactivelibrary/librarystate/authorsstate/
settingsstructure **усі PASS**, cleanup/Home/services invariants PASS.
Compact locale formatter і окремий free рядок під bar підтверджені; Device
має власний TV vector. Actual-catalog empty-query inactive-library state
після Activity recreation виправлено й перевірено, process death не охоплено.
Фінальна main build ще триває, **deployment0.2.10 pending**; не плутати
validation APK PASS із фактом оновлення встановленого застосунку.

### Основний застосунок 0.2.10 (17) встановлено й перевірено

**FINAL DEPLOY SUCCESS**: real main `agency.carpediem.tv.publicpreview`
оновлено до **0.2.10 (17)**, відкрито й візуально перевірено. Фінальна
main build **SUCCESS 1 хв 37 с**, lint **0 errors / 72 warnings**,
namespace `device-build-0.2.10-final-17-20261114` у SSD logs.
Перед заміною збережено 0.2.9 APK/preferences; same certificate перевірено,
оновлення in-place `-r -t` без uninstall. Private preferences незмінні до
явного launch; Home/services baseline preserved.

`/Volumes/CarpeTVBuild/device-deploy/0.2.10-17/deployment.json`:
status `installed_visible_and_verified`, `compact_usage_visible=true`,
visible version 0.2.10 (17) verified. Координатор оглянув actual screenshot
`/Volumes/CarpeTVBuild/device-deploy/0.2.10-17/owner-device-0.2.10.png`:
ОЗП/Сховище ліворуч, компактна used/total ГБ пара праворуч, окремий
«Вільно» в МБ під кольоровими bars; Device category з TV icon.
Point-in-time readings: ОЗП 1.2/1.9 ГБ, вільно 687 МБ; сховище
3.1/4.1 ГБ, вільно 987 МБ. Це не сталі значення ресурсів.

Це фактична поточна main версія, яка замінює попередній deployment-pending
стан; містить state fix після чотирьох validation PASS. Основний app
залишено встановленим і foreground для реального owner review —
cleanup/uninstall main не виконувати. Process death, широкі performance/
UX gates лишаються відкритими. Public release/commit і P0 mutations
у цьому checkpoint не виконувалися.

### Device QR: текст проти Issue посилання

Фактичний phone scan raw diagnostics читабельний, але телефон запропонував
Google/search URL; це не GitHub phone handoff PASS. Зберегти canonical raw
7-line QR; додати явне пояснення «QR містить текст, не посилання» й окрему
дію «Повідомити про помилку» до наявного Bug Issue QR flow. Реалізація/QA
pending, ціль наступної owner-visible версії 0.2.11 (18); поточна main
0.2.10 (17). Live form/sign-in/prefill потребують окремих фактичних доказів.

### 0.2.11 (18) diagnostics action checkpoint

Main0.2.11(18) встановлено й запущено після SSDbackup0.2.10, prefs/Home/
services preserved. New settingsstructure Diagnostics→Bug-default generated
QR/raw canonical payload/Back **PASS**, feedback усі3 screenQR із довшим
public/privacy copy **PASS**. Main visible UX readback ще pending;
phone GitHub form/sign-in/prefill цими probes не доведено. Build normal
SUCCESS1m56s lint0/72, validation SUCCESS1m57s; SSD log namespaces
`diagnostics-perf-0211-20261114`, `device-build-0.2.11-18-20261114`.

Visible main0.2.11 Device hint/BugQR підтверджено root PNG; телефонний
GitHub handoff ще pending. На v18 screenshot Close Feedback частково
обрізаний; spacing fix із незмінним192dpQR і full-buttons assertion
підготовлені, runtime ще pending. Наступна планова owner-visible версія
0.2.12(19) після перевірок; не вважати її встановленою. Новий запит плавної
зміни кольору clicked icon уточнюється; motion source edits ще не внесені.
