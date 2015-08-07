package com.itextpdf.forms.fields;

import com.itextpdf.basics.PdfException;
import com.itextpdf.basics.font.FontConstants;
import com.itextpdf.basics.font.PdfEncodings;
import com.itextpdf.basics.font.Type1Font;
import com.itextpdf.basics.io.PdfTokenizer;
import com.itextpdf.basics.io.RandomAccessFileOrArray;
import com.itextpdf.basics.io.RandomAccessSourceFactory;
import com.itextpdf.canvas.PdfCanvas;
import com.itextpdf.canvas.PdfCanvasConstants;
import com.itextpdf.canvas.color.Color;
import com.itextpdf.canvas.color.DeviceCmyk;
import com.itextpdf.canvas.color.DeviceGray;
import com.itextpdf.canvas.color.DeviceRgb;
import com.itextpdf.core.font.PdfFont;
import com.itextpdf.core.font.PdfType1Font;
import com.itextpdf.core.geom.Rectangle;
import com.itextpdf.core.pdf.*;
import com.itextpdf.core.pdf.action.PdfAction;
import com.itextpdf.core.pdf.annot.PdfAnnotation;
import com.itextpdf.core.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.core.pdf.xobject.PdfFormXObject;

import java.io.IOException;
import java.util.*;


public class PdfFormField extends PdfObjectWrapper<PdfDictionary> {

    public static final int DEFAULT_FONT_SIZE = 12;
    public static final int DA_FONT = 0;
    public static final int DA_SIZE = 1;
    public static final int DA_COLOR = 2;

    /** A field with the symbol check */
    public static final int TYPE_CHECK = 1;
    /** A field with the symbol circle */
    public static final int TYPE_CIRCLE = 2;
    /** A field with the symbol cross */
    public static final int TYPE_CROSS = 3;
    /** A field with the symbol diamond */
    public static final int TYPE_DIAMOND = 4;
    /** A field with the symbol square */
    public static final int TYPE_SQUARE = 5;
    /** A field with the symbol star */
    public static final int TYPE_STAR = 6;

    protected static String typeChars[] = {"4", "l", "8", "u", "n", "H"};

    protected String text;
    protected PdfFont font;
    protected int checkType;
    protected float borderWidth = 1;

    public PdfFormField() {
        this(new PdfDictionary());
        put(PdfName.FT, getFormType());
    }

    public PdfFormField(PdfWidgetAnnotation widget) {
        this(new PdfDictionary());
        addKid(widget);
        put(PdfName.FT, getFormType());
    }

    protected PdfFormField(PdfDictionary pdfObject) {
        super(pdfObject);
    }

    /**
     * Makes a field flag by bit position. Bit positions are numbered 1 to 32.
     * But position 1 corresponds to flag 1, position 3 corresponds to flag 4 etc.
     * @param bitPosition bit position of a flag in range 1 to 32 from the pdf specification.
     * @return corresponding field flag.
     */
    public static int makeFieldFlag(int bitPosition) {
        return (1 << (bitPosition - 1));
    }

    public static PdfFormField createEmptyField(PdfDocument doc) {
        PdfFormField field = new PdfFormField().makeIndirect(doc);
        return field;
    }

    public static PdfButtonFormField createButton(PdfDocument doc, Rectangle rect, int flags) {
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(doc, rect);
        PdfButtonFormField field = new PdfButtonFormField(annot).makeIndirect(doc);
        field.setFieldFlags(flags);
        return field;
    }

    public static PdfButtonFormField createButton(PdfDocument doc, int flags) {
        PdfButtonFormField field = new PdfButtonFormField().makeIndirect(doc);
        field.setFieldFlags(flags);
        return field;
    }

    public static PdfTextFormField createText(PdfDocument doc) {
        return new PdfTextFormField().makeIndirect(doc);
    }

    public static PdfTextFormField createText(PdfDocument doc, Rectangle rect) {
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(doc, rect);
        PdfTextFormField field = new PdfTextFormField(annot).makeIndirect(doc);

        return field;

    }

    public static PdfTextFormField createText(PdfDocument doc, Rectangle rect, String value, String name) {
        try{
            return createText(doc, rect, PdfFont.getDefaultFont(doc), DEFAULT_FONT_SIZE, value, name);
        } catch (IOException e) {
            throw new PdfException(e.getLocalizedMessage());
        }
    }

    public static PdfTextFormField createText(PdfDocument doc, Rectangle rect, PdfFont font, int fontSize, String value, String name) {
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(doc, rect);
        PdfTextFormField field = new PdfTextFormField(annot).makeIndirect(doc);
        field.setValue(value);
        field.setFieldName(name);

        PdfFormXObject xObject = field.drawTextAppearance(rect, font, fontSize, value);
        xObject.getResources().addFont(font);
        annot.setNormalAppearance(xObject.getPdfObject());

        return field;
    }

