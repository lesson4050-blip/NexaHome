# Архитектурные решения и технический анализ платформы (DECISIONS.md)

Документ фиксирует результаты Фазы 1 (Исследование) и утвержденные технические решения для разработки нативного Android TV лаунчера.

---

## 1. Выбор UI-стека: Классические Android Views против Jetpack Compose

### Решение
**Строго классический стек: Kotlin + Android Views + RecyclerView + ViewBinding.**
Jetpack Compose for TV исключен из ядра лаунчера.

### Техническое обоснование (Ориентир на 1 ГБ RAM)
1. **Потребление Heap Memory**:
   - Compose Runtime + Compiler + androidx.tv.material3 требуют 85–120 МБ heap только на граф рекомпозиции и инициализацию подсистем.
   - Классический View hierarchy лаунчера на `RecyclerView` укладывается в 28–42 МБ heap. На устройствах с 1 ГБ RAM (доступный `largeHeap` ~192–256 МБ, стандартный heap ~64–128 МБ) Compose оставляет критически мало памяти для декодирования постеров.
2. **Низкоуровневая компиляция шейдеров и Jank**:
   - На SoC начального уровня (Amlogic S905W/W2, Allwinner H313/H616, Rockchip RK3228A/RK3318) рендерер Skia в Compose вызывает ощутимый троттлинг и дропы кадров (до 15–25 FPS) при первом открытии рядов и смене фокуса.
   - Оптимизированный `RecyclerView` с общим `RecycledViewPool` между горизонтальными рядами дает стабильные 60 FPS на устаревших GPU (Mali-450 / Mali-G31).
3. **Low Memory Killer (LMK) и возврат из приложений**:
   - При запуске ресурсоемких приложений (YouTube 4K, Kodi, браузеры) система с 1 ГБ RAM принудительно убивает фоновые процессы с высоким RSS (Resident Set Size).
   - Лаунчер на Compose будет убиваться LMK практически при каждом переключении задач, вызывая медленный перезапуск (3–4 сек). View-based лаунчер восстанавливается за 0.3–0.6 сек.
4. **Опыт Projectivy Launcher**:
   - Технический эталон Projectivy построен именно на View-компонентах и легковесных кастомных контейнерах.

---

## 2. Анализ эталона Projectivy Launcher

Публичные инженерные решения, принятые за стандарт:
* **Кэширование фона**: Фоновое изображение декодируется в фоновом потоке (`Dispatchers.IO`) с принудительным `inSampleSize` и цветовым форматом `Bitmap.Config.RGB_565` (экономия 50% видеопамяти по сравнению с `ARGB_8888`).
* **Адаптивный фокус (FocusEngine)**: Фокус не полагается на стандартный системный `focusableInTouchMode`. Реализуется кастомный фокус-слой с поддержкой неонового свечения (Cyan `#00b4d8`), масштабирования карточек (1.05x–1.08x) и плавной анимации без сдвига верстки соседних элементов (через `elevation` и `bringChildToFront`).
* **Input-изоляция**: Разделение обработки физического D-Pad пульта и курсора аэромыши (G10S, пульт-указка).
* **Служба перехвата кнопок (Key Interceptor)**: Опциональный `AccessibilityService` для устройств, где OEM принудительно глушит стандартную кнопку Home или где требуется назначить быстрые действия на цветные/мультимедиа кнопки.

---

## 3. Ограничения платформы Android TV / Google TV и специфика OEM

| Сценарий / Компонент | Стандартный Android TV | Google TV / Сертифицированные OEM | Китайские боксы без сертификации (AOSP TV) | Стратегия лаунчера |
| :--- | :--- | :--- | :--- | :--- |
| **Выбор Home Launcher** | `Intent(ACTION_MAIN, CATEGORY_HOME)` стандартно открывает системный диалог выбора. | Выбор стороннего Home скрыт в меню; системный лаунчер защищен. | Свободная установка, выбор через Настройки -> Приложения -> Главный экран. | 1. Официальный интент выбора Home.<br>2. Fallback: инструкция для пользователя.<br>3. Опциональный ADB-метод (`cmd package disable-user com.google.android.apps.tv.launcherx`). |
| **Автозапуск после Boot** | `BOOT_COMPLETED` приемник + запуск Home Activity. | `BOOT_COMPLETED` разрешен. | `BOOT_COMPLETED` работает всегда. | Декларация Home Activity в манифесте + `RECEIVE_BOOT_COMPLETED` fallback. |
| **Системный VPN** | `android.net.vpn.SETTINGS` открывает системный экран. | `android.net.vpn.SETTINGS` поддерживается. | Интент часто вырезан прошивкой (ActivityNotFoundException). | Capability detection: проверка наличия Activity перед стартом. Если нет — fallback на `ACTION_WIRELESS_SETTINGS` либо запуск установленного VPN-клиента. |
| **Беспроводная трансляция (Cast)** | Встроенный Google Cast Receiver (фоновый сервис). | Встроенный Google Cast Receiver. | Google Cast отсутствует; Miracast / Wi-Fi Display реализован вендорным приложением. | Не обещать AirPlay/Cast внутри лаунчера. Экран Cast выступает диспетчером: проверка установленных ресиверов, системного Miracast и отображение понятного статуса. |
| **Управление питанием** | `ACTION_REQUEST_SHUTDOWN` требует системных прав. | Требует системных прав. | Зависит от прошивки. | При клике на Power: открытие системного Power Menu через `KeyEvent.KEYCODE_POWER`, переход в Sleep (`goToSleep`) при наличии прав, либо экран таймера сна. |

