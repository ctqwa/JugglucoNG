# CT-14 (CT2) — план реализации

Исполняемый план по фазе B из `docs/ct-driver-plan.md`. Стратегия и источники — там; здесь — что именно писать, в каком порядке, какими сигнатурами, с явными инженерными решениями там, где RE не даёт однозначного ответа. Код не пишу — это план для исполнителя.

Разведка (B0/B1) закрыта живым захватом на `SN08402458`: все опкоды, оба формата записи (`0x44`/`0x47`), бэкфилл, порядок `Iw`/`Ib` подтверждены байт-в-байт. Ссылки на конкретные подтверждённые кадры — по тексту, полные — в `ct-driver-plan.md` §B0–B1.

---

## 0. Зависимость от фазы A

Открытый вопрос B4 («нужен ли `0x08` для CT2») закрыт статическим анализом POCTech xDrip: команда в приложении не используется, калибровка полностью локальная (Room-схема `K, k1, k2, k3, Ib_avg, T_LastRef`). Это меняет приоритет внутри B4: **affine-слой из A1 — это и есть основной путь калибровки CT2**, а не запасной. `0x08` реализовать в B1 всё равно (это часть протокола, дешёво, может понадобиться для диагностики), но не блокировать им B4.

Из этого следует порядок: **B1–B3 (протокол и BLE-автомат) можно писать независимо от фазы A прямо сейчас.** B4 (подключение калибровки) физически не может быть закончен, пока не готов `A1` — но сама заглушка (интерфейс, вызов из B3) пишется заранее, реализация подставляется когда A1 будет готов.

---

## 1. Новые и изменяемые файлы

| Файл | Статус | Что |
|---|---|---|
| `drivers/anytime/AnytimeConstants.kt` | изменяемый | CT2-опкоды, обновление `usesPlainControlFrames()` |
| `drivers/anytime/AnytimeFrames.kt` | изменяемый | CT2-билдеры/парсеры |
| `drivers/anytime/AnytimeProfile.kt` | изменяемый | прогрев/ресурс/батарея для CT2-семейства |
| `drivers/anytime/AnytimeBleManager.kt` | изменяемый | ветвление конечного автомата на CT2 |
| `drivers/anytime/AnytimeCalibrationPolicy.kt` | изменяемый (общий с A2) | ничего специфичного для CT2 не требуется — гейты общие |
| `drivers/anytime/Ct14ManagedSensorIdentityAdapter.kt` | новый | наследует/переиспользует `AnytimeManagedSensorIdentityAdapter`, свой префикс |
| `ui/components/SensorTypePicker.kt` | изменяемый | `SensorType.CT14` |
| `ui/setup/Ct14SetupWizard.kt` | новый | по образцу `AnytimeSetupWizard.kt`, без QR (CT2 не имеет K/R QR-потока — см. §5) |
| `test/.../anytime/AnytimeCt2FramesTests.kt` | новый | побайтовые тесты билдеров/парсеров против захваченных кадров |
| `test/.../anytime/AnytimeCt2ProfileTests.kt` | новый | таблица префиксов CT2 |
| `res/values/strings.xml`, `values-ru/strings.xml` | изменяемый | строки CT-14 |

Отдельный пакет `drivers/poctech/` не создаётся — решение зафиксировано в `ct-driver-plan.md` §4 («Архитектура»).

---

## 2. `AnytimeConstants.kt` — опкоды CT2

Добавить блок, явно промаркированный как ASCII-опкоды (не путать с однобайтными CT3):

