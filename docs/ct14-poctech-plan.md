# CT-14 (POCTech) — план добавления сенсора в JugglucoNG

База: `main` @ `058374ab`. Документ — план работ и чек-лист приёмки. Код по нему пишет исполнитель, проверка — отдельным проходом (см. §8).

---

## 1. Главный вывод разведки

**CT-14 — это не новое семейство. Это CT2-вариант того же вендора (IST / `ist.com.sdk`), чей CT3-вариант уже реализован в репозитории как драйвер `anytime`.**

Доказательства:

| Факт | Где найдено |
|---|---|
| `AnytimeConstants.kt` шапка: «Reverse-engineered from com.anytime.rus / com.kamin.cgmblelib + **ist.com.sdk**» | `Common/src/main/java/tk/glucodata/drivers/anytime/AnytimeConstants.kt:1` |
| `Family.CT2("CT2", needsLegacyUuids = true)` уже есть в enum | там же, ~строка 291 |
| В `FAMILY_TABLE` уже лежат все CT2-префиксы: `SN04, SN06, SN08, SN12, SN18, SN20, SN22, SN48, SN50, SN52` | там же, ~строки 322–331 |
| Legacy UUID-блок `FFF0 / FFF1(write) / FFF2(notify)` уже объявлен и уже используется как fallback при discovery | `AnytimeConstants.kt` + `AnytimeBleManager.kt:2077-2088` |
| Заглушки вендорского SDK уже в дереве: `ist/com/sdk/{AlgorithmTools,EDevice,EGattMessage,LatestData,HistoryData,CurrentGlucose,KRDecodeData,DataInput,DataOutput,SDKVersion}.kt`, и `EDevice.kt` уже содержит CT2-записи | `Common/src/main/java/ist/com/sdk/` |
| В APK `POCTech_xDrip_v_0.2.apk` тот же самый SDK: `ist.com.sdk.EGattMessage.CT2 = (fff0, write fff1, read fff2)` | декомпиляция jadx |

Из APK (`ist.com.sdk.EDevice`): интервал у всех CT2 = **3 мин**, `endNumber = 6740` → `6740 × 3 мин = 337 ч ≈ **14.04 суток**`. Отсюда и название **CT-14**. Кандидаты на CT-14: `SN20 / SN48 / SN50 / SN52` (алгоритм 7 = `CT2A_44V`), `SN04` (алг. 8), `SN08` (`endNumber 6720`, алг. 1). Семидневки — `SN18` (3380). Точный префикс фиксируется по реальному имени устройства при первом сканировании.

**Следствие для оценки:** не «новый драйвер с нуля» (≈5–6 тыс. строк, как `anytime`), а **вариант CT2 внутри существующего драйвера + отдельная точка входа в UI**. Реальный объём — ниже, ближе к 800–1200 строкам плюс тесты.

---

## 2. Что даёт каждый источник

### 2.1 `POCTech_xDrip_v_0.2.apk` — **протокол** (первичный источник)

Декомпилируется чисто, тела методов читаемые Java:

```
jadx -d out --no-res /Users/jetcat/Downloads/POCTech_xDrip_v_0.2.apk
```

Что внутри:

* `ist/com/sdk/ProtocolToolsHolder.java` (672 стр.) — **вся сборка/разбор кадров, реальный байт-код**, не обфусцирован.
* `ist/com/sdk/ProtocolTools.java` (835 стр.) — фасад + `Glucose`, `GlucoseByTransmitter`, `TransmitterVersion`, `Verify` (парсер AD-структур рекламы: имя, категория, флаг «привязан»).
* `ist/com/sdk/EDevice.java` — таблица префиксов → (`initNumber`, `endNumber`, GATT-профиль, id алгоритма).
* `ist/com/sdk/{Trend,ErrorCode,WarnCode,CalibrationStatus}.java` — коды состояний.
* `com/kamin/cgmblelib/ble/BleService.java` — конечный автомат соединения.
* `lib/{arm64-v8a,armeabi-v7a}/libalgorithm-jni.so` — вендорский алгоритм. **Не бинарный blackbox-обязателен**: в репозитории уже есть путь «нативный `.so` опционально в `src/main/jniLibs/{abi}`, иначе линейный Kotlin-fallback» (`AnytimeAlgorithm.kt:1-30`). Тащить чужой `.so` в репозиторий не предлагаю — лицензионно грязно.

