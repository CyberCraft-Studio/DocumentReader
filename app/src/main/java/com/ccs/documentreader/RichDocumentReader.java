package com.ccs.documentreader;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.opencsv.CSVReader;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Універсальний "rich" читач документів. Генерує HTML для відображення в WebView.
 *
 * Принципи:
 *   • НЕ робити подвійної роботи (жодних "тестових" парсингів у проді)
 *   • Завжди закривати потоки
 *   • Безпечно екранувати HTML (без поломки UTF-8)
 *   • Формати, де доречно — мати власні стилі (Excel-таблиці, EPUB-розділи, …)
 */
public class RichDocumentReader {
    private static final String TAG = "RichDocumentReader";

    /** Розмір одного блоку при потоковому перегляді великих текстових файлів. */
    public static final int STREAM_CHUNK_BYTES = 1024 * 1024;
    /** Скільки блоків (~МБ) тримати в DOM WebView; старі видаляються при прокрутці вниз. */
    public static final int STREAM_MAX_DOM_CHUNKS = 12;
    /** Від цього розміру (або якщо розмір невідомий) — увімкнути потоковий режим. */
    public static final long STREAM_THRESHOLD_BYTES = 512 * 1024;

    /**
     * Для structured JSON/YAML: якщо більше — не будуємо AST, показуємо як текст
     * (або потоково через {@link DocumentViewerActivity}).
     */
    private static final int MAX_STRUCTURED_TEXT_BYTES = 6 * 1024 * 1024;

    private final Context context;

    public RichDocumentReader(Context context) {
        this.context = context;
    }