```kotlin
// ---- CT2 opcode catalog — ASCII-letter opcodes, distinct numbering from CT3/CT2.5 ----
// Confirmed live on SN08402458, 2026-09-07 (см. ct-driver-plan.md §B0-B1).

const val TX_CT2_VERSION: Byte     = 0x03            // 'ETX'? — не буква; см. §B1 таблицу
const val TX_CT2_HANDSHAKE: Byte   = 0x48             // 'H'
const val TX_CT2_SET_DATE: Byte    = 0x54             // 'T'
const val TX_CT2_INIT: Byte        = 0x53             // 'S'
const val TX_CT2_CHECK: Byte       = 0x43             // 'C'
const val TX_CT2_LOW_POWER: Byte   = 0x57             // 'W'
const val TX_CT2_UNBIND: Byte      = 0x58             // 'X'
const val TX_CT2_PULL_GLUCOSE: Byte = 0x55            // 'U'
const val TX_CT2_INPUT_BG_MG: Byte = 0x08
const val TX_CT2_GLUCOSE_BY_TRANSMITTER: Byte = 0x09

// RX
const val RX_CT2_HANDSHAKE_ACK: Byte = 0x48            // фиксированный кадр {0x48,0x55,0xAA,sum}
const val RX_CT2_SET_DATE_ACK: Byte  = 0x54            // фиксированный кадр {0x54,0x55,0xAA,sum}
const val RX_CT2_INIT_ACK: Byte      = 0x53
const val RX_CT2_CHECK: Byte         = 0x43
const val RX_CT2_PUSH_GLUCOSE: Byte  = 0x44            // живой push, подтверждено вживую
const val RX_CT2_PULL_RESPONSE: Byte = 0x47            // ответ на 0x55, подтверждено вживую — НЕ совпадает с TX_CT2_PULL_GLUCOSE
```

**Решение, зафиксировать явно:** `0x47` не выводится из `0x55` арифметически и не описан в декомпилированном SDK как отдельный «opcode ответа» (`ProtocolTools.Glucose` конструктор просто пропускает byte 0). Это открытие сделано только живым захватом. Жёстко прописать как константу, не пытаться вычислить.

`usesPlainControlFrames()` (`AnytimeBleManager.kt:1812`) сейчас `false` всегда — заменить на `family == AnytimeConstants.Family.CT2`, так CT2 перестаёт молча уходить в CT3-ветку однобайтных кадров (баг из `ct-driver-plan.md` §B3).

---

## 3. `AnytimeFrames.kt` — билдеры и парсеры

### 3.1 Билдеры (`Builders` object)

```kotlin
fun ct2Handshake(deviceName: String): ByteArray  // {0x48, ASCII(deviceName), sum}
fun ct2SetDate(calendar: Calendar = Calendar.getInstance()): ByteArray
    // {0x54, yearHi, yearLo, month, day, hour, minute, second, sum} — год БЕЗ вычитания 2000 (в отличие от RX-записи, где year=b+2000)
fun ct2Init(): ByteArray            = byteArrayOf(0x53, 0x55, 0xAA.toByte(), 0x52)
fun ct2Check(): ByteArray           = byteArrayOf(0x43, 0x55, 0xAA.toByte(), 0x42)
fun ct2LowPower(): ByteArray        = byteArrayOf(0x57, 0x55, 0xAA.toByte(), 0x56)
fun ct2Unbind(): ByteArray          = byteArrayOf(0x58, 0x55, 0xAA.toByte(), 0x57)
fun ct2PullGlucose(id: Int): ByteArray  // {0x55, idHi, idLo, sum}
fun ct2InputBgMg(mgdl: Int): ByteArray  // {0x08, mgdlHi, mgdlLo, sum} — реализовать для протокольной полноты, не для UI-потока (см. §0)
fun ct2Version(): ByteArray         = byteArrayOf(0x03)
```

Все константные (`init`/`check`/`lowPower`/`unbind`) — не вызывать `withSum()` заново на каждый вызов, взять как статические `byteArrayOf` литералы (они и так фиксированные, суммы уже посчитаны и подтверждены вживую).

### 3.2 Парсер живой/исторической записи глюкозы

Один парсер на оба опкода — байтовая раскладка идентична, различается только опкод в байте 0 и семантика байта 13:

```kotlin
data class Ct2GlucoseFrame(
    val record: AnytimeRawRecord,
    val isHistorical: Boolean,      // true для 0x47, false для 0x44
    val batteryPercent: Int?,       // байт 13; null когда isHistorical (0xFF — не заряд, сентинел)
)

fun parseCt2GlucoseRecord(bytes: ByteArray): Ct2GlucoseFrame? {
    if (bytes.size != 15) return null
    if (!verifySum(bytes)) return null
    val opcode = bytes[0]
    val isHistorical = when (opcode) {
        AnytimeConstants.RX_CT2_PUSH_GLUCOSE  -> false
        AnytimeConstants.RX_CT2_PULL_RESPONSE -> true
        else -> return null
    }
    val id = u16(bytes[1], bytes[2])
    // bytes[3] = год-2000, bytes[4]=month, bytes[5]=day, bytes[6]=hour, bytes[7]=minute — без секунд
    val iw = u16(bytes[8], bytes[9]) / 10f
    val ib = u16(bytes[10], bytes[11]) / 10f
    val t  = decodeCt2Temperature(bytes[12])
    val batteryByte = bytes[13].toInt() and 0xFF
    return Ct2GlucoseFrame(
        record = AnytimeRawRecord(indexInPacket = 0, glucoseId = id, ibNa = ib, iwNa = iw, temperatureC = t, recordBytes = bytes),
        isHistorical = isHistorical,
        batteryPercent = if (isHistorical) null else batteryByte,
    )
}

fun decodeCt2Temperature(b: Byte): Float {
    val v = b.toInt() and 0xFF
    return if (v and 0x80 != 0) ((v - 128) and 0xFF) + 0.5f else v.toFloat()
}
```

**Решение по дате/времени в записи:** в 15-байтной записи есть только час:минута, без секунд (в отличие от `setDate`, где секунды есть). Для `sampleTimeMs` использовать расчёт от `sensorStartAtMs + glucoseId * readingIntervalMinutes`, как уже делает `AnytimeBleManager` для CT3 (`anchorSensorTimelineIfNeeded`), а не собирать метку из полей записи напрямую — секунды и год из записи можно использовать только для sanity-проверки (год всегда `b[3]+2000`, должен совпадать с текущим), не как источник истины по времени.

### 3.3 Парсер check-ответа

```kotlin
data class Ct2CheckResult(
    val iwNa: Float,
    val ibNa: Float,
    val temperatureC: Float,
    val powerByte: Int,     // b6 — используется вендорским SDK для gate, шкала не подтверждена
    val passed: Boolean,    // temp < 50 && powerByte >= 50 — вендорская логика, СЛЕДУЕТ повторить как есть даже с неподтверждённой семантикой powerByte
)

fun parseCt2CheckResponse(bytes: ByteArray): Ct2CheckResult? {
    if (bytes.size < 8) return null
    if (!verifySum(bytes)) return null
    val iw = u16(bytes[1], bytes[2]) / 10f
    val ib = u16(bytes[3], bytes[4]) / 10f
    val t = decodeCt2Temperature(bytes[5])
    val power = bytes[6].toInt() and 0xFF
    val tempFail = t >= 50f
    val powerFail = power < 50
    return Ct2CheckResult(iw, ib, t, power, passed = !tempFail && !powerFail)
}
```

**Решение по porogu `power < 50`:** это условие из декомпилированного вендорского `checkResponse()`, не из живых данных (мы видели только `power=100` и `power=96`, обе выше порога, ветку отказа не наблюдали). Перенести как есть — вендорская константа надёжнее гадания, но пометить комментарием как непроверенную живьём (ссылка на открытый вопрос №7 в `ct-driver-plan.md`).

---

## 4. `AnytimeProfile.kt` — профиль CT2

`AnytimeProfileResolver.resolve()` уже вычисляет `ratedLifetimeDays` из `endNumber`/`readingMinutes`; для CT2 нужно только:

```kotlin
val warmupMinutes = when {
    entry.family == AnytimeConstants.Family.CT2 && (entry.prefix == "SN06" || entry.prefix == "SN12") -> 180  // initNumber=60 × 3мин
    entry.family == AnytimeConstants.Family.CT2 -> 60                                                        // initNumber=20 × 3мин, остальные префиксы
    entry.family == AnytimeConstants.Family.CT5 -> AnytimeConstants.CT5_WARMUP_MINUTES
    else -> AnytimeConstants.DEFAULT_WARMUP_MINUTES
}
```