    public static PdfChoiceFormField createChoice(PdfDocument doc, int flags) {
        PdfChoiceFormField field = new PdfChoiceFormField().makeIndirect(doc);
        field.setFieldFlags(flags);
        return field;
    }

    public static PdfChoiceFormField createChoice(PdfDocument doc, Rectangle rect, int flags) {
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(doc, rect);
        PdfChoiceFormField field = new PdfChoiceFormField(annot).makeIndirect(doc);
        field.setFieldFlags(flags);
        return field;
    }

    public static PdfChoiceFormField createChoice(PdfDocument doc, Rectangle rect, PdfArray options, String value, String name, int flags) {
        try{
            return createChoice(doc, rect, options, value, name, PdfFont.getDefaultFont(doc), DEFAULT_FONT_SIZE, flags);
        } catch (IOException e) {
            throw new PdfException(e.getLocalizedMessage());
        }
    }

    public static PdfChoiceFormField createChoice(PdfDocument doc, Rectangle rect, PdfArray options, String value, String name, PdfFont font, int fontSize, int flags) {
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(doc, rect);
        PdfChoiceFormField field = new PdfChoiceFormField(annot).makeIndirect(doc);
        field.put(PdfName.Opt, options);
        field.setFieldFlags(flags);
        field.setFieldName(name);
        field.setValue(value);

        PdfFormXObject xObject = field.drawMultiLineTextAppearance(rect, font, fontSize, value);
        xObject.getResources().addFont(font);
        annot.setNormalAppearance(xObject.getPdfObject());

        return field;
    }

    public static PdfSignatureFormField createSignature(PdfDocument doc) {
        return new PdfSignatureFormField().makeIndirect(doc);
    }

    public static PdfSignatureFormField createSignature(PdfDocument doc, Rectangle rect) {
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(doc, rect);
        return new PdfSignatureFormField(annot).makeIndirect(doc);
    }

    public static PdfButtonFormField createRadioGroup(PdfDocument doc, String name, String defaultValue) {
        PdfButtonFormField radio = createButton(doc, PdfButtonFormField.FF_RADIO);
        radio.setFieldName(name);
        radio.put(PdfName.V, new PdfName(defaultValue));
        return radio;
    }

    public static PdfFormField createRadioButton(PdfDocument doc, Rectangle rect, PdfButtonFormField radioGroup, String value) {
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(doc, rect);
        PdfFormField radio = new PdfFormField(annot).makeIndirect(doc);
        String name = radioGroup.getValue().toString().substring(1);
        if (name.equals(value)) {
            annot.setAppearanceState(new PdfName(value));
        } else {
            annot.setAppearanceState(new PdfName("Off"));
        }
        radio.drawRadioAppearance(rect.getWidth(), rect.getHeight(), value);
        radioGroup.addKid(radio);
        return radio;
    }

    public static PdfButtonFormField createPushButton(PdfDocument doc, Rectangle rect, String name, String caption) {
        PdfButtonFormField field;
        try {
            field = createPushButton(doc, rect, name, caption, PdfFont.getDefaultFont(doc), DEFAULT_FONT_SIZE);
        } catch (IOException e) {
            throw new PdfException(e.getLocalizedMessage());
        }
        return field;
    }

    public static PdfButtonFormField createPushButton(PdfDocument doc, Rectangle rect, String name, String caption, PdfFont font, int fontSize) {
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(doc, rect);
        PdfButtonFormField field = new PdfButtonFormField(annot).makeIndirect(doc);
        field.setPushButton(true);
        field.setFieldName(name);

        PdfFormXObject xObject = field.drawPushButtonAppearance(rect.getWidth(), rect.getHeight(), caption, font, fontSize);
        annot.setNormalAppearance(xObject.getPdfObject());

        return field;
    }

    public static PdfButtonFormField createCheckBox(PdfDocument doc, Rectangle rect, String value, String name) {
        return createCheckBox(doc, rect, value, name, PdfButtonFormField.TYPE_CROSS);
    }

    public static PdfButtonFormField createCheckBox(PdfDocument doc, Rectangle rect, String value, String name, int checkType) {
        PdfWidgetAnnotation annot = new PdfWidgetAnnotation(doc, rect);
        PdfFormField check = new PdfButtonFormField(annot).makeIndirect(doc);
        check.setCheckType(checkType);
        check.setFieldName(name);
        check.setValue(value);
        annot.setAppearanceState(new PdfName(value));
        check.drawCheckAppearance(rect.getWidth(), rect.getHeight(), value);

        return (PdfButtonFormField) check;
    }

