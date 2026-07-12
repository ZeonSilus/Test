# GetContact KeyFinder - Android Приложение

Android приложение для автоматического поиска ключей TOKEN и FINAL_KEY из GetContact.

## Требования

- Android 7.0+ (API 24)
- ROOT-доступ (для автоматического поиска)
- ИЛИ подключение к компьютеру через ADB

## Сборка APK

### Вариант 1: Через Android Studio

1. Откройте папку `android` в Android Studio
2. Дождитесь синхронизации Gradle
3. Build → Build Bundle(s) / APK(s) → Build APK(s)
4. APK будет в `app/build/outputs/apk/debug/app-debug.apk`

### Вариант 2: Через командную строку

```bash
cd android
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Как пользоваться

1. Установите APK на Android устройство
2. Нажмите "Поиск ключей"
3. Приложение найдёт файл и покажет ключи
4. Нажмите 📋 чтобы скопировать

## Если ROOT нет

### Через ADB (компьютер)

```bash
# Подключите телефон к компьютеру
# Включите "Отладка USB" в настройках разработчика

adb pull /data/data/app.source.getcontact/shared_prefs/GetContactSettingsPref.xml
```

### Через эмулятор

1. Установите Android Studio
2. Создайте виртуальное устройство с Google Play
3. Установите GetContact
4. Получите ROOT через эмулятор

## Структура проекта

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/getcontact/keyfinder/
│   │   │   └── MainActivity.kt
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── values/colors.xml
│   │   │   ├── values/themes.xml
│   │   │   └── xml/file_paths.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```