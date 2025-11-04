package com.ccs.documentreader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.opencsv.CSVReader;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

// EPUBlib тимчасово вимкнено
// import nl.siegmann.epublib.domain.Book;
// import nl.siegmann.epublib.domain.Resource;
// import nl.siegmann.epublib.epub.EpubReader;

public class DocumentReader {
    private static final String TAG = "DocumentReader";
    private Context context;

    public DocumentReader(Context context) {
        this.context = context;
        // Ініціалізація PDFBox
        PDFBoxResourceLoader.init(context);
    }

    public String readDocument(Uri uri, String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();

        try {
            switch (extension) {
                // Текстові формати
                case "txt":
                case "md":
                case "tex":
                case "bib":
                case "ini":
                case "ris":
                case "enw":
                case "rmd":
                    return readTextFile(uri);

                // Word документи
                case "doc":
                    return readDoc(uri);
                case "docx":
                case "docm":
                    return readDocx(uri);

                // Excel документи
                case "xls":
                    return readXls(uri);
                case "xlsx":
                case "xlsm":
                    return readXlsx(uri);

                // PowerPoint документи
                case "ppt":
                    return readPpt(uri);
                case "pptx":
                    return readPptx(uri);

                // PDF документи
                case "pdf":
                    return readPdf(uri);

                // RTF документи
                case "rtf":
                    return readRtf(uri);

                // ODT документи
                case "odt":
                case "ods":
                case "odp":
                case "odf":
                    return readOdf(uri);

                // CSV та подібні
                case "csv":
                    return readCsv(uri);
                case "tsv":
                    return readTsv(uri);

                // JSON
                case "json":
                    return readJson(uri);

                // XML
                case "xml":
                case "svg":
                    return readXml(uri);

                // YAML
                case "yaml":
                case "yml":
                    return readYaml(uri);

                // EPUB
                case "epub":
                    return readEpub(uri);

                // Архіви
                case "zip":
                    return listZipContents(uri);
                case "tar":
                    return listTarContents(uri);
                case "gz":
                    return readGzip(uri);
                case "xz":
                    return readXz(uri);
                case "7z":
                case "rar":
                case "iso":
                    return "Формат " + extension + " ще не реалізований для читання.";

                // Інші формати
                case "wps":
                case "pages":
                case "key":
                case "mobi":
                case "azw":
                case "fb2":
                case "ai":
                case "eps":
                case "ps":
                case "xps":
                case "dwg":
                case "dxf":
                case "nb":
                case "ipynb":
                case "p7s":
                case "p7m":
                case "sig":
                case "dif":
                case "slk":
                    return "Формат " + extension + " ще не підтримується повністю.\nФайл: " + fileName;

                default:
                    return "Невідомий формат файлу: " + extension;
            }
        } catch (Exception e) {
            Log.e(TAG, "Помилка читання файлу: " + fileName, e);
            return "Помилка читання файлу: " + e.getMessage();
        }
    }

    private String readTextFile(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private String readDoc(Uri uri) throws Exception {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             HWPFDocument document = new HWPFDocument(inputStream)) {
            WordExtractor extractor = new WordExtractor(document);
            return extractor.getText();
        }
    }

