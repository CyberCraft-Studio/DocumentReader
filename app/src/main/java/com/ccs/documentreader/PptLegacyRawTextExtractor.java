package com.ccs.documentreader;

import org.apache.poi.hslf.record.CString;
import org.apache.poi.hslf.record.Record;
import org.apache.poi.hslf.record.RecordTypes;
import org.apache.poi.hslf.record.TextBytesAtom;
import org.apache.poi.hslf.record.TextCharsAtom;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LittleEndian;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Текст із класичного .ppt без звернень до {@code HSLFSlide#getShapes()} та інших API,
 * що тягнуть {@code java.awt} (на Android це дає {@link NoClassDefFoundError}).
 * <p>
 * Логіка відповідає {@code org.apache.poi.hslf.extractor.QuickButCruddyTextExtractor}
 * (Apache POI 5.2.3): ім'я потоку взято як літерал, нормалізація тексту — без
 * {@code HSLFTextParagraph.toExternalString} (той клас імпортує {@code java.awt.Color}).
 */
public final class PptLegacyRawTextExtractor {

    /** Як у {@link org.apache.poi.hslf.usermodel.HSLFSlideShow#POWERPOINT_DOCUMENT}. */
    private static final String POWERPOINT_DOCUMENT_ENTRY = "PowerPoint Document";

    private final InputStream sourceStream;
    private final byte[] pptContents;

    public PptLegacyRawTextExtractor(InputStream iStream) throws IOException {
        sourceStream = iStream;
        POIFSFileSystem poifs = new POIFSFileSystem(iStream);
        try {
            try (InputStream pptIs = poifs.createDocumentInputStream(POWERPOINT_DOCUMENT_ENTRY)) {
                pptContents = IOUtils.toByteArray(pptIs);
            }
        } finally {
            poifs.close();
        }
    }

    public void close() throws IOException {
        if (sourceStream != null) {
            sourceStream.close();
        }
    }

    public String getTextAsString() {
        StringBuilder ret = new StringBuilder();
        for (String text : getTextAsVector()) {
            ret.append(text);
            if (!text.endsWith("\n")) {
                ret.append('\n');
            }
        }
        return ret.toString();
    }

    public List<String> getTextAsVector() {
        List<String> textV = new ArrayList<>();
        int walkPos = 0;
        while (walkPos != -1) {
            walkPos = findTextRecords(walkPos, textV);
        }
        return textV;
    }

    private int findTextRecords(int startPos, List<String> textV) {
        int len = (int) LittleEndian.getUInt(pptContents, startPos + 4);
        byte opt = pptContents[startPos];
        int container = opt & 0x0f;
        if (container == 0x0f) {
            return (startPos + 8);
        }
        int type = LittleEndian.getUShort(pptContents, startPos + 2);

        if (type == RecordTypes.TextBytesAtom.typeID) {
            TextBytesAtom tba = (TextBytesAtom) Record.createRecordForType(
                type, pptContents, startPos, len + 8);
            textV.add(pptRawToExternal(tba.getText()));
        }
        if (type == RecordTypes.TextCharsAtom.typeID) {
            TextCharsAtom tca = (TextCharsAtom) Record.createRecordForType(
                type, pptContents, startPos, len + 8);
            textV.add(pptRawToExternal(tca.getText()));
        }
        if (type == RecordTypes.CString.typeID) {
            CString cs = (CString) Record.createRecordForType(type, pptContents, startPos, len + 8);
            String text = cs.getText();
            if (!"___PPT10".equals(text) && !"Default Design".equals(text)) {
                textV.add(text);
            }
        }

        int newPos = (startPos + 8 + len);
        if (newPos > (pptContents.length - 8)) {
            newPos = -1;
        }
        return newPos;
    }

    /** Еквівалент {@code HSLFTextParagraph.toExternalString(raw, -1)}. */
    private static String pptRawToExternal(String rawText) {
        if (rawText == null) {
            return "";
        }
        String text = rawText.replace('\r', '\n');
        final char repl = '\n';
        return text.replace('\u000b', repl);
    }
}