**Подтверждено вживую:** прогрев `SN08` — 60 мин (`initNumber=20 × 3 мин`), и по факту первая push-запись пришла ровно после срабатывания `Init`, не после истечения 60 минут — то есть warmup в терминах вендорского `EDevice.initNumber` определяет минимальное число «нулевых»/непригодных записей, а не блокирует сессию целиком. Отразить в `AnytimeProfile`: поле `warmupMinutes` остаётся для расчёта `sensorRemainingHours`/UI, но не должно гейтить получение данных с сенсора — только достоверность первых `initNumber` записей для калибровки (согласуется с гейтом «возраст сенсора» из A2, который и так UI-уровня, а не BLE-уровня).

Батарея (порог низкого заряда): пока не подтверждена шкала для CT2, оставить дефолт как placeholder `BATTERY_LOW_VOLTS_CT3`-аналог, явно не привязывая к вольтам (у CT2 в check-ответе byte6 — не вольты, судя по наблюдавшимся значениям 96–100, скорее уже проценты) — `lowBatteryThreshold = 20` (проценты, не вольты), пересмотреть когда появятся живые данные с разряженного сенсора.

---

## 5. `AnytimeBleManager.kt` — конечный автомат

### 5.1 Разводка кадров по семейству

Существующие приватные `*Frame()` методы (`checkFrame`, `initFrame`, `lowPowerFrame`, `resetFrame`, `unbindFrame`, `setDateFrame`, `pullGlucoseFrame`) уже разветвляют CT2.5/CT3/CT2.5/CT5 через `usesSummedFrames()`/`usesPlainControlFrames()`/`isCt5()`. Добавить `isCt2()` рядом с `isCt5()`:

```kotlin
private fun isCt2(): Boolean = familyEntry.family == AnytimeConstants.Family.CT2
```

И в каждом `*Frame()` — CT2-ветка первой (до `usesSummedFrames()`, у CT2 своя, не суммированная в смысле CT2.5/CT3 форма, а ASCII-опкод + `0x55 0xAA` заполнитель):

```kotlin
private fun checkFrame(): ByteArray =
    if (isCt2()) AnytimeFrames.Builders.ct2Check()
    else if (usesSummedFrames()) AnytimeFrames.Builders.checkSummed()
    else AnytimeFrames.Builders.check()
```

Аналогично для `initFrame`, `lowPowerFrame`, `unbindFrame`, `setDateFrame`, `pullGlucoseFrame` (последний — с гейтом `count==1` всегда для CT2, серийного pull нет). **Новый метод `handshakeFrame()`** — CT3-семейство его не имеет вообще, добавляется только для CT2, вызывается перед `initFrame()` в connect-последовательности (единственная точка, где меняется порядок шагов, а не просто байты).

### 5.2 Порядок подключения для CT2

Подтверждённая вживую последовательность (§B0–B1 в `ct-driver-plan.md`): `discovery → MTU → CCCD enable → handshake → ack → setDate → ack → [опционально check] → Init → ack → live push начинается`.

Изменить `onServicesDiscovered`/connect-конвейер: после CCCD-enable, если `isCt2()`, вставить шаг `sendHandshake()` перед привычным `sendInit()`. `check` — **не** часть автоматической последовательности (подтверждено: срабатывает только по явному пользовательскому запросу, см. `ct-driver-plan.md` §B3 «Живое наблюдение конечного автомата») — не включать в connect-конвейер, только в отдельный публичный метод типа `requestSelfTest()`.

### 5.3 Разбор нотификаций

В `when (opcode)` (`AnytimeBleManager.kt:2286`) добавить:

```kotlin
AnytimeConstants.RX_CT2_PUSH_GLUCOSE  -> handleCt2GlucoseFrame(data, historical = false)
AnytimeConstants.RX_CT2_PULL_RESPONSE -> handleCt2GlucoseFrame(data, historical = true)
AnytimeConstants.RX_CT2_HANDSHAKE_ACK -> handleHandshakeAck(data)   // только если сейчас ждём именно этот ack — CT3 не порождает этот код, коллизий нет
AnytimeConstants.RX_CT2_CHECK -> handleSelfTestResult(data)          // публикует Ct2CheckResult в UI-снапшот, не в основной поток чтения
```

