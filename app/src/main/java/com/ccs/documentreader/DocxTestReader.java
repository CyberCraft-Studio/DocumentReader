package com.ccs.documentreader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.InputStream;

/**
 * Простий тестовий читач для діагностики
 */
public class DocxTestReader {
    private static final String TAG = "DocxTestReader";
    
    public static String testReadDocx(Context context, Uri uri) {
        StringBuilder result = new StringBuilder();
        
        result.append("=== ТЕСТ ЧИТАННЯ DOCX ===\n\n");
        
        try {
            result.append("1. Відкриваємо InputStream...\n");
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            
            if (inputStream == null) {
                result.append("❌ ПОМИЛКА: InputStream is null\n");
                return result.toString();
            }
            result.append("✓ InputStream створено\n\n");
            
            result.append("2. Перевірка magic bytes...\n");
            byte[] header = new byte[4];
            inputStream.mark(100);
            int read = inputStream.read(header);
            inputStream.reset();
            
            result.append("Прочитано байтів: " + read + "\n");
            result.append("Magic bytes: ");
            for (byte b : header) {
                result.append(String.format("%02X ", b));
            }
            result.append("\n");
            
            if (header[0] == 0x50 && header[1] == 0x4B) {
                result.append("✓ ZIP сигнатура знайдена (це правильний DOCX)\n\n");
            } else {
                result.append("❌ Неправильна сигнатура! Це НЕ DOCX файл!\n");
                result.append("Очікується: 50 4B (PK)\n");
                return result.toString();
            }
            
            result.append("3. Відкриваємо XWPFDocument...\n");
            XWPFDocument document = new XWPFDocument(inputStream);
            result.append("✓ Документ відкрито\n\n");
            
            result.append("4. Інформація про документ:\n");
            result.append("Кількість параграфів: " + document.getParagraphs().size() + "\n");
            result.append("Кількість таблиць: " + document.getTables().size() + "\n");
            result.append("Кількість зображень: " + document.getAllPictures().size() + "\n\n");
            
            result.append("5. Перші 3 параграфи:\n");
            int count = 0;
            for (XWPFParagraph para : document.getParagraphs()) {
                if (count >= 3) break;
                String text = para.getText();
                result.append("Параграф " + (count + 1) + ": [" + text + "]\n");
                result.append("Довжина: " + text.length() + " символів\n");
                
                // Показати байти першого символу
                if (!text.isEmpty()) {
                    char firstChar = text.charAt(0);
                    result.append("Перший символ: '" + firstChar + "' (код: " + (int)firstChar + ")\n");
                }
                result.append("\n");
                count++;
            }
            
            document.close();
            inputStream.close();
            
            result.append("=== ТЕСТ ЗАВЕРШЕНО УСПІШНО ===\n");
            
        } catch (Exception e) {
            result.append("\n❌ ПОМИЛКА: " + e.getMessage() + "\n");
            result.append("Тип помилки: " + e.getClass().getName() + "\n");
            
            Log.e(TAG, "Помилка тесту", e);
            
            StackTraceElement[] stack = e.getStackTrace();
            result.append("\nStack trace (перші 5 рядків):\n");
            for (int i = 0; i < Math.min(5, stack.length); i++) {
                result.append("  " + stack[i].toString() + "\n");
            }
        }
        
        return result.toString();
    }
}

