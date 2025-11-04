package com.ccs.documentreader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.InputStream;

/**
 * Перевірка файлів перед читанням
 */
public class FileValidator {
    private static final String TAG = "FileValidator";
    
    /**
     * Перевірка чи файл є дійсним DOCX
     */
    public static boolean isValidDocx(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return false;
            
            // Перевірка ZIP сигнатури (DOCX - це ZIP архів)
            byte[] header = new byte[4];
            int read = is.read(header);
            
            if (read < 4) return false;
            
            // ZIP magic numbers: 50 4B 03 04 або 50 4B 05 06 або 50 4B 07 08
            return (header[0] == 0x50 && header[1] == 0x4B && 
                   (header[2] == 0x03 || header[2] == 0x05 || header[2] == 0x07));
                   
        } catch (Exception e) {
            Log.e(TAG, "Помилка перевірки DOCX", e);
            return false;
        }
    }
    
    /**
     * Перевірка чи файл є дійсним DOC
     */
    public static boolean isValidDoc(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return false;
            
            // Перевірка OLE2 сигнатури
            byte[] header = new byte[8];
            int read = is.read(header);
            
            if (read < 8) return false;
            
            // OLE2 magic: D0 CF 11 E0 A1 B1 1A E1
            return (header[0] == (byte)0xD0 && header[1] == (byte)0xCF && 
                    header[2] == 0x11 && header[3] == (byte)0xE0);
                    
        } catch (Exception e) {
            Log.e(TAG, "Помилка перевірки DOC", e);
            return false;
        }
    }
    
    /**
     * Перевірка чи файл є дійсним PPTX
     */
    public static boolean isValidPptx(Context context, Uri uri) {
        // PPTX також ZIP, як і DOCX
        return isValidDocx(context, uri);
    }
}

