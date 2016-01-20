package com.itextpdf.core.parser;

import com.itextpdf.basics.geom.Rectangle;
import com.itextpdf.core.pdf.PdfDocument;
import com.itextpdf.core.pdf.PdfReader;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class TextExtractorUnicodeIdentityTest {

    private static final String sourceFolder = "./src/test/resources/com/itextpdf/core/parser/TextExtractorUnicodeIdentityTest/";

    @Test
    public void test() throws IOException {
        PdfDocument pdfDocument = new PdfDocument(new PdfReader(sourceFolder + "user10.pdf"));

        Rectangle rectangle = new Rectangle(71, 792 - 84, 225, 792 - 75);
        EventFilter filter = new TextRegionEventFilter(rectangle);
        String txt = TextExtractor.getTextFromPage(pdfDocument.getPage(1), new FilteredTextEventListener(new LocationTextExtractionStrategy(), filter));
        Assert.assertEquals("Pname Dname Email Address", txt);
    }

}