    public static PdfChoiceFormField createComboBox(PdfDocument doc, Rectangle rect, String options[][], String value, String name) {
        return createChoice(doc, rect, processOptions(options), value, name, PdfChoiceFormField.FF_COMBO);
    }

    public static PdfChoiceFormField createComboBox(PdfDocument doc, Rectangle rect, String options[], String value, String name) {
        return createChoice(doc, rect, processOptions(options), value, name, PdfChoiceFormField.FF_COMBO);
    }

    public static PdfChoiceFormField createList(PdfDocument doc, Rectangle rect, String options[][], String value, String name) {
        StringBuffer text = new StringBuffer();
        for (String[] option : options) {
            text.append(option[1]).append('\n');
        }
        return createChoice(doc, rect, processOptions(options), text.toString(), name, 0);
    }

    public static PdfChoiceFormField createList(PdfDocument doc, Rectangle rect, String options[], String value, String name) {
        StringBuffer text = new StringBuffer();
        for (String option : options) {
            text.append(option).append('\n');
        }
        return createChoice(doc, rect, processOptions(options), text.toString(), name, 0);
    }

    public static <T extends PdfFormField> T makeFormField(PdfObject pdfObject, PdfDocument document) {
        T field = null;
        if (pdfObject.isIndirectReference())
            pdfObject = ((PdfIndirectReference) pdfObject).getRefersTo();
        if (pdfObject.isDictionary()) {
            PdfDictionary dictionary = (PdfDictionary) pdfObject;
            PdfName formType = dictionary.getAsName(PdfName.FT);
            if (PdfName.Tx.equals(formType))
                field = (T) new PdfTextFormField(dictionary).makeIndirect(document);
            else if (PdfName.Btn.equals(formType))
                field = (T) new PdfButtonFormField(dictionary).makeIndirect(document);
            else if (PdfName.Ch.equals(formType))
                field = (T) new PdfChoiceFormField(dictionary).makeIndirect(document);
            else if (PdfName.Sig.equals(formType))
                field = (T) new PdfSignatureFormField(dictionary).makeIndirect(document);
            else
                field = (T) new PdfFormField(dictionary).makeIndirect(document);
        }

        return field;
    }

    public PdfName getFormType() {
        return null;
    }

    public <T extends PdfFormField> T setValue(String value) {
        PdfName formType = getFormType();
        if (PdfName.Tx.equals(formType) || PdfName.Ch.equals(formType)) {
            put(PdfName.V, new PdfString(value));
        } else if (PdfName.Btn.equals(formType)) {
             if ((getFieldFlags() & PdfButtonFormField.FF_PUSH_BUTTON) != 0) {
                 //@TODO Base64 images support
             } else {
                 put(PdfName.V, new PdfName(value));
             }
        }

        regenerateField();
        return (T) this;
    }

    public <T extends PdfFormField> T setParent(PdfFormField parent) {
        return put(PdfName.Parent, parent);
    }

    public PdfDictionary getParent() {
        return getPdfObject().getAsDictionary(PdfName.Parent);
    }

    public PdfArray getKids() {
        return getPdfObject().getAsArray(PdfName.Kids);
    }

    public <T extends PdfFormField> T addKid(PdfFormField kid) {
        kid.setParent(this);
        PdfArray kids = getKids();
        if (kids == null) {
            kids = new PdfArray();
        }
        kids.add(kid.getPdfObject());
        return put(PdfName.Kids, kids);
    }

    public <T extends PdfFormField> T addKid(PdfWidgetAnnotation kid) {
        kid.setParent(getPdfObject());
        PdfArray kids = getKids();
        if (kids == null) {
            kids = new PdfArray();
        }
        kids.add(kid.getPdfObject());
        return put(PdfName.Kids, kids);
    }

    public <T extends PdfFormField> T setFieldName(String name) {
        return put(PdfName.T, new PdfString(name));
    }

    public PdfString getFieldName() {
        return getPdfObject().getAsString(PdfName.T);
    }

    public <T extends PdfFormField> T setAlternativeName(String name) {
        return put(PdfName.TU, new PdfString(name));
    }

    public PdfString getAlternativeName() {
        return getPdfObject().getAsString(PdfName.TU);
    }

    public <T extends PdfFormField> T setMappingName(String name) {
        return put(PdfName.TM, new PdfString(name));
    }

    public PdfString getMappingName() {
        return getPdfObject().getAsString(PdfName.TM);
    }

