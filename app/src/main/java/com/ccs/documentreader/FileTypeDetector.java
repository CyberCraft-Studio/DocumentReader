package com.ccs.documentreader;

import java.util.HashMap;
import java.util.Map;

/**
 * Клас для визначення типів файлів та їх категорій
 */
public class FileTypeDetector {

    public enum FileCategory {
        DOCUMENT,
        SPREADSHEET,
        PRESENTATION,
        PDF,
        EBOOK,
        ARCHIVE,
        DATA,
        TEXT,
        UNKNOWN
    }

    private static final Map<String, FileCategory> extensionToCategory = new HashMap<>();

    static {
        // Документи
        extensionToCategory.put("doc", FileCategory.DOCUMENT);
        extensionToCategory.put("docx", FileCategory.DOCUMENT);
        extensionToCategory.put("docs", FileCategory.DOCUMENT);
        extensionToCategory.put("docm", FileCategory.DOCUMENT);
        extensionToCategory.put("odt", FileCategory.DOCUMENT);
        extensionToCategory.put("rtf", FileCategory.DOCUMENT);
        extensionToCategory.put("wps", FileCategory.DOCUMENT);
        extensionToCategory.put("pages", FileCategory.DOCUMENT);

        // Таблиці
        extensionToCategory.put("xls", FileCategory.SPREADSHEET);
        extensionToCategory.put("xlsx", FileCategory.SPREADSHEET);
        extensionToCategory.put("xlsm", FileCategory.SPREADSHEET);
        extensionToCategory.put("ods", FileCategory.SPREADSHEET);
        extensionToCategory.put("csv", FileCategory.SPREADSHEET);
        extensionToCategory.put("tsv", FileCategory.SPREADSHEET);
        extensionToCategory.put("dif", FileCategory.SPREADSHEET);
        extensionToCategory.put("slk", FileCategory.SPREADSHEET);

        // Презентації
        extensionToCategory.put("ppt", FileCategory.PRESENTATION);
        extensionToCategory.put("pptx", FileCategory.PRESENTATION);
        extensionToCategory.put("pptm", FileCategory.PRESENTATION);
        extensionToCategory.put("odp", FileCategory.PRESENTATION);
        extensionToCategory.put("key", FileCategory.PRESENTATION);

        // PDF
        extensionToCategory.put("pdf", FileCategory.PDF);

        // Електронні книги
        extensionToCategory.put("epub", FileCategory.EBOOK);
        extensionToCategory.put("mobi", FileCategory.EBOOK);
        extensionToCategory.put("azw", FileCategory.EBOOK);
        extensionToCategory.put("fb2", FileCategory.EBOOK);

        // Архіви
        extensionToCategory.put("zip", FileCategory.ARCHIVE);
        extensionToCategory.put("rar", FileCategory.ARCHIVE);
        extensionToCategory.put("7z", FileCategory.ARCHIVE);
        extensionToCategory.put("tar", FileCategory.ARCHIVE);
        extensionToCategory.put("gz", FileCategory.ARCHIVE);
        extensionToCategory.put("xz", FileCategory.ARCHIVE);
        extensionToCategory.put("iso", FileCategory.ARCHIVE);

        // Дані
        extensionToCategory.put("json", FileCategory.DATA);
        extensionToCategory.put("xml", FileCategory.DATA);
        extensionToCategory.put("yaml", FileCategory.DATA);
        extensionToCategory.put("yml", FileCategory.DATA);
        extensionToCategory.put("ini", FileCategory.DATA);
        extensionToCategory.put("bib", FileCategory.DATA);

        // Текст
        extensionToCategory.put("txt", FileCategory.TEXT);
        extensionToCategory.put("md", FileCategory.TEXT);
        extensionToCategory.put("tex", FileCategory.TEXT);
        extensionToCategory.put("rmd", FileCategory.TEXT);
        extensionToCategory.put("java", FileCategory.TEXT);
    }

    /**
     * Визначає категорію файлу за його розширенням
     */
    public static FileCategory getFileCategory(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        return extensionToCategory.getOrDefault(extension, FileCategory.UNKNOWN);
    }

    /**
     * Перевіряє, чи підтримується даний формат
     */
    public static boolean isSupported(String fileName) {
        FileCategory category = getFileCategory(fileName);
        return category != FileCategory.UNKNOWN;
    }

    /**
     * Повертає опис категорії українською
     */
    public static String getCategoryDescription(FileCategory category) {
        switch (category) {
            case DOCUMENT:
                return "📄 Текстовий документ";
            case SPREADSHEET:
                return "📊 Електронна таблиця";
            case PRESENTATION:
                return "📽️ Презентація";
            case PDF:
                return "📕 PDF документ";
            case EBOOK:
                return "📚 Електронна книга";
            case ARCHIVE:
                return "🗄️ Архів";
            case DATA:
                return "💾 Файл даних";
            case TEXT:
                return "📝 Текстовий файл";
            default:
                return "❓ Невідомий тип";
        }
    }

    /**
     * Витягує розширення файлу
     */
    private static String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return "";
    }

    /**
     * Повертає список всіх підтримуваних розширень
     */
    public static String[] getSupportedExtensions() {
        return extensionToCategory.keySet().toArray(new String[0]);
    }

    /**
     * Повертає красиво відформатований список підтримуваних форматів
     */
    public static String getSupportedFormatsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Підтримувані формати:\n\n");

        for (FileCategory category : FileCategory.values()) {
            if (category == FileCategory.UNKNOWN) continue;

            sb.append(getCategoryDescription(category)).append(":\n");
            for (Map.Entry<String, FileCategory> entry : extensionToCategory.entrySet()) {
                if (entry.getValue() == category) {
                    sb.append("  • .").append(entry.getKey()).append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}