    /** Чи варто відкривати файл як потоковий текст (чанки по 1 МБ) у {@link DocumentViewerActivity}. */
    public static boolean shouldStreamAsPlainText(String extension, long sizeBytes) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!isStreamablePlainTextExtension(ext)) return false;
        return sizeBytes < 0 || sizeBytes >= STREAM_THRESHOLD_BYTES;
    }

    public static boolean isStreamablePlainTextExtension(String ext) {
        if (ext == null) return false;
        switch (ext.toLowerCase(Locale.ROOT)) {
            case "txt":
            case "log":
            case "md":
            case "markdown":
            case "rmd":
            case "json":
            case "yaml":
            case "yml":
            case "xml":
            case "svg":
            case "csv":
            case "tsv":
            case "tex":
            case "bib":
            case "ini":
            case "conf":
            case "properties":
            case "rtf":
            case "html":
            case "htm":
            case "java":
                return true;
            default:
                return false;
        }
    }

    public static long queryOpenableSize(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver()
                .query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx);
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    /**
     * Порожня оболонка HTML для потокового перегляду: перший блок додається через JS
     * після {@code onPageFinished}.
     */
    public static String buildStreamViewerHtml(String fileName, long knownSizeBytes, boolean javaSyntax) {
        String sizeLine;
        if (knownSizeBytes >= 0) {
            sizeLine = "Розмір файлу: " + (knownSizeBytes / 1024L) + " КБ";
        } else {
            sizeLine = "Розмір файлу: невідомий (потокове читання)";
        }
        String preClass = javaSyntax ? "text-content java-src" : "text-content";
        String javaNote = javaSyntax
            ? "<br/><small>Підсвітка синтаксису Java по фрагментах; на межах 1 МБ кольори можуть зміщуватися.</small>"
            : "";
        return "<!DOCTYPE html><html><head>" +
            "<meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=yes'>" +
            "<title>" + escapeStatic(fileName == null ? "" : fileName) + "</title>" +
            "<style>" + getDocumentCss() + "</style>" +
            "</head><body class='theme-light'>" +
            "<main class='container'>" +
            "<div class='info' id='streamHead'>" + escapeStatic(sizeLine) +
            "<br/><small>Підвантаження по 1 МБ при прокрутці вниз. У пам'яті WebView зберігається лише останні ~" +
            STREAM_MAX_DOM_CHUNKS + " МБ — початок файлу може вивантажуватися з екрана.</small>" +
            javaNote + "</div>" +
            "<pre id='streamPre' class='" + preClass + "'></pre>" +
            "<div class='info' id='streamFoot'></div>" +
            "<script>" +
            "window.__streamEof=false;" +
            "window.__streamLoading=false;" +
            "window.__firstDomChunk=0;" +
            "window.__appendChunk=function(id,txt,asHtml){" +
            "var pre=document.getElementById('streamPre');" +
            "var s=document.createElement('span');" +
            "s.id='sch'+id;" +
            "s.className='stream-ch';" +
            "if(asHtml)s.innerHTML=txt;else s.textContent=txt;" +
            "pre.appendChild(s);" +
            "};" +
            "window.__evictChunk=function(id){" +
            "var e=document.getElementById('sch'+id);" +
            "if(e)e.remove();" +
            "};" +
            "window.__setFoot=function(t){document.getElementById('streamFoot').textContent=t;};" +
            "window.addEventListener('scroll',function(){" +
            "if(window.__streamLoading||window.__streamEof)return;" +
            "var st=window.scrollY||document.documentElement.scrollTop;" +
            "var sh=document.documentElement.scrollHeight;" +
            "var vh=window.innerHeight;" +
            "if(st+vh>=sh-1200){window.__streamLoading=true;StreamBridge.requestMore();}" +
            "},{passive:true});" +
            "window.__tryAutoFill=function(){" +
            "if(window.__streamLoading||window.__streamEof)return;" +
            "var sh=document.documentElement.scrollHeight;" +
            "var vh=window.innerHeight;" +
            "if(sh<=vh+200){window.__streamLoading=true;StreamBridge.requestMore();}" +
            "};" +
            "</script>" +
            "</main></body></html>";
    }

    private static String escapeStatic(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<':  out.append("&lt;");   break;
                case '>':  out.append("&gt;");   break;
                case '&':  out.append("&amp;");  break;
                case '"':  out.append("&quot;"); break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }

    public String readDocumentAsHtml(Uri uri, String fileName) {
        String extension = getFileExtension(fileName).toLowerCase(Locale.ROOT);
        long t0 = System.currentTimeMillis();

        try {
            String content;
            switch (extension) {
                case "docx":
                case "docm":
                    content = readDocxAsHtml(uri);
                    break;
                case "doc":
                    content = readDocAsHtml(uri);
                    break;
                case "docs":
                    content = readWordFlexible(uri);
                    break;

                case "xlsx":
                case "xlsm":
                    content = readXlsxAsHtml(uri);
                    break;
                case "xls":
                    content = readXlsAsHtml(uri);
                    break;

                case "pptx":
                case "pptm":
                    content = readPptxAsHtml(uri);
                    break;
                case "ppt":
                    content = readPptAsHtml(uri);
                    break;

                case "epub":
                    throw new UnsupportedOperationException(
                        "EPUB: використовуйте потоковий перегляд у DocumentViewerActivity");

                case "md":
                case "markdown":
                case "rmd":
                    content = readMarkdownAsHtml(uri);
                    break;

                case "csv":
                    content = readCsvAsHtml(uri, ',');
                    break;
                case "tsv":
                    content = readCsvAsHtml(uri, '\t');
                    break;

                case "json":
                    content = readJsonAsHtml(uri);
                    break;

                case "yaml":
                case "yml":
                    content = readYamlAsHtml(uri);
                    break;

                case "xml":
                case "svg":
                    content = readXmlAsHtml(uri);
                    break;

                case "html":
                case "htm":
                    content = readHtmlPassthrough(uri);
                    break;

                case "java":
                    content = readJavaAsHtml(uri);
                    break;

                case "rtf":
                case "txt":
                case "tex":
                case "bib":
                case "ini":
                case "ris":
                case "enw":
                case "log":
                case "conf":
                case "properties":
                    content = readTextAsHtml(uri);
                    break;

                default:
                    content = readTextAsHtml(uri);
                    break;
            }

            Log.d(TAG, "Rendered " + fileName + " in " + (System.currentTimeMillis() - t0) + " ms");
            return wrapHtml(content, fileName);

        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OOM rendering " + fileName, oom);
            // Намагаємося звільнити пам'ять і повернути дружнє повідомлення
            //noinspection CallToSystemGC
            System.gc();
            long size = querySize(uri);
            String sizeStr = size > 0 ? (size / 1024 / 1024) + " МБ" : "невідомий";
            return wrapHtml(
                "<div class='error'>" +
                    "<h2>Файл занадто великий для перегляду</h2>" +
                    "<p>Розмір: " + sizeStr + ". Спробуйте зменшений варіант або відкрийте файл у спеціалізованому застосунку.</p>" +
                "</div>", fileName);
        } catch (Exception e) {
            Log.e(TAG, "Read failed: " + fileName, e);
            return wrapHtml(
                "<div class='error'>" +
                    "<h2>Не вдалося відкрити документ</h2>" +
                    "<p>" + escape(e.getMessage()) + "</p>" +
                "</div>", fileName);
        }
    }

    /** Розширення .docs або невідоме ім'я: спроба DOCX (ZIP), інакше DOC (OLE). */
    private String readWordFlexible(Uri uri) throws Exception {
        if (FileValidator.isValidDocx(context, uri)) return readDocxAsHtml(uri);
        if (FileValidator.isValidDoc(context, uri)) return readDocAsHtml(uri);
        throw new IllegalStateException("Очікується DOC або DOCX (перевірте файл)");
    }

    // =========================== DOCX ===========================

    private String readDocxAsHtml(Uri uri) throws Exception {
        if (!FileValidator.isValidDocx(context, uri)) {
            throw new IllegalStateException("Файл не є валідним DOCX (немає ZIP-сигнатури)");
        }
        StringBuilder html = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             XWPFDocument doc = new XWPFDocument(is)) {

            html.append("<article class='reader-doc reader-word'>");
            html.append("<header class='reader-banner'><span class='reader-banner-label'>")
                .append("Microsoft Word").append("</span></header>");
            html.append("<div class='doc-body'>");

            int[] listMode = new int[1];
            for (IBodyElement el : doc.getBodyElements()) {
                appendDocxBodyElement(html, el, listMode);
            }
            closeDocxList(html, listMode);

            html.append("</div></article>");
        }
        return html.toString();
    }

    private void closeDocxList(StringBuilder html, int[] listMode) {
        if (listMode[0] == 1) {
            html.append("</ul>");
        } else if (listMode[0] == 2) {
            html.append("</ol>");
        }
        listMode[0] = 0;
    }

    private void appendDocxBodyElement(StringBuilder html, IBodyElement el, int[] listMode) {
        if (el instanceof XWPFTable) {
            closeDocxList(html, listMode);
            html.append(tableToHtml((XWPFTable) el));
            return;
        }
        if (el instanceof XWPFSDT) {
            closeDocxList(html, listMode);
            XWPFSDT sdt = (XWPFSDT) el;
            try {
                String t = sdt.getContent().getText();
                if (t != null && !t.trim().isEmpty()) {
                    html.append("<div class='doc-sdt'>");
                    for (String line : t.split("\n")) {
                        String L = line.trim();
                        if (!L.isEmpty()) {
                            html.append("<p>").append(escape(L)).append("</p>");
                        }
                    }
                    html.append("</div>");
                }
            } catch (Exception e) {
                Log.w(TAG, "SDT: " + e.getMessage());
            }
            return;
        }
        if (!(el instanceof XWPFParagraph)) {
            return;
        }
        XWPFParagraph p = (XWPFParagraph) el;
        BigInteger numId = p.getNumID();
        if (numId != null) {
            boolean ordered = isOrderedListFormat(p.getNumFmt());
            int want = ordered ? 2 : 1;
            if (listMode[0] == 0) {
                html.append(ordered ? "<ol class='doc-list'>" : "<ul class='doc-list'>");
                listMode[0] = want;
            } else if (listMode[0] != want) {
                html.append(listMode[0] == 1 ? "</ul>" : "</ol>");
                html.append(ordered ? "<ol class='doc-list'>" : "<ul class='doc-list'>");
                listMode[0] = want;
            }
            double ind = listIndentEm(p);
            String inner = paragraphInnerHtml(p);
            html.append("<li class='doc-li' style='margin-inline-start:").append(ind).append("em'>");
            if (inner.trim().isEmpty()) {
                html.append("&nbsp;");
            } else {
                html.append(inner);
            }
            html.append("</li>");
            return;
        }
        closeDocxList(html, listMode);
        html.append(paragraphToHtml(p));
    }

    private static boolean isOrderedListFormat(String fmt) {
        if (fmt == null || fmt.isEmpty()) {
            return false;
        }
        String f = fmt.toLowerCase(Locale.ROOT);
        if ("bullet".equals(f) || "none".equals(f)) {
            return false;
        }
        return true;
    }

    private static double listIndentEm(XWPFParagraph p) {
        try {
            BigInteger ilvl = p.getNumIlvl();
            if (ilvl == null) {
                return 0;
            }
            return Math.max(0, ilvl.intValue()) * 1.15;
        } catch (Exception e) {
            return 0;
        }
    }

    private String paragraphInnerHtml(XWPFParagraph p) {
        StringBuilder out = new StringBuilder();
        for (XWPFRun r : p.getRuns()) {
            out.append(runToHtml(r));
        }
        StringBuilder inlineImgs = new StringBuilder();
        for (XWPFRun r : p.getRuns()) {
            for (XWPFPicture pic : r.getEmbeddedPictures()) {
                try {
                    XWPFPictureData pd = pic.getPictureData();
                    byte[] bytes = pd.getData();
                    if (bytes == null || bytes.length == 0) continue;
                    String mime = pd.getPackagePart().getContentType();
                    inlineImgs.append("<img class='doc-image inline-image' src='data:")
                        .append(mime).append(";base64,")
                        .append(Base64.encodeToString(bytes, Base64.NO_WRAP))
                        .append("'/>");
                } catch (Exception ignored) {}
            }
        }
        out.append(inlineImgs);
        return out.toString();
    }

    private String paragraphToHtml(XWPFParagraph p) {
        String inner = paragraphInnerHtml(p);
        String text = p.getText();
        if ((text == null || text.trim().isEmpty()) && inner.trim().isEmpty()) {
            return "<br class='doc-br'/>";
        }

        String align = alignment(p.getAlignment());
        String style = p.getStyle();
        String sl = style != null ? style.toLowerCase(Locale.ROOT) : "";
        boolean heading = sl.contains("heading")
            || (sl.contains("title") && !sl.contains("subtitle"));
        int level = heading ? extractHeadingLevel(style) : 0;

        StringBuilder out = new StringBuilder();
        if (heading && level >= 1 && level <= 6) {
            out.append("<h").append(level).append(" class='doc-heading' style='text-align:")
                .append(align).append(";'>").append(inner).append("</h").append(level).append('>');
        } else {
            out.append("<p class='doc-p' style='text-align:").append(align).append(";'>")
                .append(inner).append("</p>");
        }
        return out.toString();
    }

    private String runToHtml(XWPFRun run) {
        if (!run.getEmbeddedPictures().isEmpty() && run.getText(0) == null) return "";

        String text = run.getText(0);
        if (text == null || text.isEmpty()) return "";
        text = escape(text);

        StringBuilder open = new StringBuilder();
        StringBuilder close = new StringBuilder();
        if (run.isBold()) { open.append("<strong>"); close.insert(0, "</strong>"); }
        if (run.isItalic()) { open.append("<em>"); close.insert(0, "</em>"); }
        if (run.getUnderline() != UnderlinePatterns.NONE) {
            open.append("<u>"); close.insert(0, "</u>");
        }
        if (run.isStrikeThrough()) { open.append("<s>"); close.insert(0, "</s>"); }

        StringBuilder span = new StringBuilder();
        String color = run.getColor();
        if (color != null && !color.equals("auto")) span.append("color:#").append(color).append(';');
        int sz = run.getFontSize();
        if (sz > 0) span.append("font-size:").append(sz).append("pt;");

        if (span.length() > 0) {
            open.append("<span style='").append(span).append("'>");
            close.insert(0, "</span>");
        }
        return open.toString() + text + close.toString();
    }

    private String tableToHtml(XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return "<div class='table-wrap'><table class='doc-table'><tbody></tbody></table></div>";
        }
        StringBuilder html = new StringBuilder("<div class='table-wrap'><table class='doc-table'>");
        html.append("<thead><tr>");
        for (XWPFTableCell cell : rows.get(0).getTableCells()) {
            html.append("<th class='doc-th'>");
            for (XWPFParagraph cp : cell.getParagraphs()) {
                String inner = paragraphInnerHtml(cp);
                if (!inner.trim().isEmpty()) {
                    html.append("<p class='doc-cell-p'>").append(inner).append("</p>");
                }
            }
            html.append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (int r = 1; r < rows.size(); r++) {
            XWPFTableRow row = rows.get(r);
            html.append("<tr>");
            for (XWPFTableCell cell : row.getTableCells()) {
                html.append("<td class='doc-td'>");
                for (XWPFParagraph cp : cell.getParagraphs()) {
                    String inner = paragraphInnerHtml(cp);
                    if (!inner.trim().isEmpty()) {
                        html.append("<p class='doc-cell-p'>").append(inner).append("</p>");
                    }
                }
                html.append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");
        return html.toString();
    }

    // =========================== DOC (legacy) ===========================

    private String readDocAsHtml(Uri uri) throws Exception {
        if (!FileValidator.isValidDoc(context, uri)) {
            throw new IllegalStateException("Файл не є валідним DOC (OLE)");
        }
        StringBuilder html = new StringBuilder();
        html.append("<article class='reader-doc reader-word'>");
        html.append("<header class='reader-banner'><span class='reader-banner-label'>")
            .append("Microsoft Word").append("</span></header>");
        html.append("<div class='doc-body'><div class='doc-content doc-legacy'>");
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             HWPFDocument doc = new HWPFDocument(is)) {

            Range range = doc.getRange();
            int n = range.numParagraphs();
            for (int i = 0; i < n; i++) {
                Paragraph p = range.getParagraph(i);
                String t = p.text();
                if (t == null) continue;
                t = t.replace("\u0007", "").replace("\r", "").trim();
                if (t.isEmpty()) {
                    html.append("<br class='doc-br'/>");
                } else {
                    html.append("<p class='doc-p'>").append(escape(t)).append("</p>");
                }
            }
        }
        html.append("</div></div></article>");
        return html.toString();
    }

    // =========================== Excel ===========================

    private String readXlsxAsHtml(Uri uri) throws Exception {
        StringBuilder html = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             XSSFWorkbook wb = new XSSFWorkbook(is)) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet s = wb.getSheetAt(i);
                html.append("<section class='sheet'><h3>")
                    .append(escape(s.getSheetName()))
                    .append("</h3>")
                    .append(sheetToHtml(s))
                    .append("</section>");
            }
        }
        return html.toString();
    }

    private String readXlsAsHtml(Uri uri) throws Exception {
        StringBuilder html = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             HSSFWorkbook wb = new HSSFWorkbook(is)) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet s = wb.getSheetAt(i);
                html.append("<section class='sheet'><h3>")
                    .append(escape(s.getSheetName()))
                    .append("</h3>")
                    .append(sheetToHtml(s))
                    .append("</section>");
            }
        }
        return html.toString();
    }

    private String sheetToHtml(Sheet sheet) {
        StringBuilder html = new StringBuilder("<table class='excel-table'>");
        int maxCols = 0;
        for (Row row : sheet) maxCols = Math.max(maxCols, row.getLastCellNum());
        boolean first = true;
        for (Row row : sheet) {
            html.append("<tr>");
            for (int i = 0; i < maxCols; i++) {
                Cell cell = row.getCell(i);
                String tag = first ? "th" : "td";
                html.append('<').append(tag).append('>');
                if (cell != null) html.append(cellToHtml(cell));
                html.append("</").append(tag).append('>');
            }
            html.append("</tr>");
            first = false;
        }
        html.append("</table>");
        return html.toString();
    }

    private String cellToHtml(Cell cell) {
        try {
            CellType t = cell.getCellType();
            if (t == CellType.STRING) return escape(cell.getStringCellValue());
            if (t == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
            if (t == CellType.FORMULA) return "<em>" + escape(cell.getCellFormula()) + "</em>";
            if (t == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toString();
                double v = cell.getNumericCellValue();
                return v == (long) v ? String.valueOf((long) v) : String.format(Locale.US, "%.4f", v);
            }
        } catch (Exception ignored) {}
        return "";
    }

    // =========================== PowerPoint ===========================

    private String readPptxAsHtml(Uri uri) throws Exception {
        if (!FileValidator.isValidPptx(context, uri)) {
            throw new IllegalStateException("Файл не є валідним PPTX (немає ZIP-сигнатури)");
        }
        StringBuilder html = new StringBuilder();
        html.append("<article class='reader-doc reader-slideshow'>");
        html.append("<header class='reader-banner'><span class='reader-banner-label'>")
            .append("PowerPoint").append("</span></header>");
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             org.apache.poi.xslf.usermodel.XMLSlideShow ppt =
                 new org.apache.poi.xslf.usermodel.XMLSlideShow(is)) {

            List<org.apache.poi.xslf.usermodel.XSLFSlide> slides = ppt.getSlides();
            html.append("<p class='deck-meta'>Слайдів: ").append(slides.size()).append("</p>");
            html.append("<div class='slides-deck'>");
            int idx = 1;
            for (org.apache.poi.xslf.usermodel.XSLFSlide slide : slides) {
                html.append("<section class='ppt-slide-card'>");
                html.append("<div class='ppt-slide-meta'><span class='ppt-badge'>")
                    .append(idx++).append("</span><span class='ppt-slide-title'>Слайд</span></div>");
                html.append("<div class='ppt-slide-body'>");
                boolean hasText = false;
                try {
                    org.openxmlformats.schemas.presentationml.x2006.main.CTSlide ct = slide.getXmlObject();
                    if (ct != null && ct.getCSld() != null && ct.getCSld().getSpTree() != null
                        && ct.getCSld().getSpTree().getSpList() != null) {
                        for (org.openxmlformats.schemas.presentationml.x2006.main.CTShape sp
                             : ct.getCSld().getSpTree().getSpList()) {
                            if (sp.getTxBody() == null) continue;
                            List<String> lines = txBodyParagraphLines(sp.getTxBody());
                            StringBuilder shapeHtml = new StringBuilder();
                            for (String line : lines) {
                                if (line != null && !line.trim().isEmpty()) {
                                    shapeHtml.append("<p class='ppt-line'>").append(escape(line))
                                        .append("</p>");
                                }
                            }
                            if (shapeHtml.length() > 0) {
                                html.append("<div class='ppt-shape'>").append(shapeHtml).append("</div>");
                                hasText = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    html.append("<p class='error'>").append(escape(e.getMessage())).append("</p>");
                }
                if (!hasText) {
                    html.append("<p class='ppt-empty'>На слайді немає тексту або він у непідтримуваних об'єктах ")
                        .append("(діаграми, SmartArt тощо).</p>");
                }
                html.append("</div></section>");
            }
            html.append("</div>");
        }
        html.append("</article>");
        return html.toString();
    }

    /** Окремі абзаци текстового блоку — зручніше стилізувати як «слайд». */
    private static List<String> txBodyParagraphLines(
            org.openxmlformats.schemas.drawingml.x2006.main.CTTextBody body) {
        List<String> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        for (int i = 0; i < body.sizeOfPArray(); i++) {
            org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraph p = body.getPArray(i);
            if (p == null) continue;
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < p.sizeOfRArray(); j++) {
                org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun r = p.getRArray(j);
                if (r != null && r.getT() != null) sb.append(r.getT());
            }
            out.add(sb.toString());
        }
        return out;
    }

    private String readPptAsHtml(Uri uri) throws Exception {
        if (!FileValidator.isValidPpt(context, uri)) {
            throw new IllegalStateException("Файл не є валідним PPT (OLE)");
        }
        InputStream is = context.getContentResolver().openInputStream(uri);
        if (is == null) {
            throw new IllegalStateException("Не вдалося відкрити потік PPT");
        }
        PptLegacyRawTextExtractor ex = new PptLegacyRawTextExtractor(is);
        try {
            String raw = ex.getTextAsString();
            if (raw == null) {
                raw = "";
            }
            StringBuilder html = new StringBuilder();
            html.append("<article class='reader-doc reader-slideshow'>");
            html.append("<header class='reader-banner'><span class='reader-banner-label'>")
                .append("PowerPoint").append("</span></header>");
            html.append("<div class='info ppt-legacy-banner'><small>")
                .append("Класичний .ppt: текст витягнуто без макета слайдів (порядок фрагментів може ")
                .append("відрізнятися; можливі дублікати).</small></div>");
            html.append("<div class='doc-body ppt-legacy-body'>");
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                html.append("<p class='ppt-empty'>Текст не знайдено</p>");
            } else {
                for (String line : raw.split("\n")) {
                    String t = line.trim();
                    if (t.isEmpty()) {
                        html.append("<br class='doc-br'/>");
                    } else {
                        html.append("<p class='doc-p ppt-legacy-line'>").append(escape(t)).append("</p>");
                    }
                }
            }
            html.append("</div></article>");
            return html.toString();
        } finally {
            ex.close();
        }
    }

    // =========================== Markdown ===========================

    private String readMarkdownAsHtml(Uri uri) throws Exception {
        // Якщо файл великий — fallback у моноширинний preview, інакше commonmark
        // побудує величезний AST і впаде з OOM.
        if (querySize(uri) > MAX_STRUCTURED_TEXT_BYTES / 2) {
            return readTextAsHtml(uri);
        }
        String src = readAllTextSmall(uri);
        List<Extension> exts = Arrays.asList(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create()
        );
        Parser parser = Parser.builder().extensions(exts).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(exts).build();
        return "<article class='markdown-body'>" + renderer.render(parser.parse(src)) + "</article>";
    }

    // =========================== CSV / TSV ===========================

    private String readCsvAsHtml(Uri uri, char sep) throws Exception {
        StringBuilder html = new StringBuilder("<table class='excel-table'>");
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVReader csv = new com.opencsv.CSVReaderBuilder(r)
                 .withCSVParser(new com.opencsv.CSVParserBuilder().withSeparator(sep).build())
                 .build()) {

            String[] line;
            boolean first = true;
            int row = 0;
            while ((line = csv.readNext()) != null) {
                row++;
                if (row > 5000) {
                    html.append("<tr><td colspan='99'><em>Показано перші 5000 рядків</em></td></tr>");
                    break;
                }
                html.append("<tr>");
                for (String c : line) {
                    String tag = first ? "th" : "td";
                    html.append('<').append(tag).append('>')
                        .append(escape(c == null ? "" : c))
                        .append("</").append(tag).append('>');
                }
                html.append("</tr>");
                first = false;
            }
        }
        html.append("</table>");
        return html.toString();
    }

    // =========================== Text / JSON / XML / YAML ===========================

    private String readTextAsHtml(Uri uri) throws Exception {
        return "<pre class='text-content'>" + escape(readAllTextSmall(uri)) + "</pre>";
    }

    private String readJavaAsHtml(Uri uri) throws Exception {
        String src = readAllTextSmall(uri);
        return "<div class='info'>Java · підсвітка синтаксису</div>" +
            "<pre class='text-content java-src'>" + JavaSyntaxHighlighter.highlightToHtml(src) + "</pre>";
    }

    private String readJsonAsHtml(Uri uri) throws Exception {
        // Великі JSON-файли краще показати "як є" — без pretty-print парсингу,
        // щоб не тримати в пам'яті ще й об'єктне дерево.
        if (querySize(uri) > MAX_STRUCTURED_TEXT_BYTES) {
            return readTextAsHtml(uri);
        }
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Object o = JsonParser.parseReader(r);
            return "<pre class='json-content'>" + escape(gson.toJson(o)) + "</pre>";
        } catch (Exception e) {
            // fallback: показати як plain text
            return readTextAsHtml(uri);
        }
    }

    private String readYamlAsHtml(Uri uri) throws Exception {
        if (querySize(uri) > MAX_STRUCTURED_TEXT_BYTES) {
            return readTextAsHtml(uri);
        }
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            Yaml yaml = new Yaml();
            StringBuilder out = new StringBuilder();
            for (Object doc : yaml.loadAll(is)) {
                out.append(yamlToString(doc, 0)).append("\n---\n");
            }
            return "<pre class='json-content'>" + escape(out.toString()) + "</pre>";
        } catch (Exception e) {
            return readTextAsHtml(uri);
        }
    }

    private String yamlToString(Object o, int indent) {
        StringBuilder sb = new StringBuilder();
        String pad = repeat("  ", indent);
        if (o instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                sb.append(pad).append(e.getKey()).append(":\n")
                  .append(yamlToString(e.getValue(), indent + 1));
            }
        } else if (o instanceof Iterable) {
            for (Object i : (Iterable<?>) o) {
                sb.append(pad).append("- ").append(yamlToString(i, indent + 1).trim()).append('\n');
            }
        } else {
            sb.append(pad).append(o).append('\n');
        }
        return sb.toString();
    }

    private String readXmlAsHtml(Uri uri) throws Exception {
        return readTextAsHtml(uri);
    }

    private String readHtmlPassthrough(Uri uri) throws Exception {
        // Дозволяємо HTML, але видаляємо <script>
        String src = readAllTextSmall(uri);
        src = src.replaceAll("(?is)<script.*?</script>", "");
        return src;
    }

    /**
     * Читання невеликого тексту целиком (для файлів &lt; порогу потокового режиму в Activity).
     */
    private String readAllTextSmall(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder(8192);
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[16 * 1024];
            int n;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    /** Повертає розмір файлу через ContentResolver, або -1 якщо невідомо. */
    private long querySize(Uri uri) {
        return queryOpenableSize(context, uri);
    }

    // =========================== Helpers ===========================

    private String alignment(ParagraphAlignment a) {
        if (a == null) return "left";
        switch (a) {
            case CENTER: return "center";
            case RIGHT:  return "right";
            case BOTH:   return "justify";
            default:     return "left";
        }
    }

    private int extractHeadingLevel(String style) {
        if (style == null) return 3;
        String s = style.toLowerCase(Locale.ROOT);
        if (s.contains("title") && !s.contains("subtitle")) {
            return 1;
        }
        for (int i = 1; i <= 6; i++) if (s.contains("heading" + i)) return i;
        return 3;
    }

    private String escape(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<':  out.append("&lt;");   break;
                case '>':  out.append("&gt;");   break;
                case '&':  out.append("&amp;");  break;
                case '"':  out.append("&quot;"); break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int i = fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1);
    }

    private String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * Math.max(0, n));
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    // =========================== HTML wrapper / CSS ===========================

    private String wrapHtml(String content, String fileName) {
        return "<!DOCTYPE html><html><head>" +
               "<meta charset='UTF-8'>" +
               "<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=yes'>" +
               "<title>" + escape(fileName == null ? "" : fileName) + "</title>" +
               "<style>" + getDocumentCss() + "</style>" +
               "</head><body class='theme-light'>" +
               "<main class='container'>" + content + "</main>" +
               "</body></html>";
    }

    public static String getDocumentCss() {
        return
          ":root{--bg:#f5f5f5;--surface:#fff;--text:#222;--muted:#666;--accent:#1976D2;--border:#e0e0e0;}" +
          "body.theme-dark{--bg:#121212;--surface:#1e1e1e;--text:#eaeaea;--muted:#aaa;--accent:#64b5f6;--border:#333;}" +
          "html,body{margin:0;padding:0;background:var(--bg);color:var(--text);" +
          "font-family:-apple-system,Roboto,'Helvetica Neue',Arial,sans-serif;line-height:1.6;-webkit-text-size-adjust:100%;}" +
          ".container{max-width:min(920px,100%);margin:0 auto;padding:20px;background:var(--surface);" +
          "border-radius:12px;box-shadow:0 2px 14px rgba(0,0,0,0.08);}" +
          ".reader-doc{--reader-serif:Georgia,'Noto Serif',ui-serif,serif;--reader-sans:-apple-system,BlinkMacSystemFont,Roboto,'Segoe UI',sans-serif;}" +
          ".reader-word,.reader-slideshow{font-family:var(--reader-sans);font-size:clamp(16px,2.9vw,18px);" +
          "line-height:1.75;color:var(--text);text-rendering:optimizeLegibility;-webkit-font-smoothing:antialiased;}" +
          ".reader-banner{display:flex;align-items:center;gap:10px;margin:-20px -20px 16px -20px;padding:16px 20px 18px;" +
          "background:linear-gradient(135deg,rgba(25,118,210,0.14),rgba(25,118,210,0.04));" +
          "border-bottom:1px solid var(--border);border-radius:12px 12px 0 0;}" +
          "body.theme-dark .reader-banner{background:linear-gradient(135deg,rgba(100,181,246,0.12),rgba(30,30,30,0.9));}" +
          ".reader-banner-label{font-size:11px;font-weight:700;letter-spacing:0.1em;text-transform:uppercase;color:var(--accent);}" +
          ".reader-word .doc-body,.reader-slideshow .doc-body{padding:0;}" +
          ".reader-word .doc-p,.reader-word .doc-li,.reader-word .doc-cell-p{font-family:var(--reader-serif);}" +
          ".doc-body .doc-p{margin:0 0 1em;line-height:1.8;}" +
          ".doc-body .doc-heading{font-family:var(--reader-sans);margin:1.35em 0 0.55em;line-height:1.25;}" +
          ".doc-body .doc-heading:first-child{margin-top:0.3em;}" +
          ".doc-list{margin:0.6em 0 1em;padding-inline-start:1.35em;}" +
          ".doc-list .doc-li{margin:0.35em 0;line-height:1.7;}" +
          ".doc-sdt{margin:12px 0;padding:12px 14px;background:rgba(0,0,0,0.03);border-radius:8px;border:1px dashed var(--border);}" +
          "body.theme-dark .doc-sdt{background:rgba(255,255,255,0.04);}" +
          ".table-wrap{overflow-x:auto;margin:1.25em 0;border-radius:10px;border:1px solid var(--border);" +
          "background:var(--surface);box-shadow:0 1px 5px rgba(0,0,0,0.05);}" +
          ".table-wrap .doc-table{margin:0;}" +
          ".doc-th{background:rgba(25,118,210,0.1);font-weight:600;font-family:var(--reader-sans);}" +
          "body.theme-dark .doc-th{background:rgba(100,181,246,0.12);}" +
          ".doc-cell-p{margin:0.25em 0;line-height:1.55;}" +
          ".deck-meta{margin:0 0 10px;color:var(--muted);font-size:0.95em;}" +
          ".slides-deck{display:flex;flex-direction:column;gap:22px;padding:4px 0 8px;}" +
          ".ppt-slide-card{border:1px solid var(--border);border-radius:14px;overflow:hidden;" +
          "background:linear-gradient(180deg,var(--surface) 0%,rgba(0,0,0,0.02) 100%);" +
          "box-shadow:0 6px 20px rgba(0,0,0,0.07);}" +
          "body.theme-dark .ppt-slide-card{box-shadow:0 6px 24px rgba(0,0,0,0.35);" +
          "background:linear-gradient(180deg,#1e1e1e,#252525);}" +
          ".ppt-slide-meta{display:flex;align-items:center;gap:12px;padding:12px 18px;" +
          "background:rgba(25,118,210,0.09);border-bottom:1px solid var(--border);}" +
          "body.theme-dark .ppt-slide-meta{background:rgba(100,181,246,0.08);}" +
          ".ppt-slide-title{font-size:0.85em;font-weight:600;color:var(--muted);text-transform:uppercase;letter-spacing:0.06em;}" +
          ".ppt-badge{display:inline-flex;align-items:center;justify-content:center;min-width:30px;height:30px;" +
          "padding:0 10px;border-radius:999px;background:var(--accent);color:#fff;font-weight:700;font-size:13px;}" +
          ".ppt-slide-body{padding:20px 22px 26px;}" +
          ".ppt-shape{margin-bottom:18px;padding-bottom:14px;border-bottom:1px solid rgba(0,0,0,0.06);}" +
          ".ppt-shape:last-child{border-bottom:none;margin-bottom:0;padding-bottom:0;}" +
          "body.theme-dark .ppt-shape{border-bottom-color:rgba(255,255,255,0.08);}" +
          ".ppt-line{margin:0 0 12px;font-size:1.06em;line-height:1.65;font-family:var(--reader-serif);}" +
          ".ppt-line:last-child{margin-bottom:0;}" +
          ".ppt-empty,.ppt-legacy-line{color:var(--muted);}" +
          ".ppt-legacy-banner{margin:12px 0;}" +
          ".ppt-legacy-body{padding:4px 0 0;}" +
          "h1,h2,h3,h4,h5,h6{color:var(--accent);margin:24px 0 12px;font-weight:600;}" +
          "h1{font-size:1.8em;border-bottom:3px solid var(--accent);padding-bottom:8px;}" +
          "h2{font-size:1.5em;border-bottom:2px solid var(--accent);padding-bottom:6px;}" +
          "h3{font-size:1.25em;}" +
          "p{margin:12px 0;}" +
          "a{color:var(--accent);}" +
          "strong{font-weight:700;}" +
          "em{font-style:italic;}" +
          "u{text-decoration:underline;}" +
          ".doc-table,.excel-table{width:100%;border-collapse:collapse;margin:0;font-size:0.95em;}" +
          ".doc-table td,.doc-table th,.excel-table td,.excel-table th{" +
              "border:1px solid var(--border);padding:10px 14px;text-align:left;vertical-align:top;}" +
          ".excel-table th{background:rgba(25,118,210,0.08);font-weight:600;}" +
          ".doc-table tbody tr:nth-child(even),.excel-table tr:nth-child(even){background:rgba(0,0,0,0.03);}" +
          "body.theme-dark .doc-table tbody tr:nth-child(even){background:rgba(255,255,255,0.03);}" +
          ".doc-image{max-width:100%;height:auto;margin:12px auto;display:block;border-radius:6px;}" +
          ".inline-image{display:inline-block;margin:4px 8px;}" +
          ".images-section{margin-top:32px;padding-top:24px;border-top:1px solid var(--border);}" +
          "figure{margin:16px 0;text-align:center;}figcaption{color:var(--muted);font-size:0.85em;margin-top:4px;}" +
          ".sheet{margin-bottom:24px;}" +
          ".text-content,.json-content{background:rgba(0,0,0,0.04);padding:14px;border-radius:6px;" +
              "overflow-x:auto;font-family:'Courier New',monospace;font-size:13px;line-height:1.45;}" +
          "body.theme-dark .json-content{background:#0d1117;color:#a5d6a7;}" +
          ".info{background:rgba(76,175,80,0.12);color:#2e7d32;padding:12px 14px;border-left:4px solid #4caf50;border-radius:4px;margin:12px 0;}" +
          "body.theme-dark .info{color:#a5d6a7;}" +
          ".error{background:rgba(244,67,54,0.10);color:#c62828;padding:12px 14px;border-left:4px solid #f44336;border-radius:4px;margin:12px 0;}" +
          "body.theme-dark .error{color:#ef9a9a;}" +
          ".ppt-slide{border:1px solid var(--border);border-radius:8px;padding:16px;margin:16px 0;background:var(--surface);}" +
          ".ppt-slide h3{margin-top:0;}" +
          ".epub-meta{margin-bottom:24px;padding-bottom:16px;border-bottom:2px solid var(--border);}" +
          ".epub-title{margin:0 0 8px;font-size:1.6em;}" +
          ".epub-author{color:var(--muted);}" +
          ".epub-chapter{margin:24px 0;padding-top:16px;border-top:1px dashed var(--border);}" +
          ".markdown-body code{background:rgba(0,0,0,0.05);padding:2px 6px;border-radius:4px;font-family:'Courier New',monospace;}" +
          ".markdown-body pre{background:rgba(0,0,0,0.05);padding:12px;border-radius:6px;overflow-x:auto;}" +
          ".markdown-body blockquote{border-left:4px solid var(--accent);padding-left:12px;color:var(--muted);margin:12px 0;}" +
          "pre{white-space:pre-wrap;word-wrap:break-word;}" +
          "img{max-width:100%;height:auto;}" +
          ".java-src{font-family:'JetBrains Mono','Fira Code','Courier New',monospace;font-size:13px;line-height:1.45;}" +
          ".java-kw{color:#1565C0;font-weight:600;}" +
          ".java-str{color:#2E7D32;}" +
          ".java-com{color:#6A1B9A;font-style:italic;}" +
          ".java-ann{color:#C62828;}" +
          ".java-num{color:#E65100;}" +
          "body.theme-dark .java-kw{color:#90CAF9;}" +
          "body.theme-dark .java-str{color:#A5D6A7;}" +
          "body.theme-dark .java-com{color:#CE93D8;}" +
          "body.theme-dark .java-ann{color:#FFAB91;}" +
          "body.theme-dark .java-num{color:#FFCC80;}";
    }
}
