package com.ccs.documentreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Покращений читач документів з підтримкою форматування та зображень
 * Генерує HTML для відображення в WebView
 */
public class RichDocumentReader {
    private static final String TAG = "RichDocumentReader";
    private Context context;

    public RichDocumentReader(Context context) {
        this.context = context;
        PDFBoxResourceLoader.init(context);
    }

    public String readDocumentAsHtml(Uri uri, String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        
        Log.d(TAG, "========================================");
        Log.d(TAG, "Читання файлу: " + fileName);
        Log.d(TAG, "Розширення: " + extension);
        Log.d(TAG, "URI: " + uri.toString());
        Log.d(TAG, "========================================");
        
        try {
            String htmlContent;
            switch (extension) {
                case "docx":
                case "docm":
                    Log.d(TAG, ">>> Викликаємо readDocxAsHtml");
                    
                    // ДІАГНОСТИКА: Запускаємо тестовий читач
                    String testResult = DocxTestReader.testReadDocx(context, uri);
                    Log.d(TAG, "TEST RESULT:\n" + testResult);
                    
                    htmlContent = readDocxAsHtml(uri);
                    Log.d(TAG, ">>> HTML довжина: " + htmlContent.length());
                    
                    // Якщо HTML містить сирі байти, показуємо тест
                    if (htmlContent.contains("��") || htmlContent.length() < 100) {
                        htmlContent = "<pre>" + testResult + "</pre>" + htmlContent;
                    }
                    break;
                    
                case "doc":
                    Log.d(TAG, ">>> Викликаємо readDocAsHtml (старий формат)");
                    htmlContent = readDocAsHtml(uri);
                    Log.d(TAG, ">>> DOC HTML довжина: " + htmlContent.length());
                    break;
                    
                case "pdf":
                    htmlContent = readPdfAsHtml(uri);
                    break;
                    
                case "xlsx":
                case "xlsm":
                    htmlContent = readXlsxAsHtml(uri);
                    break;
                    
                case "xls":
                    htmlContent = readXlsAsHtml(uri);
                    break;
                    
                case "txt":
                case "md":
                    htmlContent = readTextAsHtml(uri);
                    break;
                    
                case "json":
                    htmlContent = readJsonAsHtml(uri);
                    break;
                    
                case "xml":
                    htmlContent = readXmlAsHtml(uri);
                    break;
                    
                case "pptx":
                    Log.d(TAG, ">>> Викликаємо readPptxAsHtml");
                    htmlContent = readPptxAsHtml(uri);
                    Log.d(TAG, ">>> PPTX HTML довжина: " + htmlContent.length());
                    break;
                    
                case "ppt":
                    Log.d(TAG, ">>> Викликаємо readPptAsHtml");
                    htmlContent = readPptAsHtml(uri);
                    break;
                    
                default:
                    htmlContent = "<div class='info'>Для формату " + extension + 
                                " використовується базове відображення</div>" +
                                "<pre>" + readPlainText(uri) + "</pre>";
            }
            
            return wrapHtml(htmlContent, fileName);
            
        } catch (Exception e) {
            Log.e(TAG, "Помилка читання файлу: " + fileName, e);
            return wrapHtml(
                "<div class='error'>" +
                "<h2>❌ Помилка</h2>" +
                "<p>Не вдалося прочитати документ</p>" +
                "<p class='error-detail'>" + e.getMessage() + "</p>" +
                "</div>", fileName);
        }
    }

    /**
     * Читання DOCX з форматуванням та зображеннями
     */
    private String readDocxAsHtml(Uri uri) throws Exception {
        StringBuilder html = new StringBuilder();
        
        Log.d(TAG, "Читання DOCX файлу...");
        
        // Перевірка валідності файлу
        if (!FileValidator.isValidDocx(context, uri)) {
            Log.e(TAG, "File is not valid DOCX!");
            return "<div class='error'>" +
                   "<h2>❌ Format Error</h2>" +
                   "<p>File is not a valid DOCX document</p>" +
                   "<p>Possible reasons:</p>" +
                   "<ul>" +
                   "<li>File is corrupted</li>" +
                   "<li>Wrong file extension</li>" +
                   "<li>File is password protected</li>" +
                   "</ul>" +
                   "</div>";
        }
        
        InputStream inputStream = null;
        XWPFDocument document = null;
        
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                throw new Exception("Не вдалося відкрити файл");
            }
            
