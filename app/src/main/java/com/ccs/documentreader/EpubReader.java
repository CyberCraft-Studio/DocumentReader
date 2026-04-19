package com.ccs.documentreader;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * EPUB: один раз копіюємо на диск, парсимо spine, далі кожну главу рендеримо окремо
 * (без збору всього HTML в один {@link StringBuilder} — це дає OOM на великих книгах).
 */
public final class EpubReader {

    private static final String TAG = "EpubReader";
    /** Максимум сирих байтів однієї глави XHTML. */
    private static final int MAX_CHAPTER_BYTES = 4 * 1024 * 1024;
    /** Маленькі зображення в base64 — великі пропускаємо, щоб не роздувати пам'ять. */
    private static final int MAX_IMAGE_BYTES = 256 * 1024;
    private static final int MAX_CSS_TOTAL = 400 * 1024;

    private EpubReader() {}

    public static final class Session {
        public final File epubFile;
        public final List<String> chapterPaths;
        /** Вже готовий HTML-блок: заголовок книги + &lt;style&gt;…&lt;/style&gt; */
        public final String headHtml;
        public final int chapterCount;

        Session(File epubFile, List<String> chapterPaths, String headHtml) {
            this.epubFile = epubFile;
            this.chapterPaths = chapterPaths;
            this.headHtml = headHtml;
            this.chapterCount = chapterPaths.size();
        }
    }