### 2.2 `apex-re-bb430/out/` (Blueberry / Apex) — **механика сессии и калибровка** (вторичный, но важный)

Blutter-декомпиляция Flutter-приложения. Тела методов — заглушки (`size: -0x1`), но **пул строк и объектов читается полностью** и раскрывает архитектуру их CGM-слоя. У них поддержано несколько CGM: `poctech`, `sunlant`, `anytime`, `ew`; enum `cgmType`: `none=0`, **`poctech=1`**, `anytime=4`.

Извлечённое (`out/pp.txt`):

* GATT-профиль Poctech: `pp+0x1d778` → `{ "FFF0", "FFF0", "FFF2", "FFF1" }` — совпадает с `EGattMessage.CT2`.
* Порядок сессии (по строкам лога `pp+0x1d658…`): `self test → hand shake → init → set time → glucose → backfill → low power / unbind`.
* Бэкфилл: `backfill glucose:` / `backfill finished` / `backfill finished - received abnormal number` — есть защита от аномального числа записей.
* Фильтрация истории: `Skipping old record #`, `Min history date updated to`, `No valid glucose data, history filter disabled`.
* Калибровки: `Calibrations updated:`, `Loaded N`, `Failed to load calibrations:`, `Calibrations not loaded yet, using raw values` — калибровки персистятся и подгружаются до применения к сырым значениям.
* UI-модель CT-14 (`pp+0x17130…0x17210`): `Slope`, `Intercept`, `Ib`, `Iw`, `Noise`, `Sensor Sensitivity`, `Raw value`, `Filtered Glucose`, `Unfiltered Glucose`, `Trend`, `Sensor Age`, `Sensor Status`, `Sync Date`, `Package Number`, `Temperature`, `Transmitter Battery`, `Expired At`, `Calibration Parameters`.
* **Правила допуска к калибровке** (то, ради чего источник и нужен):
  * `Calibration unavailable: the sensor has been active for less than 24 hours`
  * `Calibration unavailable. Wait for flat trend (→) and noise below 0.2`
  * список калибровок с удалением по одной (long-press) и «удалить все».

---

## 3. Протокол CT2 — извлечённая спецификация

Все кадры — **без заголовка и преамбулы**. Контроль целостности — последний байт = `сумма всех предыдущих байт & 0xFF` (`ProtocolToolsHolder.ReceiveSum` / `isLegal`). CRC нет.

### 3.1 Команды (TX)

| Назначение | Байты | Ответ (RX) |
|---|---|---|
| Версия трансмиттера | `03` | 7 байт, `[0]=03`, ver = `b1*1000+b2*100+b3*10+b4`, месяц `b5`, день `b6` |
| Handshake | `48 <ASCII имя…> <sum>` | `isLegal(sum)` |
| Установка времени | `54 yrHi yrLo mon day hh mm ss <sum>` (9 байт) | `isLegal` |
| Init | `53 55 AA 52` | `isLegal` |
| Self-test / check | `43 55 AA 42` | ≥7 байт: `b5` = температура (бит 7 → `+0.5`), `b6` = ток/питание. `temp ≥ 50` → `CHECK_FAIL_TEMPERATURE(2)`, `b6 < 50` → `CHECK_FAIL_POWER(1)` |
| Low power | `57 55 AA 56` | `isLegal` |
| Unbind | `58 55 AA 57` | `isLegal` |
| Запрос записи по id | `55 idHi idLo <sum>` | 15-байтовые записи |
| Калибровка (референс BG, мг/дл) | `08 mgdlHi mgdlLo <sum>` | `[0]==08 && isLegal` |
| Глюкоза, посчитанная трансмиттером | `09 idHi idLo` | `[0]==09` → `GluMG` / `GluMM` |

