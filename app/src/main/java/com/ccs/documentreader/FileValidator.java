package com.ccs.documentreader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.InputStream;

/**
 * Швидка перевірка magic-байтів. Не відкриває весь документ — лише читає
 * перші кілька байтів стрімом.
 */
public final class FileValidator {

    private static final String TAG = "FileValidator";

    private FileValidator() {}

    public static boolean isValidDocx(Context ctx, Uri uri)  { return isZipBased(ctx, uri); }
    public static boolean isValidXlsx(Context ctx, Uri uri)  { return isZipBased(ctx, uri); }
    public static boolean isValidPptx(Context ctx, Uri uri)  { return isZipBased(ctx, uri); }
    public static boolean isValidEpub(Context ctx, Uri uri)  { return isZipBased(ctx, uri); }

    public static boolean isValidDoc(Context ctx, Uri uri) {
        return matchesHeader(ctx, uri, new int[]{0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1});
    }

    /** Класичний PPT (OLE), той самий заголовок, що й у .doc. */
    public static boolean isValidPpt(Context ctx, Uri uri) {
        return isValidDoc(ctx, uri);
    }

    public static boolean isValidPdf(Context ctx, Uri uri) {
        return matchesHeader(ctx, uri, new int[]{'%', 'P', 'D', 'F'});
    }

    private static boolean isZipBased(Context ctx, Uri uri) {
        return matchesHeader(ctx, uri, new int[]{'P', 'K'});
    }

    private static boolean matchesHeader(Context ctx, Uri uri, int[] expected) {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            if (is == null) return false;
            byte[] head = new byte[expected.length];
            int read = 0;
            while (read < head.length) {
                int n = is.read(head, read, head.length - read);
                if (n < 0) break;
                read += n;
            }
            if (read < expected.length) return false;
            for (int i = 0; i < expected.length; i++) {
                if ((head[i] & 0xFF) != (expected[i] & 0xFF)) return false;
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "header check failed: " + e.getMessage());
            return false;
        }
    }
}
