# ⚡ Швидкий довідник команд

## 🔨 Gradle команди

### Збірка

```bash
# Очистка проекту
./gradlew clean

# Збірка debug версії
./gradlew assembleDebug

# Збірка release версії
./gradlew assembleRelease

# Збірка всіх варіантів
./gradlew build

# Збірка App Bundle (для Play Store)
./gradlew bundleRelease
```

### Встановлення

```bash
# Встановити debug на підключений пристрій
./gradlew installDebug

# Встановити release
./gradlew installRelease

# Встановити і запустити
./gradlew installDebug && adb shell am start -n com.ccs.documentreader/.MainActivity
```

### Тестування

```bash
# Запустити unit тести
./gradlew test

# Запустити instrumented тести
./gradlew connectedAndroidTest

# Lint перевірка
./gradlew lint

# Lint з звітом
./gradlew lint
# Звіт: app/build/reports/lint-results.html
```

### Залежності

```bash
# Показати дерево залежностей
./gradlew dependencies

# Перевірити оновлення залежностей
./gradlew dependencyUpdates

# Синхронізувати проект
./gradlew sync
```

### Очистка

```bash
# Очистити build папки
./gradlew clean

# Очистити Gradle кеш
rm -rf ~/.gradle/caches/

# Очистити проект повністю
./gradlew clean
rm -rf .gradle build
rm -rf app/.gradle app/build
```

## 📱 ADB команди

### Базові

```bash
# Список підключених пристроїв
adb devices

# Перезапустити ADB сервер
adb kill-server
adb start-server

# Підключитися до конкретного пристрою
adb -s DEVICE_ID shell
```

### Встановлення APK

```bash
# Встановити APK
adb install app-debug.apk

# Встановити з заміною
adb install -r app-debug.apk

# Видалити додаток
adb uninstall com.ccs.documentreader

# Переустановити
adb uninstall com.ccs.documentreader
adb install app-debug.apk
```

### Файли

```bash
# Завантажити файл на пристрій
adb push test.pdf /sdcard/Download/

# Скачати файл з пристрою
adb pull /sdcard/Download/test.pdf ./

# Список файлів
adb shell ls /sdcard/Download/

# Видалити файл
adb shell rm /sdcard/Download/test.pdf
```

### Логи

```bash
# Всі логи
adb logcat

# Логи додатку
adb logcat | grep "DocumentReader"

# Очистити логи
adb logcat -c

# Зберегти логи у файл
adb logcat > logs.txt

# Логи з часом
adb logcat -v time

# Логи тільки помилок
adb logcat *:E
```

### Додаток

```bash
# Запустити додаток
adb shell am start -n com.ccs.documentreader/.MainActivity

# Зупинити додаток
adb shell am force-stop com.ccs.documentreader

# Очистити дані додатку
adb shell pm clear com.ccs.documentreader

# Інформація про додаток
adb shell dumpsys package com.ccs.documentreader

# Шлях до APK
adb shell pm path com.ccs.documentreader
```

### Дозволи

```bash
# Надати всі дозволи
adb shell pm grant com.ccs.documentreader android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.ccs.documentreader android.permission.READ_MEDIA_DOCUMENTS

# Відкликати дозвіл
adb shell pm revoke com.ccs.documentreader android.permission.READ_EXTERNAL_STORAGE

# Список дозволів
adb shell dumpsys package com.ccs.documentreader | grep permission
```

### Скріншоти

```bash
# Зробити скріншот
adb shell screencap /sdcard/screen.png
adb pull /sdcard/screen.png

# Запис екрану
adb shell screenrecord /sdcard/demo.mp4
# Ctrl+C для зупинки
adb pull /sdcard/demo.mp4
```

## 🔍 Діагностика

### Інформація про пристрій

```bash
# Версія Android
adb shell getprop ro.build.version.release

# API рівень
adb shell getprop ro.build.version.sdk

# Модель пристрою
adb shell getprop ro.product.model

# Інформація про CPU
adb shell cat /proc/cpuinfo

# Вільна пам'ять
adb shell cat /proc/meminfo

# Використання батареї
adb shell dumpsys battery
```

### Продуктивність

```bash
# Використання CPU додатком
adb shell top | grep documentreader

# Використання пам'яті
adb shell dumpsys meminfo com.ccs.documentreader

# FPS та продуктивність
adb shell dumpsys gfxinfo com.ccs.documentreader
```

## 🛠️ Git команди

### Базові

```bash
# Статус
git status

# Додати файли
git add .
git add specific_file.java

# Commit
git commit -m "feat: додати нову функцію"

# Push
git push origin main

# Pull
git pull origin main
```

### Гілки