> Обратите внимание: у CT2 **опкоды — ASCII-буквы** (`'C'`=0x43 check, `'S'`=0x53 init, `'T'`=0x54 time, `'U'`=0x55 pull, `'W'`=0x57 low-power, `'X'`=0x58 unbind, `'H'`=0x48 handshake). У CT3 и CT2.5 — совсем другие однобайтные/суммированные формы. Пересечения по значениям есть (`0x08`, `0x09`), но смысл разный — это главный источник потенциальных багов.

### 3.2 Запись глюкозы CT2 (push и pull, один формат)

Цикл вендора крутится пока `remaining() >= 15`; полезной нагрузки — 14 байт, 15-й — контрольная сумма.

| Смещение | Размер | Поле |
|---|---|---|
| 0 | 1 | опкод |
| 1–2 | 2 | `id` (int16, **big-endian**) |
| 3 | 1 | год `= b + 2000` |
| 4 | 1 | месяц |
| 5 | 1 | день |
| 6 | 1 | час |
| 7 | 1 | минута |
| 8–9 | 2 | **`Iw`** = int16 / 10 (нА) |
| 10–11 | 2 | **`Ib`** = int16 / 10 (нА) |
| 12 | 1 | температура: бит 7 установлен → `(b − 128) + 0.5`, иначе `b` |
| 13 | 1 | `electric` — заряд трансмиттера |
| 14 | 1 | sum |

**Ловушка:** в CT2 порядок `Iw, Ib`; в CT3/CT2.5 — `Ib, Iw`. И масштаб: CT2 `/10`, CT3 — целое + дробная часть `/100`. Перепутанные Ib/Iw дадут правдоподобные, но систематически неверные значения глюкозы — на глаз не ловится, ловится только тестом на зафиксированном кадре.

### 3.3 Реклама / привязка

`ProtocolTools.Verify` + `ProtocolToolsHolder.verifyHolder(byte[])` разбирает AD-структуры scan record: имя устройства, категория, флаг «привязан» (`isBound`). Это даёт определение «сенсор уже привязан к чужому телефону» до подключения.

---

## 4. Архитектурное решение

**Рекомендация: реализовать вариант CT2 внутри пакета `drivers/anytime/`, а наружу вывести отдельный тип сенсора «POCTech CT-14».**

Почему не отдельный пакет `drivers/poctech/` с нуля:

* `AnytimeBleManager.kt` — 4204 строки готового конечного автомата (discovery, reconnect, backfill, таймауты pull, история, дедуп). Форк = второй экземпляр этих багов.
* Legacy-UUID discovery для `FFF0` там уже есть и работает.
* Таблица устройств, профили, реестр, identity-adapter, алгоритм-фасад — общие.

Что при этом остаётся раздельным (пользователь не должен видеть «Anytime» при добавлении CT-14):

* своя запись в `SensorType` и в `SensorTypePicker`;
* свой мастер подключения;
* своя `ManagedSensorUiFamily`;
* свои строки/бренд.

Альтернатива (если нужен полностью независимый релизный цикл драйвера): тонкий пакет-фасад `drivers/poctech/`, который переиспользует `AnytimeFrames` / `AnytimeAlgorithm` / `AnytimeBleManager` и содержит только Registry + Driver + IdentityAdapter + Wizard. Это компромисс; выбирать до старта P1, менять потом дорого.

---

## 5. Этапы

### P0 — Подготовка и подтверждение гипотезы *(блокирует всё остальное)*