    public boolean getFieldFlag(int flag) {
        return (getFieldFlags() & flag) != 0;
    }

    public <T extends PdfFormField> T setFieldFlag(int flag) {
        return setFieldFlag(flag, true);
    }

    public <T extends PdfFormField> T setFieldFlag(int flag, boolean value) {
        int flags = getFieldFlags();

        if (value) {
            flags |= flag;
        } else {
            flags &= ~flag;
        }

        return setFieldFlags(flags);
    }

    public <T extends PdfFormField> T setFieldFlags(int flags) {
        return put(PdfName.Ff, new PdfNumber(flags));
    }

    public int getFieldFlags() {
        PdfNumber f = getPdfObject().getAsNumber(PdfName.Ff);
        if (f != null)
            return f.getIntValue();
        else
            return 0;
    }

    public PdfObject getValue() {
        return getPdfObject().get(PdfName.V);
    }

    public String getValueAsString() {
        PdfObject value = getPdfObject().get(PdfName.V);
        if (value == null) {
            return "";
        } else if (value instanceof PdfStream) {
            return new String(((PdfStream)value).getBytes());
        } else if (value instanceof PdfName) {
            return ((PdfName) value).getValue();
        } else if (value instanceof PdfString) {
            return ((PdfString) value).toUnicodeString();
        } else {
            return "";
        }
    }

    public <T extends PdfFormField> T setDefaultValue(PdfObject value) {
        return put(PdfName.DV, value);
    }

    public PdfObject getDefaultValue() {
        return getPdfObject().get(PdfName.DV);
    }

    public <T extends PdfFormField> T setAdditionalAction(PdfName key, PdfAction action) {
        PdfAction.setAdditionalAction(this, key, action);
        return (T) this;
    }

    public PdfDictionary getAdditionalAction() {
        return getPdfObject().getAsDictionary(PdfName.AA);
    }

    public <T extends PdfFormField> T setOptions(PdfArray options) {
        return put(PdfName.Opt, options);
    }

    public PdfArray getOptions() {
        return getPdfObject().getAsArray(PdfName.Opt);
    }

    public List<PdfWidgetAnnotation> getWidgets() {
        List<PdfWidgetAnnotation> widgets = new ArrayList<>();

        PdfName subType = getPdfObject().getAsName(PdfName.Subtype);
        if (subType != null && subType.equals(PdfName.Widget)) {
            widgets.add((PdfWidgetAnnotation) PdfAnnotation.makeAnnotation(getPdfObject(), getDocument()));
        }

        PdfArray kids = getKids();
        if (kids != null) {
            for (PdfObject kid : kids) {
                subType = ((PdfDictionary)kid).getAsName(PdfName.Subtype);
                if (subType != null && subType.equals(PdfName.Widget)) {
                    widgets.add((PdfWidgetAnnotation) PdfAnnotation.makeAnnotation(kid, getDocument()));
                }
            }
        }

        return widgets;
    }

    /**
     * Gets default appearance string containing a sequence of valid page-content graphics or text state operators that
     * define such properties as the field’s text size and color.
     */
    public PdfString getDefaultAppearance() {
        return getPdfObject().getAsString(PdfName.DA);
    }

    /**
     * Sets default appearance string containing a sequence of valid page-content graphics or text state operators that
     * define such properties as the field’s text size and color.
     */
    public <T extends PdfFormField> T setDefaultAppearance(String defaultAppearance) {
        byte[] b = defaultAppearance.getBytes();
        int len = b.length;
        for (int k = 0; k < len; ++k) {
            if (b[k] == '\n')
                b[k] = 32;
        }
        getPdfObject().put(PdfName.DA, new PdfString(new String(b)));
        return (T) this;
    }

    /**
     * Gets a code specifying the form of quadding (justification) to be used in displaying the text:
     * 0 Left-justified
     * 1 Centered
     * 2 Right-justified
     */
    public Integer getJustification() {
        return getPdfObject().getAsInt(PdfName.Q);
    }

    /**
     * Sets a code specifying the form of quadding (justification) to be used in displaying the text:
     * 0 Left-justified
     * 1 Centered
     * 2 Right-justified
     */
    public <T extends PdfFormField> T setJustification(int justification) {
        getPdfObject().put(PdfName.Q, new PdfNumber(justification));
        return (T) this;
    }

    /**
     * Gets a default style string, as described in "Rich Text Strings" section of Pdf spec.
     */
    public PdfString getDefaultStyle() {
        return getPdfObject().getAsString(PdfName.DS);
    }