```bash
# Створити гілку
git checkout -b feature/new-feature

# Переключитися на гілку
git checkout main

# Список гілок
git branch

# Видалити гілку
git branch -d feature/old-feature

# Злити гілку
git merge feature/new-feature
```

### Історія

```bash
# Історія комітів
git log

# Компактна історія
git log --oneline

# Графічна історія
git log --graph --oneline --all

# Зміни у файлі
git log -p filename.java
```

### Скасування змін

```bash
# Скасувати зміни у файлі
git checkout -- filename.java

# Скасувати staged зміни
git reset HEAD filename.java

# Скасувати останній commit (зберегти зміни)
git reset --soft HEAD~1

# Скасувати останній commit (видалити зміни)
git reset --hard HEAD~1
```

## 📦 Корисні скрипти

### Швидка збірка та встановлення

```bash
# quick_install.sh
#!/bin/bash
echo "🔨 Збірка..."
./gradlew assembleDebug && \
echo "📱 Встановлення..." && \
adb install -r app/build/outputs/apk/debug/app-debug.apk && \
echo "🚀 Запуск..." && \
adb shell am start -n com.ccs.documentreader/.MainActivity && \
echo "✅ Готово!"
```

### Очистка та перезбірка

```bash
# rebuild.sh
#!/bin/bash
echo "🧹 Очистка..."
./gradlew clean
echo "🔨 Збірка..."
./gradlew assembleDebug
echo "✅ Готово!"
```

### Збір логів

```bash
# collect_logs.sh
#!/bin/bash
adb logcat -c
echo "📝 Збір логів (Ctrl+C для зупинки)..."
adb logcat | grep "DocumentReader" > logs_$(date +%Y%m%d_%H%M%S).txt
```

### Тестування з різними файлами

```bash
# test_formats.sh
#!/bin/bash
echo "📄 Тестування форматів..."

# Створити тестові файли
echo "Тестовий текст" > test.txt
echo '{"test": "json"}' > test.json
echo "name,age\nJohn,25" > test.csv

# Завантажити на пристрій
adb push test.txt /sdcard/Download/
adb push test.json /sdcard/Download/
adb push test.csv /sdcard/Download/

echo "✅ Файли завантажено в Download/"
```

## 🎯 Android Studio shortcuts

### macOS

```
Cmd + N          - Новий файл
Cmd + O          - Відкрити клас
Cmd + Shift + O  - Відкрити файл
Cmd + B          - Перейти до визначення
Cmd + /          - Коментувати рядок
Cmd + Shift + /  - Блоковий коментар
Cmd + D          - Дублювати рядок
Cmd + Y          - Видалити рядок
Cmd + R          - Замінити
Cmd + Shift + R  - Замінити у всіх файлах
Shift + F10      - Run
Shift + F9       - Debug
```

### Windows/Linux

```
Alt + Insert     - Новий файл
Ctrl + N         - Відкрити клас
Ctrl + Shift + N - Відкрити файл
Ctrl + B         - Перейти до визначення
Ctrl + /         - Коментувати рядок
Ctrl + Shift + / - Блоковий коментар
Ctrl + D         - Дублювати рядок
Ctrl + Y         - Видалити рядок
Ctrl + R         - Замінити
Ctrl + Shift + R - Замінити у всіх файлах
Shift + F10      - Run
Shift + F9       - Debug
```

## 📊 Аналіз проекту

### Розмір APK

```bash
# Зібрати release APK
./gradlew assembleRelease

# Розмір APK
ls -lh app/build/outputs/apk/release/app-release.apk

# Детальний аналіз в Android Studio
# Build → Analyze APK → select app-release.apk
```

### Кількість коду

```bash
# Загальна кількість рядків
find app/src/main/java -name "*.java" | xargs wc -l

# По файлах
wc -l app/src/main/java/com/ccs/documentreader/*.java

# Тільки код (без коментарів та порожніх рядків)
find app/src/main/java -name "*.java" -exec grep -v '^\s*//' {} \; -exec grep -v '^\s*$' {} \; | wc -l
```

### Статистика проекту

```bash
# Кількість Java файлів
find app/src/main/java -name "*.java" | wc -l

# Кількість XML файлів
find app/src/main/res -name "*.xml" | wc -l

# Розмір проекту
du -sh .

# Розмір build папки
du -sh app/build
```

---

## 💡 Швидкі команди для розробки

```bash
# Повний цикл розробки
./gradlew clean assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.ccs.documentreader/.MainActivity

# Тестування та встановлення
./gradlew test lint assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# Моніторинг логів під час роботи
adb logcat -c && adb logcat | grep -E "DocumentReader|AndroidRuntime"
```

---

**Збережіть цей файл для швидкого доступу до команд! 📚**