1. Ветка от свежего `main`: `feature/ct14-poctech`.
2. Полная декомпиляция APK в рабочую папку вне репозитория (по образцу `apex-re-bb430`), плюс краткие RE-заметки в `docs/`.
3. **Снять реальное имя устройства CT-14** при сканировании и сопоставить с `FAMILY_TABLE`. Это определяет `endNumber` (14 сут / 7 сут), `initNumber` (прогрев 60 или 180 мин) и id алгоритма (1 / 6 / 7 / 8).
4. Снять один btsnoop-лог полной сессии официального приложения: подключение → handshake → init → set time → первые push-записи → одна калибровка. Без него P3 и P4 делаются вслепую.

**Приёмка P0:** известен префикс `SNxx`, подтверждены UUID `FFF0/FFF1/FFF2`, есть ≥1 сырой кадр глюкозы и ≥1 кадр check в hex.

---

### P1 — Кадры CT2

Файлы: `AnytimeConstants.kt`, `AnytimeFrames.kt`.

* Константы опкодов CT2 (`TX_CT2_CHECK = 0x43` и т.д.) — отдельным блоком, с комментарием «ASCII-опкоды, не путать с CT3».
* Билдеры: `ct2Check()`, `ct2Init()`, `ct2SetDate()`, `ct2Handshake(name)`, `ct2LowPower()`, `ct2Unbind()`, `ct2PullGlucose(id)`, `ct2InputBgMg(mgdl)`, `ct2Version()`.
* Парсеры: `parseCt2RawRecords()` (14+1 байт, см. §3.2), `parseCt2CheckResponse()`, `parseCt2VersionResponse()`, `verifyCt2Sum()`.

**Приёмка P1:** unit-тесты `AnytimeCt2FramesTests.kt` — побайтовое сравнение каждого билдера с эталоном из декомпиляции; round-trip парсера на реальном кадре из P0; проверка, что `Iw` читается из 8–9, а `Ib` из 10–11.

---

### P2 — Профиль и жизненный цикл

Файлы: `AnytimeProfile.kt`, `AnytimeConstants.kt`.

* Прогрев из `initNumber × 3 мин` (сейчас `AnytimeProfileResolver` использует общий `DEFAULT_WARMUP_MINUTES` — для CT2 это неверно: `SN06`/`SN12` дают 180 мин, остальные 60).
* Ресурс сессии из `endNumber × 3 мин`.
* Порог низкого заряда для CT2 (в `checkResponse` вендор сравнивает с 50; у CT2.5 — 2.97 В, у CT3 — 4.05 В; для CT2 шкала другая — уточнить по P0).

**Приёмка P2:** `AnytimeCt2ProfileTests.kt` — для каждого CT2-префикса ожидаемые прогрев/срок/алгоритм.

---

### P3 — Конечный автомат BLE

Файл: `AnytimeBleManager.kt`.

* Ввести `usesCt2LegacyFrames()` и развести все `*Frame()` (`checkFrame`, `initFrame`, `setDateFrame`, `lowPowerFrame`, `unbindFrame`, `pullGlucoseFrame`, `inputBgFrame`) на CT2-ветку. **Сейчас `usesPlainControlFrames()` жёстко возвращает `false` (строка 1812), поэтому CT2 молча уходит в CT3-ветку однобайтных кадров — это готовый баг.**
* Добавить шаг handshake перед init (у CT3 его нет).
* Разбор push-нотификаций CT2 (15-байтовые записи, возможна склейка нескольких в одном пакете).
* Бэкфилл: pull по одной записи (`0x55`), без серийного `0x22` — у CT2 его нет; гард на «abnormal number» по образцу Blueberry.
* MTU: `DEFAULT_MTU = 211` рассчитан на CT3. Для Telink-чипа CT2 проверить и при необходимости оставить дефолтный.

**Приёмка P3:** воспроизведение сессии из btsnoop-лога P0 через replay-харнесс (в репозитории уже есть прецедент — `SibionicsReplayHarness.kt`); никакие CT3-кадры не уходят при `family == CT2`.

---

