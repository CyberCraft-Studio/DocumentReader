package com.ccs.documentreader;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

final class IoUtils {

    private IoUtils() {}

    /** Копіює URI у файл; повертає кількість байтів. */
    static long copyUriToFile(Context ctx, Uri uri, File dest) throws Exception {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(dest)) {
            if (is == null) throw new IllegalStateException("InputStream is null");
            byte[] buf = new byte[256 * 1024];
            long total = 0;
            int n;
            while ((n = is.read(buf)) != -1) {
                os.write(buf, 0, n);
                total += n;
            }
            return total;
        }
    }
}
