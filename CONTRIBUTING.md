# 🤝 Внесок у проект

Дякуємо за інтерес до проекту! Ми раді будь-якому внеску - від виправлення помилок до додавання нових функцій.

## 🎯 Як допомогти

### 1. Повідомлення про баги

Знайшли баг? Створіть Issue з описом:

```
**Опис проблеми:**
Короткий та зрозумілий опис бага

**Кроки для відтворення:**
1. Відкрити додаток
2. Натиснути на '...'
3. Вибрати файл '...'
4. Помилка з'являється

**Очікувана поведінка:**
Що має статися

**Фактична поведінка:**
Що сталося насправді

**Скріншоти:**
Якщо можливо, додайте скріншоти

**Середовище:**
- Пристрій: [наприклад, Pixel 6]
- Версія Android: [наприклад, 14]
- Версія додатку: [наприклад, 1.0.0]

**Додаткова інформація:**
Будь-яка інша корисна інформація
```

### 2. Пропозиції нових функцій

Маєте ідею? Створіть Issue з описом:

```
**Опис функції:**
Що ви хочете додати?

**Проблема, яку це вирішує:**
Яку проблему це вирішить?

**Запропоноване рішення:**
Як це має працювати?

**Альтернативи:**
Чи розглядали ви інші варіанти?

**Додаткова інформація:**
Скріншоти, приклади з інших додатків, тощо
```

### 3. Внесок коду

#### Процес:

1. **Fork** репозиторій
2. **Clone** свій fork
   ```bash
   git clone https://github.com/ВАШ_USERNAME/DocumentReader.git
   cd DocumentReader
   ```

3. Створіть **branch** для вашої функції
   ```bash
   git checkout -b feature/amazing-feature
   ```

4. Зробіть ваші зміни
   
5. **Commit** змін
   ```bash
   git add .
   git commit -m "feat: додати підтримку нового формату"
   ```

6. **Push** в ваш fork
   ```bash
   git push origin feature/amazing-feature
   ```

7. Створіть **Pull Request**

## 📝 Стандарти коду

### Java Code Style

```java
// Класи: PascalCase
public class DocumentReader {
    
    // Константи: UPPER_SNAKE_CASE
    private static final String TAG = "DocumentReader";
    private static final int MAX_SIZE = 1024;
    
    // Змінні: camelCase
    private Context context;
    private String fileName;
    
    // Методи: camelCase
    public String readDocument(Uri uri, String fileName) {
        // Код...
    }
    
    // Private методи
    private String extractText(InputStream input) {
        // Код...
    }
}
```

### Коментарі

```java
/**
 * Читає документ з URI та повертає його вміст як текст
 * 
 * @param uri URI файлу для читання
 * @param fileName ім'я файлу (для визначення типу)
 * @return текстовий вміст документа
 * @throws Exception якщо файл не може бути прочитаний
 */
public String readDocument(Uri uri, String fileName) throws Exception {
    // Детальна логіка...
}

// Одно-рядкові коментарі для пояснення складної логіки
// TODO: додати підтримку кешування
```

### Форматування XML

```xml
<!-- activity_main.xml -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">
    
    <!-- Кнопка вибору файлу -->
    <Button
        android:id="@+id/btnSelectFile"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/select_file" />
        
</LinearLayout>
```

## 🔍 Тестування

### Перед створенням PR:

1. **Збірка проходить успішно**
   ```bash
   ./gradlew build
   ```

2. **Немає помилок лінтера**
   ```bash
   ./gradlew lint
   ```

3. **Тестування на реальному пристрої**
   - Протестуйте на різних версіях Android
   - Перевірте на різних розмірах екранів

4. **Тестування форматів**
   - Якщо додали підтримку формату, протестуйте різні файли
   - Перевірте граничні випадки (порожні файли, великі файли)

### Написання тестів

```java
// ExampleUnitTest.java
@Test
public void testFileExtensionExtraction() {
    DocumentReader reader = new DocumentReader(context);
    String extension = reader.getFileExtension("document.pdf");
    assertEquals("pdf", extension);
}

@Test
public void testSupportedFormat() {
    assertTrue(FileTypeDetector.isSupported("document.docx"));
    assertFalse(FileTypeDetector.isSupported("unknown.xyz"));
}
```

## 📋 Commit Messages