### P4 — Алгоритм и калибровка *(самая содержательная часть)*

Файлы: `AnytimeAlgorithm.kt`, `AnytimeCalibrationPolicy.kt` (сейчас 30 строк — расширяется).

Целевая модель — из Blueberry (§2.2), она богаче текущей:

1. **Хранимые калибровки.** Список точек `(timestamp, glucoseId, referenceMgdl, Ib, Iw, T)`, персистится, подгружается на старте. До загрузки — режим «using raw values», не подставлять недокалиброванное значение как калиброванное.
2. **Модель.** `Slope` / `Intercept` по точкам (вендорский `inputKAndR` — это K/R трансмиттера, отдельная сущность; в CT2 есть и то и другое — не смешивать).
3. **Гейты допуска:**
   * возраст сенсора ≥ 24 ч;
   * тренд плоский (`TREND_STEADY`);
   * шум < 0.2.
   Каждый гейт — со своим пользовательским текстом отказа.
4. **Управление точками:** удаление одной, удаление всех, пересчёт после изменения (`onUserCalibrationRevisionChanged` уже есть в контракте `ManagedBluetoothSensorDriver`).
5. **Диагностика в UI:** `Ib`, `Iw`, `Noise`, `Slope`, `Intercept`, `Sensitivity`, `Raw`, `Filtered`, `Unfiltered`, `Package Number`, `Temperature`, `Battery` — уже есть слоты в `ManagedSensorUiSnapshot` (`sensorDetailTelemetry`, `vendorCalibrations`).
6. **Нативный алгоритм** — только как опциональный путь через существующий механизм `jniLibs`, без коммита вендорского `.so` в репозиторий. Основной путь — Kotlin.

**Приёмка P4:** тесты на каждый гейт; тест «калибровки не загружены → сырой режим»; тест монотонности; прогон на реальном ряде из P0 с расхождением к официальному приложению в пределах оговорённого допуска.

---

### P5 — Идентичность, реестр, хранение

Файлы: `AnytimeRegistry.kt`, `AnytimeManagedSensorIdentityAdapter.kt` (либо новые в `drivers/poctech/`), `ManagedSensorIdentityRegistry.kt`, `ManagedBluetoothSensorDriver.kt`.

* Свой префикс provisional-id (по образцу `ANY-`, `ICN-`).
* Новый `ManagedSensorUiFamily.POCTECH`.
* Регистрация адаптера в `ManagedSensorIdentityRegistry.all`.
* Ограничение 16 символов на нативное имя сенсора (`MAX_NATIVE_SENSOR_ID_CHARS`) — проверить, что серийники CT-14 в него укладываются.

**Приёмка P5:** `SensorIdentityTests` и `ManagedSensorHandoffCoverageTests` расширены новым семейством и зелёные; удаление сенсора чистит и prefs, и нативный слот, и пушится на часы (`WearSync2.pushRemoval`).

---

### P6 — UI и локализация

Файлы: `SensorTypePicker.kt` (+ `SensorType.POCTECH_CT14`), `SensorSelectionCards.kt`, `SensorScreen.kt`, `SensorCard.kt`, `DashboardScreen.kt`, `SensorViewModel.kt`, новый `ui/setup/PoctechCt14SetupWizard.kt`, `AndroidManifest.xml` (если появляется свой reconnect-receiver), `res/values*/strings.xml`.

* Мастер по образцу `AnytimeSetupWizard.kt`: скан → выбор устройства → handshake/bind → прогрев.
* Экран калибровки с текстами гейтов и памяткой (в Blueberry есть готовый текст «Important for sensor calibration» — можно взять как основу, но переписать своими словами).
* Строки: базовый `values/strings.xml` обязателен; `values-ru` — вторым приоритетом; остальные локали не блокируют.

**Приёмка P6:** сенсор добавляется из чистой установки без обращения к коду; отображаются возраст, срок, батарея, Ib/Iw/шум; калибровка проходит и отражается на графике.