---

## 4. Архитектура подсистем и контракты

### 4.1. Доступ к установленным приложениям (`PackageManagerRepository`)
* Опрос `PackageManager.queryIntentActivities()` по категориям:
  * `Intent.CATEGORY_LEANBACK_LAUNCHER` (первичный список ТВ-приложений).
  * `Intent.CATEGORY_LAUNCHER` (мобильные приложения без ТВ-баннера, отображаются с корректной fallback-карточкой).
* Принудительная фильтрация системных невидимых пакетов.
* Реагирование на системные широковещательные события:
  * `ACTION_PACKAGE_ADDED`
  * `ACTION_PACKAGE_REMOVED`
  * `ACTION_PACKAGE_CHANGED`

### 4.2. Каталог медиа и рекомендации (`MediaCatalogProvider`)
* В ядро лаунчера **не встраиваются** нелегальные парсеры или пиратские базы.
* Метаданные (популярные фильмы и сериалы недели) подтягиваются через абстракцию `MediaCatalogProvider` (TMDb / Trakt REST API с локальным дисковым SQLite кэшем).
* Работа в оффлайн-режиме: при отсутствии сети Home отображает кэшированные постеры или структурированный Offline State без крашей.
* Клик по фильму открывает внутренний экран деталей `MediaDetailActivity` (описание, рейтинг, трейлер YouTube, кнопки запуска через установленные стриминговые клиенты по зарегистрированным deeplinks).

### 4.3. IPTV-модуль (`LegalIptvProvider`)
* Потоковый (streaming) парсер M3U8 плейлистов: чтение строк без загрузки гигабайтных файлов целиком в RAM.
* Хранение списка каналов и EPG в локальной базе `Room SQLite` с FTS4 индексацией для моментального поиска с пульта.
* Плеер: `androidx.media3:media3-exoplayer` (HLS, MPEG-TS, RTSP) с аппаратным ускорением декодирования.
* Изоляция: сбой воспроизведения или недоступность потока не блокирует Home screen.

### 4.4. VPN-виджет (`VpnCapabilityManager`)
* Статус VPN отслеживается через системный `ConnectivityManager.NetworkCallback` (мониторинг `NetworkCapabilities.TRANSPORT_VPN`).
* Топбарный виджет отображает:
  * `CONNECTED` (зеленый индикатор + имя интерфейса / страна узла).
  * `DISCONNECTED` (серый индикатор + "OFF").
* Клик по виджету:
  * Если установлен сторонний VPN-клиент (выбранный в настройках лаунчера) -> запуск приложения.
  * Иначе -> попытка открыть системный `android.net.vpn.SETTINGS`.
  * При отсутствии интента -> открытие настроек беспроводных сетей.

### 4.5. Управление профилями (`ProfileManager`)
* Поддержка до 3 взрослых и 2 детских профилей.
* Хранение настроек: легковесный локальный `DataStore / EncryptedSharedPreferences`.
* Детский профиль:
  * Блокировка выхода 4-значным PIN-кодом.
  * Фильтрация контента с возрастным рейтингом > 12+.
  * Скрытие системных настроек и IPTV каналов для взрослых.

### 4.6. Dual Input & Focus Engine (`TvFocusEngine`)
* Модель ввода с двумя взаимоисключающими состояниями:
  * `MODE_DPAD`: классическая кнопочная навигация по графу `nextFocusUp/Down/Left/Right`. Плавный автоскролл `RecyclerView` к фокусному элементу с центрированием.
  * `MODE_POINTER`: движение курсора мыши временно скрывает D-Pad подсветку. При наведении на кликабельный элемент курсор выделяет карточку.
  * При первом нажатии любой клавиши D-Pad система мгновенно восстанавливает фокус на текущем видимом элементе и выключает мышиное выделение.

---

## 5. Пакетная структура проекта