    /**
     * Копіює EPUB у кеш, парсить OPF/spine. Викликати з фонового потоку.
     * Тимчасовий файл треба видалити після перегляду ({@link Session#epubFile}).
     */
    public static Session openSession(Context context, Uri uri) throws Exception {
        File tmp = File.createTempFile("epub_", ".epub", context.getCacheDir());
        IoUtils.copyUriToFile(context, uri, tmp);
        try (ZipFile zf = new ZipFile(tmp)) {
            String opfPath = findOpfPath(zf);
            if (opfPath == null) {
                opfPath = findFirstEntryPath(zf, ".opf");
            }
            if (opfPath == null) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw new IllegalStateException("Не знайдено OPF (пошкоджений EPUB)");
            }

            String opfXml = readEntryString(zf, opfPath, 2 * 1024 * 1024);
            String opfDir = parentDir(opfPath);
            Map<String, String> manifest = parseManifest(opfXml);
            Map<String, String> manifestTypes = parseManifestTypes(opfXml);
            List<String> spine = parseSpine(opfXml);

            String title = firstMatch(opfXml, "<dc:title[^>]*>([^<]+)</dc:title>");
            String author = firstMatch(opfXml, "<dc:creator[^>]*>([^<]+)</dc:creator>");

            StringBuilder head = new StringBuilder(4096);
            head.append("<div class='epub-meta'>");
            if (title != null && !title.isEmpty()) {
                head.append("<h1 class='epub-title'>").append(escape(title.trim())).append("</h1>");
            }
            if (author != null && !author.isEmpty()) {
                head.append("<div class='epub-author'>").append(escape(author.trim())).append("</div>");
            }
            head.append("</div>");

            StringBuilder allCss = new StringBuilder();
            for (Map.Entry<String, String> e : manifest.entrySet()) {
                String mt = manifestTypes.get(e.getKey());
                if (mt != null && mt.contains("css") && allCss.length() < MAX_CSS_TOTAL) {
                    String cssPath = resolve(opfDir, e.getValue());
                    String css = readEntryString(zf, cssPath, MAX_CSS_TOTAL - allCss.length());
                    if (css != null) {
                        allCss.append(scopeCss(css)).append('\n');
                    }
                }
            }
            if (allCss.length() > 0) {
                head.append("<style>").append(allCss).append("</style>");
            }

            List<String> chapters = new ArrayList<>();
            for (String id : spine) {
                String href = manifest.get(id);
                if (href == null) continue;
                chapters.add(resolve(opfDir, stripFragment(href)));
            }

            if (chapters.isEmpty()) {
                chapters.addAll(listHtmlPaths(zf));
            }

            return new Session(tmp, chapters, head.toString());
        }
    }

    /** Тіло однієї глави (фрагмент для innerHTML), з обмеженими картинками. */
    public static String renderChapterBody(File epubFile, int chapterIndex, String zipPath)
            throws Exception {
        try (ZipFile zf = new ZipFile(epubFile)) {
            String xhtml = readEntryString(zf, zipPath, MAX_CHAPTER_BYTES);
            if (xhtml == null) return "";
            String body = extractBody(xhtml);
            body = stripScripts(body);
            body = inlineImages(body, parentDir(zipPath), zf);
            return "<section class='epub-chapter' id='epch" + chapterIndex + "'>" + body + "</section>";
        }
    }

    public static String buildEpubShellHtml(String fileTitle, Session session) {
        String hint = "<div class='info' id='epubHint'><small>"
            + escapeStatic("Глави підвантажуються при прокрутці вниз. У пам'яті залишаються лише останні розділи.")
            + "</small></div>";
        return "<!DOCTYPE html><html><head>" +
            "<meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=yes'>" +
            "<title>" + escapeStatic(fileTitle == null ? "" : fileTitle) + "</title>" +
            "<style>" + RichDocumentReader.getDocumentCss() + "</style>" +
            "</head><body class='theme-light'>" +
            "<main class='container'>" +
            session.headHtml +
            hint +
            "<div id='epubRoot'></div>" +
            "<div class='info' id='epubFoot'></div>" +
            "<script>" +
            "window.__epubLoading=false;" +
            "window.__epubEof=false;" +
            "window.__epubFirst=0;" +
            "window.__appendEpub=function(idx,html){" +
            "var r=document.getElementById('epubRoot');" +
            "var w=document.createElement('div');" +
            "w.id='epubw'+idx;" +
            "w.innerHTML=html;" +
            "r.appendChild(w);" +
            "};" +
            "window.__evictEpub=function(idx){" +
            "var e=document.getElementById('epubw'+idx);" +
            "if(e)e.remove();" +
            "};" +
            "window.__setEpubFoot=function(t){document.getElementById('epubFoot').textContent=t;};" +
            "window.addEventListener('scroll',function(){" +
            "if(window.__epubLoading||window.__epubEof)return;" +
            "var st=window.scrollY||document.documentElement.scrollTop;" +
            "var sh=document.documentElement.scrollHeight;" +
            "var vh=window.innerHeight;" +
            "if(st+vh>=sh-1400){window.__epubLoading=true;EpubBridge.requestMore();}" +
            "},{passive:true});" +
            "window.__tryEpubFill=function(){" +
            "if(window.__epubLoading||window.__epubEof)return;" +
            "var sh=document.documentElement.scrollHeight;" +
            "var vh=window.innerHeight;" +
            "if(sh<=vh+300){window.__epubLoading=true;EpubBridge.requestMore();}" +
            "};" +
            "</script>" +
            "</main></body></html>";
    }

    private static String escapeStatic(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String stripScripts(String html) {
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
    }

    private static List<String> listHtmlPaths(ZipFile zf) {
        List<String> out = new ArrayList<>();
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            if (ze.isDirectory()) continue;
            String n = ze.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")) {
                out.add(ze.getName());
            }
        }
        Collections.sort(out);
        return out;
    }

    private static String findOpfPath(ZipFile zf) throws Exception {
        ZipEntry e = zf.getEntry("META-INF/container.xml");
        if (e == null) return null;
        String xml = readEntryString(zf, "META-INF/container.xml", 256 * 1024);
        if (xml == null) return null;
        Matcher m = Pattern.compile("full-path=\"([^\"]+)\"").matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private static String findFirstEntryPath(ZipFile zf, String ext) {
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            String name = en.nextElement().getName();
            if (name.toLowerCase(Locale.ROOT).endsWith(ext)) return name;
        }
        return null;
    }

    static String readEntryString(ZipFile zf, String path, int maxBytes) throws Exception {
        ZipEntry e = zf.getEntry(path);
        if (e == null) return null;
        long sz = e.getSize();
        if (sz > maxBytes) {
            Log.w(TAG, "Entry truncated: " + path + " (" + sz + " bytes)");
        }
        try (InputStream is = zf.getInputStream(e)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(
                Math.min(maxBytes, 1024 * 1024));
            byte[] chunk = new byte[64 * 1024];
            int total = 0;
            while (total < maxBytes) {
                int n = is.read(chunk);
                if (n < 0) break;
                int take = Math.min(n, maxBytes - total);
                bos.write(chunk, 0, take);
                total += take;
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readEntryBytes(ZipFile zf, String path, int maxBytes) throws Exception {
        ZipEntry e = zf.getEntry(path);
        if (e == null) return null;
        try (InputStream is = zf.getInputStream(e)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int total = 0;
            while (total < maxBytes) {
                int n = is.read(buf);
                if (n < 0) break;
                int take = Math.min(n, maxBytes - total);
                bos.write(buf, 0, take);
                total += take;
            }
            return bos.toByteArray();
        }
    }

    private static Map<String, String> parseManifest(String opfXml) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher m = Pattern.compile(
            "<item\\b[^>]*\\bid=\"([^\"]+)\"[^>]*\\bhref=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE).matcher(opfXml);
        while (m.find()) {
            result.put(m.group(1), decodeUrl(m.group(2)));
        }
        Matcher m2 = Pattern.compile(
            "<item\\b[^>]*\\bhref=\"([^\"]+)\"[^>]*\\bid=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE).matcher(opfXml);
        while (m2.find()) {
            result.putIfAbsent(m2.group(2), decodeUrl(m2.group(1)));
        }
        return result;
    }

    private static Map<String, String> parseManifestTypes(String opfXml) {
        Map<String, String> result = new HashMap<>();
        Matcher m = Pattern.compile(
            "<item\\b[^>]*\\bid=\"([^\"]+)\"[^>]*\\bmedia-type=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE).matcher(opfXml);
        while (m.find()) {
            result.put(m.group(1), m.group(2));
        }
        return result;
    }

    private static List<String> parseSpine(String opfXml) {
        List<String> list = new ArrayList<>();
        Matcher m = Pattern.compile(
            "<itemref\\b[^>]*\\bidref=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE).matcher(opfXml);
        while (m.find()) {
            list.add(m.group(1));
        }
        return list;
    }

    private static String extractBody(String xhtml) {
        Matcher m = Pattern.compile("<body[^>]*>([\\s\\S]*?)</body>", Pattern.CASE_INSENSITIVE)
            .matcher(xhtml);
        if (m.find()) return m.group(1);
        return xhtml;
    }

    private static String inlineImages(String body, String chapterDir, ZipFile zf) throws Exception {
        Matcher m = Pattern.compile(
            "(<img\\b[^>]*?\\bsrc=\")([^\"]+)(\")",
            Pattern.CASE_INSENSITIVE).matcher(body);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String src = decodeUrl(m.group(2));
            String resolved = resolve(chapterDir, stripFragment(src));
            byte[] data = readEntryBytes(zf, resolved, MAX_IMAGE_BYTES);
            String replacement;
            if (data != null && data.length > 0) {
                String mime = guessMime(resolved);
                String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
                replacement = m.group(1) + "data:" + mime + ";base64," + b64 + m.group(3);
            } else {
                replacement = m.group(1) + m.group(3);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String guessMime(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static String scopeCss(String css) {
        return css.replaceAll("(?i)@import[^;]+;", "");
    }

    private static String parentDir(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i + 1);
    }

    private static String resolve(String base, String href) {
        if (href.startsWith("/")) return href.substring(1);
        if (base == null || base.isEmpty()) return href;
        String combined = base + href;
        List<String> parts = new ArrayList<>();
        for (String seg : combined.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
            } else {
                parts.add(seg);
            }
        }
        return String.join("/", parts);
    }

    private static String stripFragment(String href) {
        int i = href.indexOf('#');
        return i < 0 ? href : href.substring(0, i);
    }

    private static String decodeUrl(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String firstMatch(String text, String regex) {
        try {
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) return m.group(1);
        } catch (Exception e) {
            Log.w(TAG, "regex: " + e.getMessage());
        }
        return null;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