---

## 6. Открытые вопросы (закрываются в P0)

| # | Вопрос | Как закрыть | Блокирует |
|---|---|---|---|
| 1 | Точный `SNxx` реального CT-14 | скан BLE | P2 |
| 2 | Аргумент handshake `0x48 <имя>` — что именно за строка (имя устройства? серийник? id пользователя?) | btsnoop | P3 |
| 3 | Шкала батареи CT2 (в `checkResponse` сравнение с 50 — это не вольты) | btsnoop + сопоставление с индикатором офиц. приложения | P2 |
| 4 | Точная формула шума и «плоского тренда» в Blueberry (тела методов — заглушки) | ручной дизасм `libapp.so` по адресам из `blutter_frida.js`, либо собственная реализация по наблюдаемому ряду | P4 |
| 5 | Нужен ли `0x09` (глюкоза от трансмиттера) как fallback при отсутствии калибровок | сравнить с собственным расчётом на реальных данных | P4 |
| 6 | Формат QR/K-R для CT-14 (у Anytime есть `AnytimeQr.kt`, 384 стр.) | коробка/наклейка сенсора + `inputKAndR_request` в SDK | P4 |

Пункт 4 — единственный, где источник Blueberry не даёт готового ответа статически: Blutter оставил тела closures/async как `size: -0x1`. Либо ручной `llvm-objdump` по `libs/libapp.so`, либо своя реализация. Рекомендую второе: гейты «24 ч / плоский тренд / шум < 0.2» — это спецификация, а не алгоритм, и её достаточно.

---

## 7. Тестовая стратегия

Ориентир — существующие наборы `Common/src/test/java/tk/glucodata/drivers/anytime/*Tests.kt`.

* `AnytimeCt2FramesTests.kt` — байт-в-байт по каждому билдеру и парсеру.
* `AnytimeCt2ProfileTests.kt` — таблица префиксов.
* `Ct14CalibrationPolicyTests.kt` — каждый гейт отдельно + комбинации.
* `Ct14AlgorithmTests.kt` — фиксированный ряд Ib/Iw/T → ожидаемая глюкоза.
* Replay-харнесс на btsnoop из P0 (прецедент: `SibionicsReplayHarness.kt`).
* Регрессия: `SensorIdentityTests`, `ManagedSensorHandoffCoverageTests`, `AnytimeFramesTests` — CT3-путь не должен измениться. Это отдельный явный критерий: **ни один существующий тест `anytime` не должен быть отредактирован под CT2.**

---

## 8. Чек-лист приёмки (проверяю я, по этапам)

Для каждого этапа проверяется:

- [ ] Реализовано ровно то, что в §5, без расползания в соседние драйверы.
- [ ] CT3/CT2.5/CT4/CT5-ветки не изменены поведенчески; существующие тесты `anytime` не правились.
- [ ] Каждая протокольная константа снабжена ссылкой на источник (`ProtocolToolsHolder.<метод>` или `pp+0x…`).
- [ ] Ib/Iw не перепутаны (отдельный тест).
- [ ] Нет вендорских бинарников (`libalgorithm-jni.so`) и вендорских ресурсов в коммитах.
- [ ] Ошибки не глотаются молча: каждый отказ парсинга/калибровки логируется и виден в UI.
- [ ] Строки вынесены в `strings.xml`, без хардкода в Compose.
- [ ] Тесты из §7 добавлены и зелёные; сборка `:Common` проходит.

---

## 9. Порядок и оценка

```
P0 (разведка, блокирующий)  →  P1 (кадры)  →  P2 (профиль)  →  P3 (BLE)  →  P4 (алгоритм)  →  P5 (identity)  →  P6 (UI)
                                    ↘ P5 и P6 можно вести параллельно с P4 после P3 ↗
```

Критический путь — P0 → P3 → P4. Без btsnoop-лога и реального сенсора P3/P4 не верифицируемы, только «правдоподобны».