    private String readDocx(Uri uri) throws Exception {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            return extractor.getText();
        }
    }

    private String readXls(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             HSSFWorkbook workbook = new HSSFWorkbook(inputStream)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                content.append("=== Аркуш: ").append(sheet.getSheetName()).append(" ===\n\n");
                content.append(readSheet(sheet));
            }
        }
        return content.toString();
    }

    private String readXlsx(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                content.append("=== Аркуш: ").append(sheet.getSheetName()).append(" ===\n\n");
                content.append(readSheet(sheet));
            }
        }
        return content.toString();
    }

    private String readSheet(Sheet sheet) {
        StringBuilder content = new StringBuilder();
        for (Row row : sheet) {
            for (Cell cell : row) {
                CellType cellType = cell.getCellType();
                if (cellType == CellType.STRING) {
                    content.append(cell.getStringCellValue());
                } else if (cellType == CellType.NUMERIC) {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        content.append(cell.getDateCellValue());
                    } else {
                        content.append(cell.getNumericCellValue());
                    }
                } else if (cellType == CellType.BOOLEAN) {
                    content.append(cell.getBooleanCellValue());
                } else if (cellType == CellType.FORMULA) {
                    content.append(cell.getCellFormula());
                } else {
                    content.append("");
                }
                content.append("\t");
            }
            content.append("\n");
        }
        return content.toString();
    }

    private String readPpt(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             HSLFSlideShow ppt = new HSLFSlideShow(inputStream)) {
            content.append("PowerPoint презентація (.ppt)\n");
            content.append("Кількість слайдів: ").append(ppt.getSlides().size()).append("\n\n");
            content.append("Примітка: Повне читання PPT потребує додаткової обробки.");
        }
        return content.toString();
    }

    private String readPptx(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             XMLSlideShow ppt = new XMLSlideShow(inputStream)) {
            content.append("PowerPoint презентація (.pptx)\n");
            content.append("Кількість слайдів: ").append(ppt.getSlides().size()).append("\n\n");

            int slideNumber = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                content.append("--- Слайд ").append(slideNumber++).append(" ---\n");
                slide.getShapes().forEach(shape -> {
                    if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape) {
                        content.append(((org.apache.poi.xslf.usermodel.XSLFTextShape) shape).getText());
                        content.append("\n");
                    }
                });
                content.append("\n");
            }
        }
        return content.toString();
    }

    private String readPdf(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            content.append("PDF документ\n");
            content.append("Кількість сторінок: ").append(document.getNumberOfPages()).append("\n\n");
            content.append(stripper.getText(document));
        }
        return content.toString();
    }

    private String readRtf(Uri uri) throws Exception {
        // RTF читання як текст (базова підтримка)
        return readTextFile(uri);
    }

    private String readOdf(Uri uri) throws Exception {
        return "ODT/ODS/ODP формати потребують бібліотеки ODF Toolkit.\n" +
               "Рекомендується додати залежність:\n" +
               "implementation 'org.odftoolkit:simple-odf:0.9.0'";
    }

    private String readCsv(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             CSVReader reader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                for (String cell : nextLine) {
                    content.append(cell).append("\t");
                }
                content.append("\n");
            }
        }
        return content.toString();
    }

    private String readTsv(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line.replace("\t", "    ")).append("\n");
            }
        }
        return content.toString();
    }

    private String readJson(Uri uri) throws Exception {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Object json = JsonParser.parseReader(reader);
            return gson.toJson(json);
        }
    }

    private String readXml(Uri uri) throws Exception {
        return readTextFile(uri);
    }

    private String readYaml(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            Yaml yaml = new Yaml();
            Iterable<Object> documents = yaml.loadAll(inputStream);
            for (Object doc : documents) {
                content.append(yamlObjectToString(doc, 0)).append("\n");
            }
        }
        return content.toString();
    }

    private String yamlObjectToString(Object obj, int indent) {
        StringBuilder sb = new StringBuilder();
        String indentStr = "  ".repeat(indent);

        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append(indentStr).append(entry.getKey()).append(":\n");
                sb.append(yamlObjectToString(entry.getValue(), indent + 1));
            }
        } else if (obj instanceof Iterable) {
            for (Object item : (Iterable<?>) obj) {
                sb.append(indentStr).append("- ");
                sb.append(yamlObjectToString(item, indent + 1));
            }
        } else {
            sb.append(indentStr).append(obj).append("\n");
        }

        return sb.toString();
    }

    private String readEpub(Uri uri) throws Exception {
        // EPUBlib бібліотека тимчасово недоступна
        // Для повної підтримки EPUB додайте відповідну залежність
        return "EPUB формат\n\n" +
               "Підтримка EPUB електронних книг тимчасово недоступна.\n\n" +
               "Для повної підтримки потрібно додати EPUBlib бібліотеку:\n" +
               "1. Використайте альтернативну бібліотеку\n" +
               "2. Або конвертуйте EPUB в PDF для читання\n\n" +
               "Назва файлу: " + uri.getLastPathSegment();
    }

    private String listZipContents(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        content.append("=== Вміст ZIP архіву ===\n\n");

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             ZipArchiveInputStream zipInput = new ZipArchiveInputStream(inputStream)) {
            ArchiveEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                content.append(entry.getName());
                if (!entry.isDirectory()) {
                    content.append(" (").append(entry.getSize()).append(" байт)");
                }
                content.append("\n");
            }
        }

        return content.toString();
    }

    private String listTarContents(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        content.append("=== Вміст TAR архіву ===\n\n");

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             TarArchiveInputStream tarInput = new TarArchiveInputStream(inputStream)) {
            ArchiveEntry entry;
            while ((entry = tarInput.getNextEntry()) != null) {
                content.append(entry.getName());
                if (!entry.isDirectory()) {
                    content.append(" (").append(entry.getSize()).append(" байт)");
                }
                content.append("\n");
            }
        }

        return content.toString();
    }

    private String readGzip(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             GzipCompressorInputStream gzipInput = new GzipCompressorInputStream(inputStream);
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzipInput, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private String readXz(Uri uri) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             XZCompressorInputStream xzInput = new XZCompressorInputStream(inputStream);
             BufferedReader reader = new BufferedReader(new InputStreamReader(xzInput, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return "";
    }
}

