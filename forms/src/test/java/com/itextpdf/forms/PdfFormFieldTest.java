package com.itextpdf.forms;

import com.itextpdf.core.geom.Rectangle;
import com.itextpdf.core.pdf.*;
import com.itextpdf.core.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.core.testutils.CompareTool;
import com.itextpdf.forms.formfields.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class PdfFormFieldTest {

    static final public String sourceFolder = "./src/test/resources/com/itextpdf/forms/PdfFormFieldTest/";
    static final public String destinationFolder = "./target/test/com/itextpdf/forms/PdfFormFieldTest/";

    @BeforeClass
    static public void beforeClass() {
        new File(destinationFolder).mkdirs();
    }

    @Test
    public void formFieldTest01() throws IOException {
        PdfReader reader = new PdfReader(sourceFolder + "formFieldFile.pdf");
        PdfDocument pdfDoc = new PdfDocument(reader);

        PdfAcroForm form = PdfAcroForm.getAcroForm(pdfDoc, false);

        ArrayList<PdfFormField> fields = form.getFormFields();
        PdfFormField field = fields.get(1);

        Assert.assertTrue(fields.size() == 4);
        Assert.assertTrue(field.getFieldName().toUnicodeString().equals("Text1"));
        Assert.assertTrue(field.getValue().toString().equals("TestField"));
    }

    @Test
    public void formFieldTest02() throws IOException, InterruptedException {
        String filename = destinationFolder + "formFieldTest02.pdf";
        PdfWriter writer = new PdfWriter(new FileOutputStream(filename));
        PdfDocument pdfDoc = new PdfDocument(writer);

        PdfAcroForm form = PdfAcroForm.getAcroForm(pdfDoc, true);
        form.put(PdfName.NeedAppearances, new PdfBoolean(true));

        PdfPage page = pdfDoc.addNewPage();
        Rectangle rect = new Rectangle(210, 490, 150, 22);
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(pdfDoc, rect);
        page.addAnnotation(annot);
        PdfTextFormField field = new PdfTextFormField(pdfDoc, annot);

        field.setFieldName("TestField");
        field.setValue(new PdfString("some value"));
        form.addField(field);

        pdfDoc.close();

        CompareTool compareTool = new CompareTool();
        String errorMessage = compareTool.compareByContent(filename, sourceFolder + "cmp_formFieldTest02.pdf", destinationFolder, "diff_");
        if (errorMessage != null) {
            Assert.fail(errorMessage);
        }
    }

    @Test
    public void formFieldTest03() throws IOException, InterruptedException {
        PdfReader reader = new PdfReader(sourceFolder + "formFieldFile.pdf");
        String filename = destinationFolder + "formFieldTest03.pdf";
        PdfWriter writer = new PdfWriter(new FileOutputStream(filename));
        PdfDocument pdfDoc = new PdfDocument(reader, writer);

        PdfAcroForm form = PdfAcroForm.getAcroForm(pdfDoc, true);
        form.put(PdfName.NeedAppearances, new PdfBoolean(true));

        PdfPage page = pdfDoc.getFirstPage();
        Rectangle rect = new Rectangle(210, 490, 150, 22);
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(pdfDoc, rect);
        page.addAnnotation(annot);
        PdfTextFormField field = new PdfTextFormField(pdfDoc, annot);

        field.setFieldName("TestField");
        field.setValue(new PdfString("some value"));
        form.addField(field);

        pdfDoc.close();

        CompareTool compareTool = new CompareTool();
        String errorMessage = compareTool.compareByContent(filename, sourceFolder + "cmp_formFieldTest03.pdf", destinationFolder, "diff_");
        if (errorMessage != null) {
            Assert.fail(errorMessage);
        }
    }

    @Test
    public void choiceFieldTest01() throws IOException, InterruptedException {

        String filename = destinationFolder + "choiceFieldTest01.pdf";
        PdfWriter writer = new PdfWriter(new FileOutputStream(filename));
        PdfDocument pdfDoc = new PdfDocument(writer);

        PdfAcroForm form = PdfAcroForm.getAcroForm(pdfDoc, true);
        form.put(PdfName.NeedAppearances, new PdfBoolean(true));

        PdfPage page = pdfDoc.addNewPage();
        Rectangle rect = new Rectangle(210, 490, 150, 20);
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(pdfDoc, rect);
        page.addAnnotation(annot);
        PdfChoiceFormField choice = new PdfChoiceFormField(pdfDoc, annot);

        PdfArray array = new PdfArray();
        array.add(new PdfString("First Item"));
        array.add(new PdfString("Second Item"));
        array.add(new PdfString("Third Item"));
        array.add(new PdfString("Fourth Item"));
        choice.setFieldName("TestField");
        choice.setOptions(array);
        choice.setFieldFlag(PdfChoiceFormField.COMBO);
        choice.setValue(array.get(2));
        form.addField(choice);

        Rectangle rect1 = new Rectangle(210, 250, 150, 16);
        PdfWidgetAnnotation annot1 = new PdfWidgetAnnotation(pdfDoc, rect1);
        page.addAnnotation(annot1);
        PdfChoiceFormField choice1 = new PdfChoiceFormField(pdfDoc, annot1);
        choice1.setOptions(array);
        choice1.setFieldName("TestField1");
        choice1.setFieldFlag(PdfChoiceFormField.MULTI_SELECT);
        form.addField(choice1);

        pdfDoc.close();

        CompareTool compareTool = new CompareTool();
        String errorMessage = compareTool.compareByContent(filename, sourceFolder + "cmp_choiceFieldTest01.pdf", destinationFolder, "diff_");
        if (errorMessage != null) {
            Assert.fail(errorMessage);
        }
    }
}