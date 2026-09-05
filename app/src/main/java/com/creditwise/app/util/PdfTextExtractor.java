package com.creditwise.app.util;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;

/** Extracts the raw text layer from a PDF statement. */
public final class PdfTextExtractor {

    private PdfTextExtractor() {}

    public static String extract(InputStream in) throws IOException {
        try (PDDocument doc = PDDocument.load(in)) {
            if (doc.isEncrypted()) {
                throw new IOException("PDF is encrypted");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            return stripper.getText(doc);
        }
    }
}