    /**
     * Sets a default style string, as described in "Rich Text Strings" section of Pdf spec.
     */
    public <T extends PdfFormField> T setDefaultStyle(PdfString defaultStyleString) {
        getPdfObject().put(PdfName.DS, defaultStyleString);
        return (T) this;
    }

    /**
     * Gets a rich text string, as described in "Rich Text Strings" section of Pdf spec.
     * May be either {@link PdfStream} or {@link PdfString}.
     */
    public PdfObject getRichText() {
        return getPdfObject().get(PdfName.RV);
    }

    /**
     * Sets a rich text string, as described in "Rich Text Strings" section of Pdf spec.
     * May be either {@link PdfStream} or {@link PdfString}.
     */
    public <T extends PdfFormField> T setRichText(PdfObject richText) {
        getPdfObject().put(PdfName.RV, richText);
        return (T) this;
    }

    public PdfFormXObject drawTextAppearance(Rectangle rect, PdfFont font, int fontSize, String value) {
        PdfStream stream = new PdfStream().makeIndirect(getDocument());
        PdfCanvas canvas = new PdfCanvas(stream, new PdfResources());

        setDefaultAppearance(setFontAndSize(font, fontSize));

        float height = rect.getHeight();
        float width = rect.getWidth();

        canvas.
                beginVariableText().
                saveState().
                newPath().
                beginText().
                setFontAndSize(font, fontSize).
                resetFillColorRgb().
                setTextMatrix(2, height / 2 - fontSize * 0.3f).
                showText(value).
                endText().
                restoreState().
                endVariableText();


        PdfFormXObject xObject = new PdfFormXObject(new Rectangle(0, 0, width, height));
        xObject.getPdfObject().getOutputStream().writeBytes(stream.getBytes());

        return xObject;
    }

    public PdfFormXObject drawMultiLineTextAppearance(Rectangle rect, PdfFont font, int fontSize, String value) {
        PdfStream stream = new PdfStream().makeIndirect(getDocument());
        PdfCanvas canvas = new PdfCanvas(stream, new PdfResources());

        setDefaultAppearance(setFontAndSize(font, fontSize));

        float width = rect.getWidth();
        float height = rect.getHeight();

        drawBorder(canvas, width, height);
        canvas.
                beginVariableText().
                saveState().
                rectangle(3, 3, width - 6, height - 6).
                clip().
                newPath().
                beginText().
                setFontAndSize(font, fontSize).
                resetFillColorRgb().
                setTextMatrix(4, 5);
        StringTokenizer tokenizer = new StringTokenizer(value, "\n");
        while (tokenizer.hasMoreTokens()) {
            height -= fontSize * 1.2;
            canvas.
                    setTextMatrix(3, height).
                    showText(tokenizer.nextToken());
        }
        canvas.
                endText().
                restoreState().
                endVariableText();


        PdfFormXObject xObject = new PdfFormXObject(new Rectangle(0, 0, rect.getWidth(), rect.getHeight()));
        xObject.getPdfObject().getOutputStream().writeBytes(stream.getBytes());

        return xObject;
    }

    public void drawBorder(PdfCanvas canvas, float width, float height) {

        if (borderWidth <=0) {
            return;
        }

        borderWidth = Math.max(1, borderWidth);

        canvas.
                saveState().
                setStrokeColor(Color.BLACK).
                setLineWidth(borderWidth).
                rectangle(0, 0, width, height).
                stroke().
                restoreState();
    }

    public void drawRadioAppearance(float width, float height, String value) {
        PdfStream streamOn = new PdfStream().makeIndirect(getDocument());
        PdfCanvas canvasOn = new PdfCanvas(streamOn, new PdfResources());
        drawBorder(canvasOn, width, height);
        drawRadioField(canvasOn, 0, 0, width, height, true);

        PdfStream streamOff = new PdfStream().makeIndirect(getDocument());
        PdfCanvas canvasOff = new PdfCanvas(streamOff, new PdfResources());
        drawBorder(canvasOff, width, height);

        Rectangle rect = new Rectangle(0, 0, width, height);
        PdfWidgetAnnotation widget = getWidgets().get(0);
        PdfFormXObject xObjectOn = new PdfFormXObject(rect);
        xObjectOn.getPdfObject().getOutputStream().writeBytes(streamOn.getBytes());
        widget.setNormalAppearance(new PdfDictionary());
        widget.getNormalAppearanceObject().put(new PdfName(value), xObjectOn.getPdfObject());

        PdfFormXObject xObjectOff = new PdfFormXObject(rect);
        xObjectOff.getPdfObject().getOutputStream().writeBytes(streamOff.getBytes());
        widget.getNormalAppearanceObject().put(new PdfName("Off"), xObjectOff.getPdfObject());
    }