```text
app/src/main/java/com/orbit/tv/
├── platform/               // Низкоуровневые системные адаптеры Android
│   ├── home/               // Обработка Home Intent, BootReceiver, проверка дефолтного лаунчера
│   ├── package_manager/    // Чтение установленных APK, иконок, баннеров
│   ├── remote_input/       // Обработка KeyEvent пульта, KeyInterceptorService
│   └── device_capabilities/// Проверка наличия VPN Intent, Cast, Miracast, Bluetooth
├── core/                   // Общесистемная бизнес-логика
│   ├── navigation/         // Контроллер переходов между экранами лаунчера
│   ├── focus/              // FocusEngine, D-Pad/Air-Mouse State Machine
│   ├── storage/            // Room Database, Datastore (настройки, кэш, профили)
│   ├── performance/        // Управление памятью, тримминг кэша, BackgroundMemoryManager
│   └── deeplink/           // Резолвер внешних ссылок и интентов запуска
├── features/               // Функциональные модули
│   ├── home/               // Презентер и логика главного экрана
│   ├── apps/               // Сетка всех приложений (Apps Hub)
│   ├── recommendations/    // Провайдер метаданных фильмов и сериалов
│   ├── media/              // Экран деталей медиа (Detail Template)
│   ├── iptv/               // Загрузка M3U, EPG, Media3 плеер
│   ├── vpn/                // Мониторинг NetworkCallback, диспетчер VPN
│   ├── cast/               // Хаб беспроводных дисплеев
│   ├── profiles/           // Менеджер профилей, PIN-экран
│   ├── games/              // Хаб облачных и нативных игр
│   └── settings/           // Настройки лаунчера, тем, переназначения кнопок
└── ui/                     // Визуальный слой (Views, Custom Layouts, Adapters)
    ├── tokens/             // Цвета, размеры, интервалы Safe-Zone
    ├── components/         // Sidebar, StatusHeader, AppCardView, MediaCardView
    ├── screens/            // HomeActivity, AppsHubActivity, IptvActivity
    └── overlays/           // Диалоги выбора, PIN-оверлей, Quick Menu
```

---

## 6. Метрики производительности (Target Benchmarks)

* **Cold Start Time**: ≤ 1.2 с на Android TV 9.0 (1 ГБ RAM).
* **RAM Footprint (Home Screen)**: ≤ 45 МБ PSS в режиме покоя.
* **FPS при скролле рядов**: Стабильные 60 FPS (90 FPS при поддержке панели).
* **Crash-free Rate**: 99.9% при вызовах сторонних Intent и возврате по кнопке Home.

---

## 7. Каталог фильмов и сериалов: Интеграция Cinemeta и диалог деталей

### Архитектурное решение
1. **Публичный мета-каталог Cinemeta**:
   - `https://v3-cinemeta.strem.io/catalog/movie/top.json`
   - `https://v3-cinemeta.strem.io/catalog/series/top.json`
   - Без API-ключей, без rate limits, актуальные рейтинги IMDb, жанры и синопсисы на 2026 год.
2. **Отказоустойчивость и кэширование**:
   - Сетевой запрос через `HttpURLConnection` в фоновом потоке `Dispatchers.IO` (таймаут 6 с).
   - Автоматическое кэширование JSON в `context.cacheDir/cached_movies.json` и `cached_series.json`.
   - Кураторский оффлайн-бейзлайн для мгновенного первого запуска без интернета.
3. **Легковесный диалог деталей (`MediaDetailDialog`)**:
   - Leanback D-pad фокус на кнопках "Смотреть трейлер" и "Искать в приложениях".
   - Вызов нативных интентов `Intent.ACTION_VIEW` для поиска трейлеров на YouTube и контента в установленных приложениях.
   - Очистка ресурсов и `RecycledViewPool` между рядами фильмов и сериалов для жесткого бюджета 1 ГБ RAM.

---

## 8. Игровой хаб и беспроводная трансляция (Gaming & Cast Hubs)

### 8.1. Gaming & Cloud Arcade (`GamesActivity`)
- **Диспетчеризация облачного гейминга**:
  - GeForce NOW (`com.nvidia.geforcenow`).
  - Xbox Cloud Gaming / Game Pass (`com.gamepass`).
  - PlayStation Remote Play (`com.playstation.remoteplay`).
  - Прямой запуск при наличии пакета на устройстве; автоматический переход в Android TV Play Store (`market://details?id=...`) при отсутствии.
- **Интеграция с локальными играми**:
  - Фильтрация пакетов с атрибутом `isGame = true`.
  - Показ понятного Empty State при отсутствии установленных нативных игр.
  - Поддержка меню деблоата и закрепления (Long click -> `AppOptionsDialog`).
- **Защита от сброса лаунчера**:
  - `OnBackPressedCallback` безопасно возвращает фокус на главный экран без падения в черный экран.

### 8.2. Wireless Cast & Mirroring Hub (`CastActivity`)
- **Поддержка протоколов без фиктивных обещаний**:
  - **Miracast / Wi-Fi Display**: вызов `android.settings.WIFI_DISPLAY_SETTINGS` и системного `Settings.ACTION_CAST_SETTINGS`, плюс детекция OEM-пакетов вендоров (Amlogic, Rockchip, Allwinner, Actions).
  - **Chromecast Built-in**: проверка и вызов системного Cast экрана.
  - **Apple AirPlay 2**: обнаружение установленных приемников (`AirScreen`, `AirReceiver`) с автоматическим предложением установки AirScreen из Google Play при их отсутствии.