`handleCt2GlucoseFrame` вызывает `parseCt2GlucoseRecord`, кладёт `record` в существующий `rawAlgorithmWindow` (тот же `TreeMap<Int, AnytimeRawRecord>`, никакой отдельной структуры не нужно — CT2 укладывается в существующий контракт `AnytimeRawRecord` без изменений схемы), и передаёт `push = !historical` в существующий `commitReading`/`shouldSkipStartupRoomImport` конвейер — эта часть переиспользуется без изменений, как и для CT3.

**Важно:** `push`/`historical` в существующем коде уже используется как параметр `AnytimeAlgorithm.compute(..., logNativeFallbackWarnings = push)` — семантика совпадает 1:1 с найденным на CT2 разделением `0x44`/`0x47`, дополнительной абстракции не требуется.

### 5.4 Бэкфилл

Существующий механизм пропусков (`historyStopBeforeId`, `rememberPendingCt5Gap`-аналог для не-CT5) уже описан в коде как «CT3/CT4 concern, needs contiguous 0..current raw prefix» (`AnytimeBleManager.kt:1294`) — CT2 подпадает под тот же путь один в один, серийный пул не нужен, `pullGlucoseFrame(id, count=1)` уже гейтит `count>1` только на `supportsLegacySeriesHistory()`, которая для CT2 должна возвращать `false` явно (у CT2 нет `0x22`).

**Подтверждено вживую:** бэкфилл идёт строго последовательно по одному id, интервал между TX и RX — доли секунды, 14 подряд идущих запросов без ошибок. Не нужен троттлинг сверх уже существующего `HISTORY_PULL_BATCH_DELAY_MS`, если он не окажется избыточно консервативным на практике — не менять без причины, существующий таймер CT3 подходит.

### 5.4.1 Гард на аномально большой бэкфилл — перенос принципа из Blueberry

У Blueberry для Poctech есть отдельная строка `"Poctech backfill finished - received abnormal number"` плюс фильтрация `"Skipping old record #"` / `"Min history date updated to"` (`apex-re-bb430/out/pp.txt`, кластер `pp+0x1d698`–`0x1d770`). Точный порог и логика недоступны — Blutter оставил тело этой ветки заглушкой (`AnonymousClosure ... size: -0x1`), восстановить можно только ручным дизасмом `libapp.so`, чего делать не будем (см. решение в `docs/ct-driver-plan.md` §4, открытый вопрос №4 фазы B). Переносим не число, а сам принцип.

**Проблема, которую это решает:** `historyStopBeforeId` ограничивает бэкфилл по диапазону id, но **не ограничивает его по количеству запросов**. У CT3/CT2.5 это не так больно — пакетный `0x22` тянет пачками. У CT2 бэкфилл строго по одной записи (`0x55`, подтверждено вживую — см. §5.4 выше), пакетного пула нет. Разрыв в тысячи id после многодневного отключения означает тысячи последовательных BLE round-trip'ов подряд, без предупреждения и без потолка.

**Решение:** добавить явный потолок на размер одного бэкфилла для CT2 — например, константа `CT2_MAX_BACKFILL_RECORDS` (обсудить конкретное число; ориентир — не больше, чем можно вытянуть за разумное время при наблюдавшихся ~0.3–0.7 с на round-trip, т.е. порядка нескольких сотен, не тысяч). При превышении:

* остановить бэкфилл на границе потолка, а не тянуть всё до конца;
* залогировать и отразить в `sensorDetailTelemetry` — пользователь должен видеть, что часть истории не подтянута, а не только тихо ждать;
* не блокировать live-поток — свежие push-записи должны продолжать приходить и обрабатываться независимо от того, докачан бэкфилл или нет.

Это тот же принцип, что и «Skipping old record #» у Blueberry — не пытаться восстановить историю, которая физически не критична, а честно ограничить объём и сообщить об этом, а не наращивать очередь молча.

**Приёмка:** синтетический тест — разрыв в N+1 записей (N = потолок) не уходит в N+1 отдельных TX; тест на реальный видимый статус в `sensorDetailTelemetry`, когда бэкфилл обрублен по потолку.

---

## 6. B4 — подключение калибровки (заглушка сейчас, реализация после A1)