            Log.d(TAG, "InputStream створено, відкриваємо документ...");
            document = new XWPFDocument(inputStream);
            Log.d(TAG, "Документ відкрито успішно! Параграфів: " + document.getParagraphs().size());
            
            // Перевірка чи документ порожній
            if (document.getParagraphs().isEmpty() && document.getTables().isEmpty()) {
                return "<div class='error'>Документ порожній або пошкоджений</div>";
            }
            
            // Параграфи з вбудованими зображеннями
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                html.append(paragraphToHtml(paragraph, document));
            }
            
            // Таблиці
            for (XWPFTable table : document.getTables()) {
                html.append(tableToHtml(table));
            }
            
            // Всі зображення в кінці (якщо не вбудовані)
            List<XWPFPictureData> pictures = document.getAllPictures();
            if (!pictures.isEmpty()) {
                html.append("<div class='images-section'>");
                html.append("<h3>📷 Зображення з документа (").append(pictures.size()).append(")</h3>");
                for (int i = 0; i < pictures.size(); i++) {
                    try {
                        XWPFPictureData picture = pictures.get(i);
                        byte[] imageData = picture.getData();
                        if (imageData != null && imageData.length > 0) {
                            String base64 = encodeImageToBase64(imageData);
                            String mimeType = picture.getPackagePart().getContentType();
                            html.append("<div class='image-container'>");
                            html.append("<p class='image-caption'>Зображення ").append(i + 1).append("</p>");
                            html.append("<img src='data:").append(mimeType)
                                .append(";base64,").append(base64).append("' class='doc-image' alt='Image ").append(i + 1).append("'/>");
                            html.append("</div>");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Помилка завантаження зображення " + i, e);
                        html.append("<p class='error'>Не вдалося завантажити зображення ").append(i + 1).append("</p>");
                    }
                }
                html.append("</div>");
            }
            
            return html.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Помилка читання DOCX", e);
            throw new Exception("Не вдалося прочитати DOCX файл: " + e.getMessage());
        } finally {
            try {
                if (document != null) document.close();
                if (inputStream != null) inputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "Помилка закриття ресурсів", e);
            }
        }
    }

    /**
     * Конвертація параграфа DOCX в HTML (з підтримкою зображень)
     */
    private String paragraphToHtml(XWPFParagraph paragraph, XWPFDocument document) {
        // Перевірка на вбудовані зображення в runs
        StringBuilder images = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            List<XWPFPicture> embeddedPictures = run.getEmbeddedPictures();
            if (!embeddedPictures.isEmpty()) {
                for (XWPFPicture picture : embeddedPictures) {
                    try {
                        XWPFPictureData pictureData = picture.getPictureData();
                        byte[] imageData = pictureData.getData();
                        if (imageData != null && imageData.length > 0) {
                            String base64 = encodeImageToBase64(imageData);
                            String mimeType = pictureData.getPackagePart().getContentType();
                            images.append("<img src='data:").append(mimeType)
                                .append(";base64,").append(base64)
                                .append("' class='doc-image inline-image'/>");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Помилка вбудованого зображення", e);
                    }
                }
            }
        }
        
        String textHtml = paragraphToHtmlSimple(paragraph);
        
        // Додати зображення після параграфа
        return textHtml + images.toString();
    }
    
    /**
     * Конвертація параграфа без зображень
     */
    private String paragraphToHtmlSimple(XWPFParagraph paragraph) {
        if (paragraph.getText().trim().isEmpty()) {
            return "<br/>";
        }
        
        StringBuilder html = new StringBuilder();
        String alignment = getAlignment(paragraph.getAlignment());
        
        // Визначення стилю параграфа
        String style = paragraph.getStyle();
        boolean isHeading = style != null && style.toLowerCase().contains("heading");
        
        if (isHeading) {
            int level = extractHeadingLevel(style);
            html.append("<h").append(level).append(" style='text-align:").append(alignment).append("'>");
        } else {
            html.append("<p style='text-align:").append(alignment).append("'>");
        }
        
        // Обробка runs (форматованих частин тексту)
        for (XWPFRun run : paragraph.getRuns()) {
            html.append(runToHtml(run));
        }
        
        if (isHeading) {
            int level = extractHeadingLevel(style);
            html.append("</h").append(level).append(">");
        } else {
            html.append("</p>");
        }
        
        return html.toString();
    }

    /**
     * Конвертація run (форматованої частини) в HTML
     */
    private String runToHtml(XWPFRun run) {
        // Пропустити run якщо він містить тільки зображення
        if (!run.getEmbeddedPictures().isEmpty() && run.getText(0) == null) {
            return ""; // Зображення обробляються окремо
        }
        
        String text = run.getText(0);
        if (text == null || text.isEmpty()) return "";
        
        // Для POI тексту НЕ використовуємо escapeHtml - це псує UTF-8
        // Замість цього екрануємо тільки якщо є небезпечні символи
        if (text.contains("<") || text.contains(">") || text.contains("&")) {
            text = escapeHtmlSafe(text);
        }
        // Інакше залишаємо текст як є
        
        StringBuilder html = new StringBuilder();
        boolean needsSpan = false;
        StringBuilder styleAttr = new StringBuilder();
        
        // Жирний
        if (run.isBold()) {
            html.append("<strong>");
        }
        
        // Курсив
        if (run.isItalic()) {
            html.append("<em>");
        }
        
        // Підкреслений
        if (run.getUnderline() != UnderlinePatterns.NONE) {
            html.append("<u>");
        }
        
        // Закреслений
        if (run.isStrikeThrough()) {
            html.append("<s>");
        }
        
        // Колір тексту
        String color = run.getColor();
        if (color != null && !color.equals("auto")) {
            styleAttr.append("color:#").append(color).append(";");
            needsSpan = true;
        }
        
        // Розмір шрифту
        int fontSize = run.getFontSize();
        if (fontSize > 0) {
            styleAttr.append("font-size:").append(fontSize).append("pt;");
            needsSpan = true;
        }
        
        if (needsSpan) {
            html.append("<span style='").append(styleAttr).append("'>");
        }
        
        html.append(text);
        
        if (needsSpan) {
            html.append("</span>");
        }
        
        if (run.isStrikeThrough()) {
            html.append("</s>");
        }
        
        if (run.getUnderline() != UnderlinePatterns.NONE) {
            html.append("</u>");
        }
        
        if (run.isItalic()) {
            html.append("</em>");
        }
        
        if (run.isBold()) {
            html.append("</strong>");
        }
        
        return html.toString();
    }

    /**
     * Конвертація таблиці в HTML
     */
    private String tableToHtml(XWPFTable table) {
        StringBuilder html = new StringBuilder("<table class='doc-table'>");
        
        for (XWPFTableRow row : table.getRows()) {
            html.append("<tr>");
            for (XWPFTableCell cell : row.getTableCells()) {
                html.append("<td>");
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    String text = paragraph.getText();
                    if (!text.trim().isEmpty()) {
                        // Для таблиць також використовуємо безпечне екранування
                        if (text.contains("<") || text.contains(">")) {
                            html.append(escapeHtmlSafe(text));
                        } else {
                            html.append(text);
                        }
                    }
                }
                html.append("</td>");
            }
            html.append("</tr>");
        }
        
        html.append("</table>");
        return html.toString();
    }

    /**
     * Читання PDF з рендерингом сторінок як зображень
     */
    private String readPdfAsHtml(Uri uri) throws Exception {
        StringBuilder html = new StringBuilder();
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             PDDocument document = PDDocument.load(inputStream)) {
            
            int pageCount = document.getNumberOfPages();
            html.append("<div class='pdf-info'>");
            html.append("<h3>📄 PDF документ</h3>");
            html.append("<p>Сторінок: ").append(pageCount).append("</p>");
            html.append("</div>");
            
            // Рендеринг перших кількох сторінок
            PDFRenderer renderer = new PDFRenderer(document);
            int maxPages = Math.min(10, pageCount); // Обмеження для продуктивності
            
            for (int i = 0; i < maxPages; i++) {
                Bitmap bitmap = renderer.renderImage(i, 2.0f); // 2x якість
                String base64 = bitmapToBase64(bitmap);
                
                html.append("<div class='pdf-page'>");
                html.append("<p class='page-number'>Сторінка ").append(i + 1).append("</p>");
                html.append("<img src='data:image/png;base64,").append(base64)
                    .append("' class='pdf-page-image'/>");
                html.append("</div>");
                
                bitmap.recycle();
            }
            
            if (pageCount > maxPages) {
                html.append("<div class='info'>Показано перші ")
                    .append(maxPages).append(" з ").append(pageCount)
                    .append(" сторінок</div>");
            }
        }
        
        return html.toString();
    }

    /**
     * Читання Excel з HTML таблицями
     */
    private String readXlsxAsHtml(Uri uri) throws Exception {
        StringBuilder html = new StringBuilder();
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                html.append("<div class='sheet'>");
                String sheetName = sheet.getSheetName();
                // Назву аркушу НЕ екрануємо якщо немає небезпечних символів
                if (sheetName != null && (sheetName.contains("<") || sheetName.contains(">"))) {
                    html.append("<h3>📊 ").append(escapeHtmlSafe(sheetName)).append("</h3>");
                } else {
                    html.append("<h3>📊 ").append(sheetName).append("</h3>");
                }
                html.append(sheetToHtml(sheet));
                html.append("</div>");
            }
        }
        
        return html.toString();
    }

    private String readXlsAsHtml(Uri uri) throws Exception {
        StringBuilder html = new StringBuilder();
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             HSSFWorkbook workbook = new HSSFWorkbook(inputStream)) {
            
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                html.append("<div class='sheet'>");
                String sheetName = sheet.getSheetName();
                // Назву аркушу НЕ екрануємо якщо немає небезпечних символів
                if (sheetName != null && (sheetName.contains("<") || sheetName.contains(">"))) {
                    html.append("<h3>📊 ").append(escapeHtmlSafe(sheetName)).append("</h3>");
                } else {
                    html.append("<h3>📊 ").append(sheetName).append("</h3>");
                }
                html.append(sheetToHtml(sheet));
                html.append("</div>");
            }
        }
        
        return html.toString();
    }

    private String sheetToHtml(Sheet sheet) {
        StringBuilder html = new StringBuilder("<table class='excel-table'>");
        
        int maxCols = 0;
        for (Row row : sheet) {
            maxCols = Math.max(maxCols, row.getLastCellNum());
        }
        
        for (Row row : sheet) {
            html.append("<tr>");
            for (int i = 0; i < maxCols; i++) {
                Cell cell = row.getCell(i);
                html.append("<td>");
                if (cell != null) {
                    html.append(cellToHtml(cell));
                }
                html.append("</td>");
            }
            html.append("</tr>");
        }
        
        html.append("</table>");
        return html.toString();
    }

    private String cellToHtml(Cell cell) {
        CellType cellType = cell.getCellType();
        
        if (cellType == CellType.STRING) {
            String value = cell.getStringCellValue();
            // Для комірок також не екрануємо якщо немає небезпечних символів
            if (value != null && (value.contains("<") || value.contains(">"))) {
                return escapeHtmlSafe(value);
            }
            return value;
        } else if (cellType == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toString();
            } else {
                double value = cell.getNumericCellValue();
                if (value == (long) value) {
                    return String.valueOf((long) value);
                } else {
                    return String.format("%.2f", value);
                }
            }
        } else if (cellType == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        } else if (cellType == CellType.FORMULA) {
            return "<em>" + cell.getCellFormula() + "</em>";
        }
        
        return "";
    }

    /**
     * Читання текстових файлів
     */
    private String readTextAsHtml(Uri uri) throws Exception {
        StringBuilder html = new StringBuilder("<pre class='text-content'>");
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                html.append(escapeHtml(line)).append("\n");
            }
        }
        
        html.append("</pre>");
        return html.toString();
    }

    /**
     * Читання JSON з підсвічуванням
     */
    private String readJsonAsHtml(Uri uri) throws Exception {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Object json = JsonParser.parseReader(reader);
            String formatted = gson.toJson(json);
            
            return "<pre class='json-content'>" + escapeHtml(formatted) + "</pre>";
        }
    }

    /**
     * Читання XML з підсвічуванням
     */
    private String readXmlAsHtml(Uri uri) throws Exception {
        return readTextAsHtml(uri);
    }

    private String readDocAsHtml(Uri uri) throws Exception {
        Log.d(TAG, "Читання DOC файлу (старий формат)...");
        
        StringBuilder html = new StringBuilder();
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             HWPFDocument document = new HWPFDocument(inputStream)) {
            
            Log.d(TAG, "DOC документ відкрито успішно!");
            
            // Отримуємо текст з документа
            Range range = document.getRange();
            int numParagraphs = range.numParagraphs();
            
            Log.d(TAG, "Кількість параграфів: " + numParagraphs);
            
            html.append("<div class='doc-content'>");
            html.append("<p class='info'>📄 DOC документ (старий формат Word)</p>");
            
            for (int i = 0; i < numParagraphs; i++) {
                Paragraph para = range.getParagraph(i);
                String text = para.text();
                
                // Логування першого параграфа для діагностики
                if (i == 0 && !text.isEmpty()) {
                    Log.d(TAG, "Перший параграф: [" + text + "]");
                    Log.d(TAG, "Довжина: " + text.length());
                    if (text.length() > 0) {
                        Log.d(TAG, "Перший символ код: " + (int)text.charAt(0));
                    }
                }
                
                if (!text.trim().isEmpty()) {
                    html.append("<p>");
                    
                    // НЕ екрануємо якщо немає небезпечних символів
                    if (text.contains("<") || text.contains(">")) {
                        html.append(escapeHtmlSafe(text));
                    } else {
                        html.append(text);
                    }
                    
                    html.append("</p>");
                } else {
                    html.append("<br/>");
                }
            }
            
            html.append("</div>");
            
            return html.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Помилка читання DOC", e);
            return "<div class='error'>" +
                   "<h2>❌ Помилка читання DOC</h2>" +
                   "<p>" + e.getMessage() + "</p>" +
                   "<p>Можливо файл пошкоджений або має неправильний формат</p>" +
                   "</div>";
        }
    }

    /**
     * Читання PPTX презентації
     */
    private String readPptxAsHtml(Uri uri) throws Exception {
        Log.d(TAG, "Читання PPTX файлу...");
        
        StringBuilder html = new StringBuilder();
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(inputStream)) {
            
            Log.d(TAG, "PPTX документ відкрито успішно!");
            
            List<org.apache.poi.xslf.usermodel.XSLFSlide> slides = ppt.getSlides();
            
            html.append("<div class='ppt-content'>");
            html.append("<div class='info'>🎭 PowerPoint презентація (.pptx)</div>");
            html.append("<p><strong>Кількість слайдів:</strong> ").append(slides.size()).append("</p>");
            
            Log.d(TAG, "Кількість слайдів: " + slides.size());
            
            int slideNum = 1;
            for (org.apache.poi.xslf.usermodel.XSLFSlide slide : slides) {
                html.append("<div class='ppt-slide'>");
                html.append("<h3>📄 Слайд ").append(slideNum).append("</h3>");
                
                try {
                    // Простіший підхід - витягуємо текст через getText() кожного shape окремо
                    // Це уникає проблеми з AWT при ініціалізації всіх shapes одразу
                    
                    boolean hasText = false;
                    org.openxmlformats.schemas.presentationml.x2006.main.CTSlide ctSlide = slide.getXmlObject();
                    
                    if (ctSlide != null && ctSlide.getCSld() != null && 
                        ctSlide.getCSld().getSpTree() != null) {
                        
                        // Отримуємо XML shapes
                        org.openxmlformats.schemas.presentationml.x2006.main.CTGroupShape spTree = 
                            ctSlide.getCSld().getSpTree();
                        
                        // Обробляємо кожен shape окремо
                        if (spTree.getSpList() != null) {
                            for (int i = 0; i < spTree.getSpList().size(); i++) {
                                try {
                                    org.openxmlformats.schemas.presentationml.x2006.main.CTShape ctShape = 
                                        spTree.getSpList().get(i);
                                    
                                    // Витягуємо текст з txBody
                                    if (ctShape.getTxBody() != null) {
                                        String shapeText = extractTextFromTxBody(ctShape.getTxBody());
                                        if (shapeText != null && !shapeText.trim().isEmpty()) {
                                            html.append("<p>");
                                            if (shapeText.contains("<") || shapeText.contains(">")) {
                                                html.append(escapeHtmlSafe(shapeText));
                                            } else {
                                                html.append(shapeText);
                                            }
                                            html.append("</p>");
                                            hasText = true;
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Пропуск shape " + i + ": " + e.getMessage());
                                }
                            }
                        }
                    }
                    
                    if (!hasText) {
                        html.append("<p class='info'>Слайд без тексту (можливо тільки зображення або діаграми)</p>");
                    }
                    
                } catch (Exception e) {
                    html.append("<p class='error'>Помилка: ").append(e.getMessage()).append("</p>");
                    Log.e(TAG, "Помилка читання слайду " + slideNum, e);
                }
                
                html.append("</div>");
                slideNum++;
            }
            
            html.append("</div>");
            
            return html.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Помилка читання PPTX", e);
            return "<div class='error'>" +
                   "<h2>❌ Помилка читання PPTX</h2>" +
                   "<p>" + e.getMessage() + "</p>" +
                   "<p>Можливо файл пошкоджений або має неправильний формат</p>" +
                   "</div>";
        }
    }
    
    /**
     * Витягування тексту з CTTextBody (без використання AWT)
     */
    private String extractTextFromTxBody(org.openxmlformats.schemas.drawingml.x2006.main.CTTextBody txBody) {
        if (txBody == null) return "";
        
        StringBuilder text = new StringBuilder();
        
        try {
            // Отримуємо всі параграфи
            for (int i = 0; i < txBody.sizeOfPArray(); i++) {
                org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraph para = txBody.getPArray(i);
                
                if (para != null) {
                    // Отримуємо всі runs (текстові частини)
                    for (int j = 0; j < para.sizeOfRArray(); j++) {
                        org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun run = para.getRArray(j);
                        
                        if (run != null && run.getT() != null) {
                            text.append(run.getT());
                        }
                    }
                    
                    // Якщо є текст, додаємо новий рядок після параграфа
                    if (text.length() > 0 && i < txBody.sizeOfPArray() - 1) {
                        text.append("\n");
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Помилка витягування тексту з txBody: " + e.getMessage());
        }
        
        return text.toString();
    }
    
    /**
     * Читання PPT презентації (старий формат)
     */
    private String readPptAsHtml(Uri uri) throws Exception {
        Log.d(TAG, "Читання PPT файлу (старий формат)...");
        
        return "<div class='info'>" +
               "<h3>🎭 PowerPoint презентація (.ppt)</h3>" +
               "<p>Старий формат PPT має обмежену підтримку.</p>" +
               "<p>Рекомендується конвертувати у PPTX для кращого відображення.</p>" +
               "</div>";
    }

    private String readPlainText(Uri uri) throws Exception {
        StringBuilder text = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
        }
        return text.toString();
    }

    /**
     * Обгортка HTML з CSS стилями
     */
    private String wrapHtml(String content, String fileName) {
        return "<!DOCTYPE html>" +
                "<html><head>" +
                "<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=yes'>" +
                "<title>" + escapeHtml(fileName) + "</title>" +
                "<style>" + getCss() + "</style>" +
                "</head><body>" +
                "<div class='container'>" +
                content +
                "</div>" +
                "</body></html>";
    }

    /**
     * CSS стилі для документа
     */
    private String getCss() {
        return
                "body { margin: 0; padding: 16px; font-family: 'Roboto', sans-serif; line-height: 1.6; color: #333; background: #f5f5f5; }" +
                ".container { max-width: 900px; margin: 0 auto; background: white; padding: 24px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }" +
                "h1, h2, h3, h4, h5, h6 { color: #1976D2; margin-top: 24px; margin-bottom: 12px; font-weight: 600; }" +
                "h1 { font-size: 2em; border-bottom: 3px solid #1976D2; padding-bottom: 8px; }" +
                "h2 { font-size: 1.75em; border-bottom: 2px solid #1976D2; padding-bottom: 6px; }" +
                "h3 { font-size: 1.5em; }" +
                "p { margin: 12px 0; text-align: justify; }" +
                "strong { font-weight: 700; color: #000; }" +
                "em { font-style: italic; }" +
                "u { text-decoration: underline; }" +
                ".doc-table, .excel-table { width: 100%; border-collapse: collapse; margin: 16px 0; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }" +
                ".doc-table td, .excel-table td, .doc-table th, .excel-table th { border: 1px solid #ddd; padding: 12px; text-align: left; }" +
                ".doc-table tr:nth-child(even), .excel-table tr:nth-child(even) { background-color: #f9f9f9; }" +
                ".doc-table tr:hover, .excel-table tr:hover { background-color: #e3f2fd; }" +
                ".doc-image { max-width: 100%; height: auto; margin: 16px auto; display: block; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.15); }" +
                ".inline-image { margin: 8px 0; }" +
                ".image-container { margin: 16px 0; text-align: center; }" +
                ".image-caption { font-weight: 600; color: #1976D2; margin-bottom: 8px; font-size: 14px; }" +
                ".images-section { margin-top: 32px; padding-top: 24px; border-top: 2px solid #e0e0e0; }" +
                ".pdf-page { margin: 24px 0; padding: 16px; background: #fafafa; border-radius: 8px; }" +
                ".pdf-page-image { width: 100%; height: auto; border: 1px solid #ddd; border-radius: 4px; }" +
                ".page-number { font-weight: 600; color: #1976D2; margin-bottom: 8px; }" +
                ".pdf-info { background: #e3f2fd; padding: 16px; border-radius: 8px; margin-bottom: 24px; }" +
                ".sheet { margin-bottom: 32px; }" +
                ".text-content { background: #f5f5f5; padding: 16px; border-radius: 8px; overflow-x: auto; font-family: 'Courier New', monospace; font-size: 14px; line-height: 1.4; }" +
                ".json-content { background: #263238; color: #aed581; padding: 16px; border-radius: 8px; overflow-x: auto; font-family: 'Courier New', monospace; font-size: 14px; }" +
                ".info { background: #e8f5e9; color: #2e7d32; padding: 16px; border-radius: 8px; border-left: 4px solid #4caf50; margin: 16px 0; }" +
                ".error { background: #ffebee; color: #c62828; padding: 16px; border-radius: 8px; border-left: 4px solid #f44336; margin: 16px 0; }" +
                ".error-detail { font-family: monospace; font-size: 12px; margin-top: 8px; opacity: 0.8; }" +
                ".doc-content { margin: 16px 0; }" +
                ".doc-content p { margin: 8px 0; line-height: 1.6; }" +
                ".ppt-content { margin: 16px 0; }" +
                ".ppt-slide { background: #fff; border: 2px solid #1976D2; border-radius: 8px; padding: 20px; margin: 16px 0; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }" +
                ".ppt-slide h3 { color: #1976D2; margin-top: 0; border-bottom: 2px solid #1976D2; padding-bottom: 8px; }" +
                ".ppt-slide p { margin: 12px 0; font-size: 16px; line-height: 1.8; }" +
                "pre { white-space: pre-wrap; word-wrap: break-word; }";
    }

    // Допоміжні методи
    
    private String getAlignment(ParagraphAlignment alignment) {
        if (alignment == null) return "left";
        switch (alignment) {
            case CENTER: return "center";
            case RIGHT: return "right";
            case BOTH: return "justify";
            default: return "left";
        }
    }

    private int extractHeadingLevel(String style) {
        if (style == null) return 3;
        if (style.toLowerCase().contains("heading1")) return 1;
        if (style.toLowerCase().contains("heading2")) return 2;
        if (style.toLowerCase().contains("heading3")) return 3;
        if (style.toLowerCase().contains("heading4")) return 4;
        return 3;
    }

    private String encodeImageToBase64(byte[] imageData) {
        return Base64.encodeToString(imageData, Base64.NO_WRAP);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    /**
     * Безпечне екранування HTML для UTF-8 тексту
     */
    private String escapeHtmlSafe(String text) {
        if (text == null) return "";
        
        // Використовуємо StringBuilder для збереження UTF-8
        StringBuilder result = new StringBuilder(text.length() + 50);
        
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            
            // Екрануємо ТІЛЬКИ небезпечні для HTML символи
            switch (ch) {
                case '<':
                    result.append("&lt;");
                    break;
                case '>':
                    result.append("&gt;");
                    break;
                case '&':
                    result.append("&amp;");
                    break;
                case '"':
                    result.append("&quot;");
                    break;
                default:
                    // Всі інші символи, включаючи кирилицю, залишаємо
                    result.append(ch);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Старий метод для сумісності
     */
    private String escapeHtml(String text) {
        return escapeHtmlSafe(text);
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return "";
    }
}