    public void drawRadioField(PdfCanvas canvas, final float x, final float y, final float width, final float height, final boolean on) {
        canvas.saveState();
        if (on) {
            canvas.
                    resetFillColorRgb().
                    circle(width / 2, height / 2, Math.min(width, height) / 4).
                    fill();
        }
        canvas.restoreState();
    }

    public void drawCheckAppearance(float width, float height, String value) {
        PdfStream streamOn = new PdfStream().makeIndirect(getDocument());
        PdfCanvas canvasOn = new PdfCanvas(streamOn, new PdfResources());
        drawBorder(canvasOn, width, height);
        drawCheckBox(canvasOn, width, height, DEFAULT_FONT_SIZE, true);

        PdfStream streamOff = new PdfStream().makeIndirect(getDocument());
        PdfCanvas canvasOff = new PdfCanvas(streamOff, new PdfResources());
        drawBorder(canvasOff, width, height);
        drawCheckBox(canvasOff, width, height, DEFAULT_FONT_SIZE, false);

        Rectangle rect = new Rectangle(0, 0, width, height);
        PdfWidgetAnnotation widget = getWidgets().get(0);
        PdfFormXObject xObjectOn = new PdfFormXObject(rect);
        xObjectOn.getPdfObject().getOutputStream().writeBytes(streamOn.getBytes());
        xObjectOn.getResources().addFont(getFont());
        widget.setNormalAppearance(new PdfDictionary());
        widget.getNormalAppearanceObject().put(new PdfName(value), xObjectOn.getPdfObject());

        PdfFormXObject xObjectOff = new PdfFormXObject(rect);
        xObjectOff.getPdfObject().getOutputStream().writeBytes(streamOff.getBytes());
        xObjectOff.getResources().addFont(getFont());
        widget.getNormalAppearanceObject().put(new PdfName("Off"), xObjectOff.getPdfObject());
//        setDefaultAppearance(setFontAndSize(getFont(), DEFAULT_FONT_SIZE));
    }
    
    public PdfFormXObject drawPushButtonAppearance(float width, float height, String text, PdfFont font, int fontSize) {
        PdfStream stream = new PdfStream().makeIndirect(getDocument());
        PdfCanvas canvas = new PdfCanvas(stream, new PdfResources());
//        setDefaultAppearance(setFontAndSize(font, fontSize));

        drawButton(canvas, 0, 0, width, height, text, font, fontSize);

        PdfFormXObject xObject = new PdfFormXObject(new Rectangle(0, 0, width, height));
        xObject.getPdfObject().getOutputStream().writeBytes(stream.getBytes());
        xObject.getResources().addFont(font);

        return xObject;
    }

    public void drawButton(PdfCanvas canvas, float x, float y, float width, float height, String text, PdfFont font, int fontSize) {
        canvas.
                saveState().
                setStrokeColor(Color.BLACK).
                setLineWidth(1).
                setLineCapStyle(PdfCanvasConstants.LineCapStyle.BUTT).
                rectangle(x, y, width, height).
                stroke().
                setLineWidth(1).
                setLineCapStyle(PdfCanvasConstants.LineCapStyle.BUTT).
                setFillColor(Color.LIGHT_GRAY).
                rectangle(x + 0.5f, y + 0.5f, width - 1, height - 1).
                fill().
                setStrokeColor(Color.WHITE).
                setLineWidth(1).
                setLineCapStyle(PdfCanvasConstants.LineCapStyle.BUTT).
                moveTo(x + 1, y + 1).
                lineTo(x + 1, y + height - 1).
                lineTo(x + width - 1, y + height - 1).
                stroke().
                setStrokeColor(Color.GRAY).
                setLineWidth(1).
                setLineCapStyle(PdfCanvasConstants.LineCapStyle.BUTT).
                moveTo(x + 1, y + 1).
                lineTo(x + width - 1, y + 1).
                lineTo(x + width - 1, y + height - 1).
                stroke().
                resetFillColorRgb().
                beginText().
                setFontAndSize(font, fontSize).
                setTextMatrix(0, y + (height - fontSize) / 2).
                showText(text).
                endText().
                restoreState();
    }



    public void drawCheckBox(PdfCanvas canvas, float width, float height, int fontSize, boolean on) {
        if (!on) {
            return;
        }
        PdfFont ufont = getFont();
        canvas.
                beginText().
                setFontAndSize(ufont, fontSize).
                resetFillColorRgb().
                setTextMatrix((width - ufont.getFontProgram().getWidthPoint(text, fontSize)) / 2, (height - ufont.getFontProgram().getAscent(text) * 0.001f * fontSize) / 2).
                showText(text).
                endText();
    }