Сейчас, до готовности A1:

```kotlin
override fun integratesUserCalibration(isRawMode: Boolean): Boolean = !isRawMode
override fun onUserCalibrationRevisionChanged(revision: Long) {
    // TODO(A1): пересчитать contiguous-окно через AnytimeCalibrator + affine-слой.
    // До готовности A1 — no-op, драйвер продолжает отдавать линейный/цепочечный расчёт без пользовательской калибровки.
}
```

После готовности A1 (affine-слой в `AnytimeCalibrator`/`AnytimeAlgorithm`) — просто подключить тот же путь, никакого отдельного кода калибровки для CT2 не пишется, весь смысл архитектурного решения «CT2 внутри `drivers/anytime/`» в том, что A1 реализуется один раз и работает на CT3 и CT2 одновременно.

### 6.1 Явное состояние «калибровки ещё не загружены» — перенос из Blueberry

У Blueberry три различимых состояния калибровки Poctech, каждое со своим текстом: `"Poctech: Calibrations not loaded yet, using raw values"`, `"Poctech: Loaded N"`, `"Poctech: Failed to load calibrations: "` (`pp+0x11338`–`0x11348`). Сейчас в плане A1 (`ct-driver-plan.md` §A1) хранилище точек калибровки описано, но состояние «точки ещё не подгружены из персиста» явно не смоделировано — расчёт просто «работает поверх накопленных точек», без разграничения «точек нет, потому что их и не было» от «точек нет, потому что ещё не успели загрузиться при холодном старте сессии».

Разница на практике: если сразу после восстановления сессии (`createManagedCallback`/`restoreFromPersistence`) прийти к пользователю с «нет калибровки» вместо «загрузка…», при наличии реальных сохранённых точек это даст видимый глазу скачок значения на графике в первые секунды после запуска — калиброванное значение вернётся только когда персист долистает до конца.

**Решение, привязать к A1:** три состояния явно, а не через `null`/пустой список:

```kotlin
sealed class Ct2CalibrationLoadState {
    object NotYetLoaded : Ct2CalibrationLoadState()          // "using raw values"
    data class Loaded(val pointCount: Int) : Ct2CalibrationLoadState()
    data class FailedToLoad(val reason: String) : Ct2CalibrationLoadState()
}
```

Пока состояние `NotYetLoaded` — не подставлять сырое значение как калиброванное молча (симметрично находке A4 про молчаливое переключение моделей: смена состояния должна быть видна, не быть тихой). После `Loaded(0)` — это уже не «не загружены», а «загружены, но пусто», обычный путь без калибровки. Различие важно только на узком окне холодного старта сессии, но именно там сейчас возможен необъяснимый скачок.

**Приёмка:** тест на порядок событий при восстановлении сессии — до завершения загрузки персиста показывается `NotYetLoaded`/raw, после — калиброванное значение подставляется без промежуточного мигания на неверном значении.

**Про алгоритмы 1/6/7/8 (CT2) vs 3 (CT3)** — открытый вопрос №8. Рабочее решение до появления опровергающих данных: **считать, что базовая цепочка (`K_BASE`, ramp `K_AUTO`, температурный множитель, ограничитель шага) идентична для всех `algorithmId`, различие — только в профильных константах** (`endNumber`, `initNumber`, возможно, `AUTO_RAMP_DENOM`/`WARMUP_RECORDS`, которые пока зашиты как константы 4800/480 в `AnytimeCalibrator.kt`, а должны, вероятно, стать полями `AnytimeProfile`, зависящими от `algorithmId`). Верифицируется тестом A6-подобным на данных `SN08` — если разбег с ожидаемыми показаниями системный, а не шумовой, значит гипотеза не верна и `AUTO_RAMP_DENOM`/`WARMUP_RECORDS` придётся параметризовать по алгоритму. Не блокирует написание B1-B3 — обнаружится на этапе тестирования B4.

---

## 7. Идентичность и реестр (B5) — код не нужен, проверено чтением источника

**Ревизия исходного плана.** Пункт ниже предполагал новый класс-адаптер; после чтения `AnytimeManagedSensorIdentityAdapter.kt` (135 строк, уже в дереве) выяснилось, что предположение было неверным — адаптер **уже полностью family-agnostic**:

* `mayBeAnytimeAlias()` матчит через `AnytimeConstants.resolveFamily(raw).family != Family.UNKNOWN` — это условие уже истинно для CT2, отдельного условия под CT2 не требуется.
* `resolveCanonicalSensorId`, `createManagedCallback`, `hasPersistedManagedRecord` и все остальные методы работают через `AnytimeRegistry`/`AnytimeConstants.canonicalSensorId` — ни одна функция не завязана на конкретное CT3-семейство.
* Провизорный префикс `ANY-` (`AnytimeConstants.deriveInitialSensorId`) используется как fallback только когда нет MAC/hex-адреса для канонического id — на практике редкий путь, и он уже одинаково общий для всех Anytime-семейств по архитектуре («CT2 живёт внутри `drivers/anytime/`»), не разрыв, а последовательное продолжение того же решения.

**Вывод: B5 не требует нового кода.** CT-14-сенсор, добавленный через `AnytimeRegistry.addSensor(...)` (как уже делает `Ct14SetupWizard.kt`), корректно распознаётся, персистится и восстанавливается существующим адаптером без единой строки правок. Регистрировать отдельный `Ct14ManagedSensorIdentityAdapter` в `ManagedSensorIdentityRegistry.all` не нужно — это было бы дублирование ради дублирования.

`ManagedSensorUiFamily` — переиспользовать `ANYTIME`, как и было решено изначально; отдельный enum-регистр не нужен, разница чисто в названии карточки сенсора.

**Приёмка B5 (пересмотрено):** не «написать и протестировать адаптер», а «подтвердить регрессионным тестом, что существующий `AnytimeManagedSensorIdentityAdapter` корректно резолвит CT2-сенсор» — один тест на `resolveCanonicalSensorId`/`hasPersistedManagedRecord` с CT2-подобным `sensorId`, без нового production-кода.

---

## 8. UI (B6)

`Ct14SetupWizard.kt`, `SensorTypePicker.kt`, строки — написаны (см. ревью в разговоре). Из плана осталась одна точечная правка:

### Сузить фильтр сканирования до CT2

Сейчас в `Ct14ScanStep` (`Ct14SetupWizard.kt`):

```kotlin
val nameLooksCt14 = nameCandidates.any(AnytimeConstants::isAnytimeDevice)  // ловит ЛЮБОЕ Anytime-семейство
val isLikelyCt14 = advertisesLegacy || nameLooksCt14
val familyEntry = AnytimeConstants.resolveFamily(bestName)                 // вычислен, но не используется для фильтра
```

`isAnytimeDevice` возвращает `true` для CT2/CT2.5/CT3/CT3_PLUS/CT3_YUWELL/CT3_ULTRASONIC/CT4/CT5 — реальный 4/4H (CT3) сенсор рядом попадёт в список экрана «CT-14 Setup», подписанный своим настоящим именем семейства. Это скопировано из `AnytimeSetupWizard.kt` (там та же картина в обратную сторону — не регрессия, существующий паттерн), но противоречит цели самого CT-14-визарда как отдельного, недвусмысленного входа.

**Правка:**

```kotlin
val familyEntry = AnytimeConstants.resolveFamily(bestName)   // вычислить раньше nameLooksCt14
val nameLooksCt14 = familyEntry.family == AnytimeConstants.Family.CT2
val isLikelyCt14 = advertisesLegacy || nameLooksCt14
```

`advertisesLegacy` (проверка `SERVICE_LEGACY_CT2` в scan record) уже узкая и правильная — трогать не нужно, только `nameLooksCt14`.

**Приёмка:** тест/ручная проверка — сенсор с именем `SN16` (CT3) или `Anytime` (CT5) не появляется в списке экрана CT-14 при выключенном «Показать все устройства»; `SN08` и любой другой CT2-префикс — появляется.

---

## 9. Тесты

### `AnytimeCt2FramesTests.kt`

Каждый тест — сравнение с реально захваченным кадром (не синтетика), источник — `ct-driver-plan.md` §B0–B1:

```kotlin
@Test fun handshakeMatchesLiveCapture() {
    val frame = AnytimeFrames.Builders.ct2Handshake("SN08402458")
    assertArrayEquals(hexToBytes("48534e303834303234353888"), frame)
}
@Test fun setDateMatchesLiveCapture() {
    val cal = calendarFor(2026, 9, 7, 20, 7, 24)
    assertArrayEquals(hexToBytes("5407ea090714071888"), AnytimeFrames.Builders.ct2SetDate(cal))
}
@Test fun checkMatchesLiveCapture() { /* 43 55 aa 42, статический */ }
@Test fun parseLivePushRecord() {
    val frame = AnytimeFrames.parseCt2GlucoseRecord(hexToBytes("4400001a0907143b008400151d64d7"))!!
    assertEquals(0, frame.record.glucoseId)
    assertEquals(13.2f, frame.record.iwNa, 0.01f)
    assertEquals(2.1f, frame.record.ibNa, 0.01f)
    assertEquals(29f, frame.record.temperatureC, 0.01f)
    assertFalse(frame.isHistorical)
    assertEquals(100, frame.batteryPercent)
}
@Test fun parseLivePullResponse() {
    // 47000e1a0907152a008100169efff2 — id=14, historical, батарея = null (не 0xFF как число!)
    val frame = AnytimeFrames.parseCt2GlucoseRecord(hexToBytes("47000e1a0907152a008100169efff2"))!!
    assertTrue(frame.isHistorical)
    assertNull(frame.batteryPercent)
}
@Test fun ibIwOrderNotSwapped() {
    // отдельный явный тест на порядок полей — самый опасный пункт всего протокола
    val frame = AnytimeFrames.parseCt2GlucoseRecord(hexToBytes("4400001a0907143b008400151d64d7"))!!
    assertNotEquals(frame.record.iwNa, frame.record.ibNa) // тривиально, но фиксирует что оба поля читаются раздельно
    assertEquals(13.2f, frame.record.iwNa, 0.01f)  // Iw ДО Ib в байтах — если поменять местами в парсере, тест упадёт
    assertEquals(2.1f, frame.record.ibNa, 0.01f)
}
@Test fun checksumRejectsCorruptedFrame() {
    val corrupted = hexToBytes("4400001a0907143b008400151d64d8") // последний байт испорчен
    assertNull(AnytimeFrames.parseCt2GlucoseRecord(corrupted))
}
```

Полный набор захваченных эталонов (24 записи бэкфилла + push id=0,17,18,24) — взять из таблицы в `ct-driver-plan.md` §«Бэкфилл и второй опкод записи», прогнать все разом как параметризованный тест на 3-минутный шаг id и корректность суммы.

### `AnytimeCt2ProfileTests.kt`

Таблица всех CT2-префиксов из `FAMILY_TABLE` → ожидаемые `warmupMinutes`/`ratedLifetimeDays`. `SN08` — отдельная явная проверка: `warmupMinutes=60`, `ratedLifetimeDays=14`.

### Регрессия

`AnytimeFramesTests`, `AnytimeCt5Tests`, `AnytimeProfileTests` — прогнать без изменений, ни один не должен быть отредактирован (критерий из `ct-driver-plan.md`).

---

## 10. Порядок работы

```
§2 (константы) ─▶ §3 (кадры) ─▶ §9 частично (тесты кадров, независимо от BLE)
                                        │
                    §4 (профиль) ─▶ §5 (BLE-автомат) ─▶ §9 (тесты профиля)
                                        │
                    §7 (identity) ─▶ §8 (UI) ─▶ ручная проверка на живом SN08402458
                                        │
                    §6 (calibration hook) ── ждёт A1, заглушка ставится сразу
```

Каждый шаг §2–§5 проверяем на уже захваченных живых данных — эталон есть на всё, включая полный 24-записный бэкфилл. Ручная проверка на реальном сенсоре (§8) — финальный шаг, не первый: к этому моменту протокол уже верифицирован тестами на записанных кадрах, живое устройство нужно только для UI/UX и для новых сценариев, которых пока не было в захвате (низкий заряд, unbind, low-power, конец срока службы через 14 суток).
