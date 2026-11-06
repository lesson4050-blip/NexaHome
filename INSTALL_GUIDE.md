# Руководство по установке и настройке Smart TV Launcher (INSTALL_GUIDE.md)

Данная инструкция описывает процесс установки APK на Android TV, Google TV и ТВ-приставки, а также настройку лаунчера в качестве основного системного экрана.

---

## 1. Расположение скомпилированного APK

Файл сборки находится в директории проекта:
```text
d:\TV\app\build\outputs\apk\debug\app-debug.apk
```

---

## 2. Способ 1: Установка через USB-флешку

1. Скопируйте `app-debug.apk` на отформатированную в FAT32/NTFS флешку.
2. Вставьте флешку в USB-порт телевизора или ТВ-бокса.
3. Откройте любой установленный файловый менеджер на ТВ (X-Plore, FX File Explorer, File Commander).
4. Запустите `app-debug.apk` и подтвердите установку (разрешите установку из неизвестных источников при запросе системы).
5. Нажмите кнопку **Home** на пульте — система предложит выбрать домашнее приложение. Выберите **Smart TV Launcher** и нажмите **«Всегда» (Always)**.

---

## 3. Способ 2: Установка по сети через ADB (Рекомендуемый способ)

### 3.1. Включение отладки по USB/сети на телевизоре
1. Перейдите в **Настройки (Settings) -> Настройки устройства (Device Preferences) -> Об устройстве (About)**.
2. Найдите пункт **Сборка ОС (Android TV OS Build)** и нажмите кнопку **OK** на пульте 7 раз, пока не появится надпись *«Вы стали разработчиком»*.
3. Вернитесь назад, откройте появившийся раздел **Для разработчиков (Developer Options)**.
4. Включите **Отладка по USB (USB Debugging)** и **Сетевая отладка (Network Debugging)**.
5. Узнайте IP-адрес телевизора в **Настройки -> Сеть и Интернет -> Ваша Wi-Fi сеть** (например: `192.168.1.150`).

### 3.2. Установка с компьютера
Откройте терминал PowerShell и выполните команды:

```powershell
# Подключение к телевизору
adb connect 192.168.1.150:5555

# Установка APK с сохранением данных
adb install -r "d:\TV\app\build\outputs\apk\debug\app-debug.apk"

# Назначение лаунчера главным экраном по умолчанию
adb shell cmd package set-home-activity "com.orbit.tv.launcher/.ui.screens.HomeActivity"
```

---

## 4. Обход блокировок сторонних лаунчеров на упрямых OEM

На некоторых устройствах (Google TV Chromecast, Xiaomi TV Stick, TCL) производитель блокирует выбор стороннего лаунчера через обычное меню.

### Для устройств Google TV (Chromecast with Google TV, современные Sony/TCL):
```powershell
# Отключение стандартного перегруженного рекламой интерфейса Google TV
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx

# Проверка: при нажатии Home будет открываться наш чистый лаунчер
```

### Для устройств Xiaomi / Mi TV / Mi Box (оболочка PatchWall):
```powershell
# Отключение рекламы и стокового лаунчера Xiaomi
adb shell pm disable-user --user 0 com.mitv.tvhome
adb shell pm disable-user --user 0 com.xiaomi.mitv.upgrade
```

---

## 5. Полный и безопасный откат к заводскому состоянию

Если потребуется вернуть стоковый системный интерфейс, просто включите обратно замороженные пакеты:

```powershell
# Для Google TV:
adb shell pm enable com.google.android.apps.tv.launcherx

# Для Xiaomi:
adb shell pm enable com.mitv.tvhome
```
Никаких системных файлов не удаляется и root-права не требуются. Все действия на 100% обратимы.