    public PdfFont getFont() {
        return font;
    }

    public void setFont(PdfFont font) {
        this.font = font;
    }

    public String getText() {
        return text;
    }

    public void setCheckType(int checkType) {
        if (checkType < TYPE_CHECK || checkType > TYPE_STAR) {
            checkType = TYPE_CROSS;
        }
        this.checkType = checkType;
        setText(typeChars[checkType - 1]);
        try {
            setFont(new PdfType1Font(getDocument(), new Type1Font(FontConstants.ZAPFDINGBATS, PdfEncodings.WINANSI)));
        }
        catch (IOException e) {
            throw new PdfException(e.getLocalizedMessage());
        }
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean regenerateField() {
        PdfName type = getFormType();
        String value = getValueAsString();

        if (PdfName.Tx.equals(type) || PdfName.Ch.equals(type)) {
            try{
                PdfDictionary apDic = getPdfObject().getAsDictionary(PdfName.AP);
                PdfStream asNormal = null;
                if (apDic != null) {
                    asNormal = apDic.getAsStream(PdfName.N);
                }
                PdfArray bBox = getPdfObject().getAsArray(PdfName.Rect);
                if (bBox == null) {
                    PdfArray kids = getKids();
                    if (kids == null) {
                        throw new PdfException("Wrong form field to regenerate. Add annotation to the field");
                    }
                    bBox = ((PdfDictionary) kids.get(0)).getAsArray(PdfName.Rect);
                }

                Object[] fontAndSize = getFontAndSize(asNormal);
                PdfFont font = (PdfFont) fontAndSize[0];
                int fontSize = (int) fontAndSize[1];

                PdfFormXObject appearance;
                if (PdfName.Tx.equals(type)) {
                    appearance = drawTextAppearance(bBox.toRectangle(), font, fontSize, value);
                } else {
                    appearance = drawMultiLineTextAppearance(bBox.toRectangle(), font, fontSize, value);
                }
                appearance.getResources().addFont(font);
                PdfDictionary ap = new PdfDictionary();
                ap.put(PdfName.N, appearance.getPdfObject());
                put(PdfName.AP, ap);

                return true;
            } catch (IOException e) {
                throw new PdfException(e.getLocalizedMessage());
            }

        } else if (PdfName.Btn.equals(type)) {
            int ff = getFieldFlags();
            if ((ff & PdfButtonFormField.FF_PUSH_BUTTON) != 0) {
                try {
                    Rectangle rect = getRect(getPdfObject());
                    PdfDictionary apDic = getPdfObject().getAsDictionary(PdfName.AP);
                    PdfStream asNormal = null;
                    if (apDic != null) {
                        asNormal = apDic.getAsStream(PdfName.N);
                    }
                    Object[] fontAndSize = getFontAndSize(asNormal);
                    PdfFont font = (PdfFont) fontAndSize[0];
                    int fontSize = (int) fontAndSize[1];
                    PdfFormXObject appearance = drawPushButtonAppearance(rect.getWidth(), rect.getHeight(), value, font, fontSize);
                    appearance.getResources().addFont(font);
                    apDic.put(PdfName.N, appearance.getPdfObject());
                } catch (IOException e) {
                    throw new PdfException(e.getLocalizedMessage());
                }
            }
            else if ((ff & PdfButtonFormField.FF_RADIO) != 0) {
                PdfArray kids = getKids();
                for (PdfObject kid : kids) {
                    if (kid.isIndirectReference()) {
                        kid = ((PdfIndirectReference) kid).getRefersTo();
                    }
                    PdfFormField field = new PdfFormField((PdfDictionary) kid);
                    PdfWidgetAnnotation widget = field.getWidgets().get(0);
                    PdfDictionary buttonValues = field.getPdfObject().getAsDictionary(PdfName.AP).getAsDictionary(PdfName.N);
                    String state;
                    if (buttonValues.get(new PdfName(value)) != null) {
                        state = value;
                    } else {
                        state = "Off";

                    }
                    widget.setAppearanceState(new PdfName(state));
                }
            } else {
                Rectangle rect = getRect(getPdfObject());
                setCheckType(checkType);
                drawCheckAppearance(rect.getWidth(), rect.getHeight(), value);
                PdfWidgetAnnotation widget = getWidgets().get(0);
                if (value.equals("Yes")) {
                    widget.setAppearanceState(new PdfName(value));
                } else {
                    widget.setAppearanceState(new PdfName("Off"));
                }
            }
        }
        return true;
    }

    public float getBorderWidth() {
        return borderWidth;
    }

    public void setBorderWidth(float borderWidth) {
        this.borderWidth = borderWidth;
    }

    protected Rectangle getRect(PdfDictionary field) {
        PdfArray rect = field.getAsArray(PdfName.Rect);
        if (rect == null) {
            PdfArray kids = field.getAsArray(PdfName.Kids);
            if (kids == null) {
                throw new PdfException(PdfException.WrongFormFieldAddAnnotationToTheField);
            }
            rect = ((PdfDictionary) kids.get(0)).getAsArray(PdfName.Rect);
        }

        return rect.toRectangle();
    }

    protected static PdfArray processOptions(String options[][]) {
        PdfArray array = new PdfArray();
        for (String option[] : options) {
            String subOption[] = option;
            PdfArray subArray = new PdfArray(new PdfString(subOption[0]));
            subArray.add(new PdfString(subOption[1]));
            array.add(subArray);
        }
        return array;
    }

    protected static PdfArray processOptions(String options[]) {
        PdfArray array = new PdfArray();
        for (String option : options) {
            array.add(new PdfString(option));
        }
        return array;
    }

    protected String setFontAndSize(PdfFont font, int fontSize) {
        PdfStream stream = new PdfStream();
        PdfCanvas canvas = new PdfCanvas(stream, new PdfResources());
        canvas.setFontAndSize(font, fontSize).resetFillColorRgb();
        return new String(stream.getBytes());
    }

    protected Object[] getFontAndSize(PdfDictionary asNormal) throws IOException {
        Object[] fontAndSize = new Object[2];
        PdfDictionary resources = null;
        if (asNormal != null) {
            resources = asNormal.getAsDictionary(PdfName.Resources);
        }
        if (resources != null) {
            PdfDictionary fontDic = resources.getAsDictionary(PdfName.Font);
            if (fontDic != null) {
                String str = getDefaultAppearance().toUnicodeString();
                Object[] dab = splitDAelements(str);
                PdfName fontName = new PdfName(dab[0].toString());
                fontAndSize[0] =  new PdfFont(getDocument(), fontDic.getAsDictionary(fontName));
//                fontAndSize[0] =  PdfFont.createFont(getDocument(), fontDic.getAsDictionary(fontName));
                fontAndSize[1] = ((Integer)dab[1]).intValue();
            }
        } else {
            fontAndSize[0] = PdfFont.getDefaultFont(getDocument());
            fontAndSize[1] = DEFAULT_FONT_SIZE;
        }

        return fontAndSize;
    }

    protected static Object[] splitDAelements(String da) {

        PdfTokenizer tk = new PdfTokenizer(new RandomAccessFileOrArray(new RandomAccessSourceFactory().createSource(PdfEncodings.convertToBytes(da, null))));
        ArrayList<String> stack = new ArrayList<String>();
        Object ret[] = new Object[3];
        try {
            while (tk.nextToken()) {
                if (tk.getTokenType() == PdfTokenizer.TokenType.Comment)
                    continue;
                if (tk.getTokenType() == PdfTokenizer.TokenType.Other) {
                    String operator = tk.getStringValue();
                    if (operator.equals("Tf")) {
                        if (stack.size() >= 2) {
                            ret[DA_FONT] = stack.get(stack.size() - 2);
                            ret[DA_SIZE] = new Integer(stack.get(stack.size() - 1));
                        }
                    }
                    else if (operator.equals("g")) {
                        if (stack.size() >= 1) {
                            float gray = new Float(stack.get(stack.size() - 1)).floatValue();
                            if (gray != 0) {
                                ret[DA_COLOR] = new DeviceGray(gray);
                            }
                        }
                    }
                    else if (operator.equals("rg")) {
                        if (stack.size() >= 3) {
                            float red = new Float(stack.get(stack.size() - 3)).floatValue();
                            float green = new Float(stack.get(stack.size() - 2)).floatValue();
                            float blue = new Float(stack.get(stack.size() - 1)).floatValue();
                            ret[DA_COLOR] = new DeviceRgb(red, green, blue);
                        }
                    }
                    else if (operator.equals("k")) {
                        if (stack.size() >= 4) {
                            float cyan = new Float(stack.get(stack.size() - 4)).floatValue();
                            float magenta = new Float(stack.get(stack.size() - 3)).floatValue();
                            float yellow = new Float(stack.get(stack.size() - 2)).floatValue();
                            float black = new Float(stack.get(stack.size() - 1)).floatValue();
                            ret[DA_COLOR] = new DeviceCmyk(cyan, magenta, yellow, black);
                        }
                    }
                    stack.clear();
                }
                else
                    stack.add(tk.getStringValue());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }
}