Використовуйте [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: додати підтримку формату XYZ
fix: виправити помилку читання PDF
docs: оновити README з новими форматами
style: виправити форматування коду
refactor: переписати метод readDocument
test: додати тести для FileTypeDetector
chore: оновити залежності
```

### Приклади:

```bash
# Нова функція
git commit -m "feat: додати підтримку ODT документів"

# Виправлення бага
git commit -m "fix: виправити crash при відкритті великих PDF"

# Документація
git commit -m "docs: додати приклади використання API"

# Рефакторинг
git commit -m "refactor: оптимізувати читання Excel файлів"
```

## 🎨 UI/UX Guidelines

### Material Design

- Використовуйте Material Components
- Дотримуйтесь Material Design guidelines
- Підтримуйте світлу та темну теми (у майбутніх версіях)

### Accessibility

- Додавайте contentDescription для іконок
- Підтримуйте TalkBack
- Використовуйте читабельні розміри шрифтів (мін. 14sp)

```xml
<ImageButton
    android:id="@+id/btnShare"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:src="@drawable/ic_share"
    android:contentDescription="@string/share_document" />
```

### Strings

Всі тексти в `strings.xml`:

```xml
<resources>
    <string name="app_name">Читач Документів</string>
    <string name="select_document">Вибрати документ</string>
    <!-- Не хардкодьте текст в layout або Java файлах -->
</resources>
```

## 🌐 Локалізація

Додавання нової мови:

1. Створіть папку `values-LANG/`
   ```
   res/
   ├── values/          # Українська (за замовчуванням)
   ├── values-en/       # English
   └── values-uk/       # Українська (явно)
   ```

2. Перекладіть `strings.xml`

3. Перевірте всі екрани

## 📦 Додавання залежностей

### Нова бібліотека:

1. Додайте в `app/build.gradle`
   ```gradle
   dependencies {
       implementation 'com.example:library:1.0.0'
   }
   ```

2. Оновіть `packagingOptions` якщо потрібно

3. Додайте ProGuard правила

4. Оновіть README з інформацією про бібліотеку

5. Перевірте ліцензію бібліотеки

## 🔐 Безпека

### Не додавайте в git:

- API ключі
- Паролі
- Keystore файли
- Персональні дані

### Використовуйте:

```properties
# local.properties (не в git)
api.key=YOUR_KEY_HERE
```

```java
// У коді
Properties properties = new Properties();
properties.load(context.getAssets().open("local.properties"));
String apiKey = properties.getProperty("api.key");
```

## 📊 Додавання підтримки нового формату

### Checklist:

- [ ] Додати бібліотеку в `build.gradle`
- [ ] Додати case в `DocumentReader.readDocument()`
- [ ] Реалізувати метод `readНовийФормат()`
- [ ] Додати формат в `FileTypeDetector`
- [ ] Додати MIME тип в `MainActivity.openFilePicker()`
- [ ] Протестувати на різних файлах
- [ ] Додати в документацію (README.md)
- [ ] Додати в CHANGELOG.md

### Приклад:

```java
// 1. В DocumentReader.java
case "новий_формат":
    return readНовийФормат(uri);

private String readНовийФормат(Uri uri) throws Exception {
    StringBuilder content = new StringBuilder();
    
    try (InputStream inputStream = 
         context.getContentResolver().openInputStream(uri)) {
        // Використайте відповідну бібліотеку
        // Прочитайте та конвертуйте в текст
    }
    
    return content.toString();
}

// 2. В FileTypeDetector.java
extensionToCategory.put("новий_формат", FileCategory.DOCUMENT);
```

## 🐛 Debugging

### Корисні команди:

```bash
# Логи в реальному часі
adb logcat | grep "DocumentReader"

# Очистка build
./gradlew clean

# Перевірка залежностей
./gradlew dependencies

# Аналіз розміру APK
./gradlew assembleRelease
ls -lh app/build/outputs/apk/release/
```

### Логування:

```java
private static final String TAG = "DocumentReader";

Log.d(TAG, "Читання файлу: " + fileName);
Log.e(TAG, "Помилка читання", exception);
Log.w(TAG, "Велий розмір файлу: " + fileSize);
```

## ✅ Pull Request Checklist

Перед створенням PR переконайтеся:

- [ ] Код скомпільовано без помилок
- [ ] Немає warnings від лінтера
- [ ] Додано/оновлено коментарі
- [ ] Протестовано на реальному пристрої
- [ ] Оновлено документацію (якщо потрібно)
- [ ] Додано в CHANGELOG.md
- [ ] Commit messages відповідають стандарту
- [ ] Код відформатовано
- [ ] Немає hardcoded текстів
- [ ] Додано strings.xml для нових текстів

## 📞 Питання?

- Створіть Issue з міткою "question"
- Напишіть email: [ваш email]
- Обговорення на GitHub Discussions

## 📄 Ліцензія

Робляючи внесок, ви погоджуєтесь, що ваш код буде ліцензовано під тією ж ліцензією, що і проект.

---

**Дякуємо за ваш внесок! 🎉**

Разом ми зробимо цей додаток кращим для всіх! 🚀

