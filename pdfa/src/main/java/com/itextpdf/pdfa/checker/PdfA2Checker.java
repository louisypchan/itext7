package com.itextpdf.pdfa.checker;

import com.itextpdf.basics.color.IccProfile;
import com.itextpdf.basics.geom.Rectangle;
import com.itextpdf.basics.image.ImageFactory;
import com.itextpdf.basics.image.Jpeg2000Image;
import com.itextpdf.canvas.PdfGraphicsState;
import com.itextpdf.canvas.color.Color;
import com.itextpdf.canvas.color.DeviceRgb;
import com.itextpdf.canvas.color.PatternColor;
import com.itextpdf.core.pdf.*;
import com.itextpdf.core.pdf.annot.PdfAnnotation;
import com.itextpdf.core.pdf.colorspace.*;
import com.itextpdf.core.pdf.extgstate.PdfExtGState;
import com.itextpdf.pdfa.PdfAConformanceException;
import com.itextpdf.pdfa.PdfAConformanceLevel;

import java.util.*;

public class PdfA2Checker extends PdfA1Checker{

    protected static final HashSet<PdfName> forbiddenAnnotations = new HashSet<>(Arrays.asList(PdfName._3D, PdfName.Sound, PdfName.Screen, PdfName.Movie));
    protected static final HashSet<PdfName> forbiddenActions = new HashSet<>(Arrays.asList(PdfName.Launch, PdfName.Sound, PdfName.Movie,
            PdfName.ResetForm, PdfName.ImportData, PdfName.JavaScript, PdfName.Hide, PdfName.SetOCGState, PdfName.Rendition, PdfName.Trans, PdfName.GoTo3DView));
    protected static final HashSet<PdfName> allowedBlendModes = new HashSet<PdfName>(Arrays.asList(PdfName.Normal,
            PdfName.Compatible, PdfName.Multiply, PdfName.Screen, PdfName.Overlay,
            PdfName.Darken, PdfName.Lighten, PdfName.ColorDodge, PdfName.ColorBurn,
            PdfName.HardLight, PdfName.SoftLight, PdfName.Difference, PdfName.Exclusion,
            PdfName.Hue, PdfName.Saturation, PdfName.Color, PdfName.Luminosity));

    static final int MAX_PAGE_SIZE = 14400;
    static final int MIN_PAGE_SIZE = 3;

    private boolean transparencyIsUsed = false;
    private boolean currentFillCsIsIccBasedCMYK = false;
    private boolean currentStrokeCsIsIccBasedCMYK = false;

    private Map<PdfName, PdfArray> separationColorSpaces = new HashMap<>();


    public PdfA2Checker(PdfAConformanceLevel conformanceLevel) {
        super(conformanceLevel);
    }

    @Override
    public void checkInlineImage(PdfStream inlineImage, PdfDictionary currentColorSpaces) {
        PdfObject filter = inlineImage.get(PdfName.Filter);
        if (filter instanceof PdfName) {
            if (filter.equals(PdfName.LZWDecode))
                throw new PdfAConformanceException(PdfAConformanceException.LZWDecodeFilterIsNotPermitted);
            if (filter.equals(PdfName.Crypt)) {
                throw new PdfAConformanceException(PdfAConformanceException.CryptFilterIsNotPermitted);
            }
        } else if (filter instanceof PdfArray) {
            for (int i = 0; i < ((PdfArray) filter).size(); i++) {
                PdfName f = ((PdfArray) filter).getAsName(i);
                if (f.equals(PdfName.LZWDecode))
                    throw new PdfAConformanceException(PdfAConformanceException.LZWDecodeFilterIsNotPermitted);
                if (f.equals(PdfName.Crypt)) {
                    throw new PdfAConformanceException(PdfAConformanceException.CryptFilterIsNotPermitted);
                }
            }
        }

        checkImage(inlineImage, currentColorSpaces);
    }

    @Override
    public void checkColor(Color color, PdfDictionary currentColorSpaces, Boolean fill) {
        if (color instanceof PatternColor) {
            PdfPattern pattern = ((PatternColor)color).getPattern();
            if (pattern instanceof PdfPattern.Shading) {
                PdfDictionary shadingDictionary = ((PdfPattern.Shading) pattern).getShading();
                PdfObject colorSpace = shadingDictionary.get(PdfName.ColorSpace);
                checkColorSpace(PdfColorSpace.makeColorSpace(colorSpace, null), currentColorSpaces, true, true);
                PdfGraphicsState gState = new PdfGraphicsState();
                PdfDictionary extGStateDict = ((PdfDictionary) pattern.getPdfObject()).getAsDictionary(PdfName.ExtGState);
                gState.updateFromExtGState(new PdfExtGState(extGStateDict));
                checkExtGState(gState);
            }
        }

        checkColorSpace(color.getColorSpace(), currentColorSpaces, true, fill);
    }

    @Override
    public void checkColorSpace(PdfColorSpace colorSpace, PdfDictionary currentColorSpaces, boolean checkAlternate, Boolean fill) {
        if (fill != null) {
            if (fill) {
                currentFillCsIsIccBasedCMYK = false;
            } else {
                currentStrokeCsIsIccBasedCMYK = false;
            }
        }

        if (colorSpace instanceof PdfSpecialCs.Separation) {

            PdfSpecialCs.Separation separation = (PdfSpecialCs.Separation) colorSpace;
            checkSeparationCS(separation.getPdfObject());
            if (checkAlternate) {
                checkColorSpace(separation.getBaseCs(), currentColorSpaces, false, fill);
            }

        } else if (colorSpace instanceof PdfSpecialCs.DeviceN) {

            PdfSpecialCs.DeviceN deviceN = (PdfSpecialCs.DeviceN) colorSpace;
            PdfDictionary attributes = deviceN.getPdfObject().getAsDictionary(4);
            PdfDictionary colorants = attributes.getAsDictionary(PdfName.Colorants);
            if (colorants != null) {
                for (Map.Entry<PdfName, PdfObject> entry : colorants.entrySet()) {
                    PdfArray separation = (PdfArray) entry.getValue();
                    checkSeparationInsideDeviceN(separation, deviceN.getPdfObject().get(2), deviceN.getPdfObject().get(3).getIndirectReference());
                }
            }
            if (checkAlternate) {
                checkColorSpace(deviceN.getBaseCs(), currentColorSpaces, false, fill);
            }

        } else if (colorSpace instanceof PdfSpecialCs.Indexed) {
            if (checkAlternate) {
                checkColorSpace(((PdfSpecialCs.Indexed) colorSpace).getBaseCs(), currentColorSpaces, true, fill);
            }
        } else if (colorSpace instanceof PdfSpecialCs.UncoloredTilingPattern) {
            if (checkAlternate) {
                checkColorSpace(((PdfSpecialCs.UncoloredTilingPattern) colorSpace).getUnderlyingColorSpace(), currentColorSpaces, true, fill);
            }
        } else {

            if (colorSpace instanceof PdfDeviceCs.Rgb) {
                if (!checkDefaultCS(currentColorSpaces, fill, PdfName.DefaultRGB, 3)) {
                    rgbIsUsed = true;
                }
            } else if (colorSpace instanceof PdfDeviceCs.Cmyk) {
                if (!checkDefaultCS(currentColorSpaces, fill, PdfName.DefaultCMYK, 4)) {
                    cmykIsUsed = true;
                }
            } else if (colorSpace instanceof PdfDeviceCs.Gray) {
                if (!checkDefaultCS(currentColorSpaces, fill, PdfName.DefaultGray, 1)) {
                    grayIsUsed = true;
                }
            }
        }

        if (fill != null && colorSpace instanceof PdfCieBasedCs.IccBased) {
            byte[] iccBytes = ((PdfCieBasedCs.IccBased) colorSpace).getPdfObject().getAsStream(1).getBytes();
            if (ICC_COLOR_SPACE_CMYK.equals(IccProfile.getIccColorSpaceName(iccBytes))) {
                if (fill) {
                    currentFillCsIsIccBasedCMYK = true;
                } else {
                    currentStrokeCsIsIccBasedCMYK = true;
                }
            }
        }
    }

    @Override
    public void checkExtGState(PdfGraphicsState extGState) {
        if (Integer.valueOf(1).equals(extGState.getOverprintMode())) {
            if (extGState.getFillOverprint() && currentFillCsIsIccBasedCMYK) {
                throw new PdfAConformanceException(PdfAConformanceException.OverprintModeShallNotBeOneWhenAnICCBasedCMYKColourSpaceIsUsedAndWhenOverprintingIsSetToTrue);
            }
            if (extGState.getStrokeOverprint() && currentStrokeCsIsIccBasedCMYK) {
                throw new PdfAConformanceException(PdfAConformanceException.OverprintModeShallNotBeOneWhenAnICCBasedCMYKColourSpaceIsUsedAndWhenOverprintingIsSetToTrue);
            }
        }

        if (extGState.getTransferFunction() != null) {
            throw new PdfAConformanceException(PdfAConformanceException.AnExtgstateDictionaryShallNotContainTheTrKey);
        }
        if (extGState.getHTP() != null) {
            throw new PdfAConformanceException(PdfAConformanceException.AnExtgstateDictionaryShallNotContainTheHTPKey);
        }

        PdfObject transferFunction2 = extGState.getTransferFunction2();
        if (transferFunction2 != null && !PdfName.Default.equals(transferFunction2)) {
            throw new PdfAConformanceException(PdfAConformanceException.AnExtgstateDictionaryShallNotContainTheTR2KeyWithAValueOtherThanDefault);
        }

        if (extGState.getHalftone() instanceof PdfDictionary) {
            PdfDictionary halftoneDict = (PdfDictionary) extGState.getHalftone();
            Integer halftoneType = halftoneDict.getAsInt(PdfName.HalftoneType);
            if (halftoneType != 1 && halftoneType != 5) {
                throw new PdfAConformanceException(PdfAConformanceException.AllHalftonesShallHaveHalftonetype1Or5);
            }

            if (halftoneDict.containsKey(PdfName.HalftoneName)) {
                throw new PdfAConformanceException(PdfAConformanceException.HalftonesShallNotContainHalftonename);
            }
        }

        checkRenderingIntent(extGState.getRenderingIntent());

        if (extGState.getSoftMask() != null && extGState.getSoftMask() instanceof PdfDictionary) {
            transparencyIsUsed = true;
        }
        if (extGState.getStrokeAlpha() != null && extGState.getStrokeAlpha() < 1) {
            transparencyIsUsed = true;
        }
        if (extGState.getFillAlpha() != null && extGState.getFillAlpha() < 1) {
            transparencyIsUsed = true;
        }

        PdfObject bm = extGState.getBlendMode();
        if (bm != null) {
            if (!PdfName.Normal.equals(bm)) {
                transparencyIsUsed = true;
            }
            PdfName[] blendModes = null;
            if (bm instanceof PdfArray) {
                blendModes = (PdfName[]) ((PdfArray) bm).toArray();
            } else if (bm instanceof PdfName) {
                blendModes = new PdfName[]{(PdfName) bm};
            }

            for (PdfName blendMode : blendModes) {
                if (!allowedBlendModes.contains(blendMode)) {
                    throw new PdfAConformanceException(PdfAConformanceException.OnlyStandardBlendModesShallBeusedForTheValueOfTheBMKeyOnAnExtendedGraphicStateDictionary);
                }
            }
        }
    }

    @Override
    protected double getMaxRealValue(){
        return Float.MAX_VALUE;
    }

    @Override
    protected int getMaxStringLength(){
        return 32767;
    }
    
    @Override
    protected void checkAnnotation(PdfDictionary annotDic) {
        PdfName subtype = annotDic.getAsName(PdfName.Subtype);

        if (subtype == null) {
            throw new PdfAConformanceException(PdfAConformanceException.AnnotationType1IsNotPermitted).setMessageParams("null");
        }
        if (forbiddenAnnotations.contains(subtype)) {
            throw new PdfAConformanceException(PdfAConformanceException.AnnotationType1IsNotPermitted).setMessageParams(subtype.getValue());
        }

        if (!subtype.equals(PdfName.Popup)) {
            PdfNumber f = annotDic.getAsNumber(PdfName.F);
            if (f == null) {
                throw new PdfAConformanceException(PdfAConformanceException.AnAnnotationDictionaryShallContainTheFKey);
            }
            int flags = f.getIntValue();
            if (!checkFlag(flags, PdfAnnotation.Print)
                    || checkFlag(flags, PdfAnnotation.Hidden)
                    || checkFlag(flags, PdfAnnotation.Invisible)
                    || checkFlag(flags, PdfAnnotation.NoView)
                    || checkFlag(flags, PdfAnnotation.ToggleNoView)) {
                throw new PdfAConformanceException(PdfAConformanceException.TheFKeysPrintFlagBitShallBeSetTo1AndItsHiddenInvisibleNoviewAndTogglenoviewFlagBitsShallBeSetTo0);
            }
            if (subtype.equals(PdfName.Text)) {
                if (!checkFlag(flags, PdfAnnotation.NoZoom) || !checkFlag(flags, PdfAnnotation.NoRotate)) {
                    throw new PdfAConformanceException(PdfAConformanceException.TextAnnotationsShouldSetTheNozoomAndNorotateFlagBitsOfTheFKeyTo1);
                }
            }
        }

        if (PdfName.Widget.equals(subtype) && (annotDic.containsKey(PdfName.AA) || annotDic.containsKey(PdfName.A))) {
            throw new PdfAConformanceException(PdfAConformanceException.WidgetAnnotationDictionaryOrFieldDictionaryShallNotIncludeAOrAAEntry);
        }

        if (checkStructure(conformanceLevel)) {
            if (contentAnnotations.contains(subtype) && !annotDic.containsKey(PdfName.Contents)) {
                throw new PdfAConformanceException(PdfAConformanceException.AnnotationOfType1ShouldHaveContentsKey).setMessageParams(subtype);
            }
        }

        PdfDictionary ap = annotDic.getAsDictionary(PdfName.AP);
        if (ap != null) {
            if (ap.containsKey(PdfName.R) || ap.containsKey(PdfName.D)) {
                throw new PdfAConformanceException(PdfAConformanceException.AppearanceDictionaryShallContainOnlyTheNKeyWithStreamValue);
            }
            PdfObject n = ap.get(PdfName.N);
            if (PdfName.Widget.equals(subtype) && PdfName.Btn.equals(annotDic.getAsName(PdfName.FT))) {
                if (n == null || !n.isDictionary())
                    throw new PdfAConformanceException(PdfAConformanceException.AppearanceDictionaryOfWidgetSubtypeAndBtnFieldTypeShallContainOnlyTheNKeyWithDictionaryValue);
            } else {
                if (n == null || !n.isStream())
                    throw new PdfAConformanceException(PdfAConformanceException.AppearanceDictionaryShallContainOnlyTheNKeyWithStreamValue);
            }
        } else {
            boolean isCorrectRect = false;
            PdfArray rect = annotDic.getAsArray(PdfName.Rect);
            if (rect != null && rect.size() == 4) {
                PdfNumber index0 = rect.getAsNumber(0);
                PdfNumber index1 = rect.getAsNumber(1);
                PdfNumber index2 = rect.getAsNumber(2);
                PdfNumber index3 = rect.getAsNumber(3);
                if (index0 != null && index1 != null && index2 != null && index3 != null &&
                        index0.getFloatValue() == index2.getFloatValue() && index1.getFloatValue() == index3.getFloatValue())
                    isCorrectRect = true;
            }
            if (!PdfName.Popup.equals(subtype) &&
                    !PdfName.Link.equals(subtype) &&
                    !isCorrectRect)
                throw new PdfAConformanceException(PdfAConformanceException.EveryAnnotationShallHaveAtLeastOneAppearanceDictionary);
        }
    }

    @Override
    protected void checkForm(PdfDictionary form) {
        if (form != null) {
            PdfBoolean needAppearances = form.getAsBoolean(PdfName.NeedAppearances);
            if (needAppearances != null && needAppearances.getValue()) {
                throw new PdfAConformanceException(PdfAConformanceException.NeedAppearancesFlagOfTheInteractiveFormDictionaryShallEitherNotBePresentedOrShallBeFalse);
            }
            if (checkStructure(conformanceLevel) && form.containsKey(PdfName.XFA)) {
                throw new PdfAConformanceException(PdfAConformanceException.TheInteractiveFormDictionaryShallNotContainTheXfaKey);
            }
        }
    }

    @Override
    protected void checkCatalogValidEntries(PdfDictionary catalogDict) {
        if (catalogDict.containsKey(PdfName.NeedsRendering)) {
            throw new PdfAConformanceException(PdfAConformanceException.TheCatalogDictionaryShallNotContainTheNeedsrenderingKey);
        }

        if (catalogDict.containsKey(PdfName.AA)) {
            throw new PdfAConformanceException(PdfAConformanceException.CatalogDictionaryShallNotContainAAEntry);
        }

        if (catalogDict.containsKey(PdfName.Requirements)) {
            throw new PdfAConformanceException(PdfAConformanceException.CatalogDictionaryShallNotContainRequirementsEntry);
        }

        PdfDictionary permissions = catalogDict.getAsDictionary(PdfName.Perms);
        if (permissions != null) {
            for (PdfName dictKey : permissions.keySet()) {
                if (PdfName.DocMDP.equals(dictKey)) {
                    PdfDictionary signatureDict = permissions.getAsDictionary(PdfName.DocMDP);
                    if (signatureDict != null) {
                        PdfArray references = signatureDict.getAsArray(PdfName.Reference);
                        if (references != null) {
                            for (int i = 0; i < references.size(); i++) {
                                PdfDictionary referenceDict = references.getAsDictionary(i);
                                if (referenceDict.containsKey(PdfName.DigestLocation)
                                        || referenceDict.containsKey(PdfName.DigestMethod)
                                        || referenceDict.containsKey(PdfName.DigestValue)) {
                                    throw new PdfAConformanceException(PdfAConformanceException.SigRefDicShallNotContDigestParam);
                                }
                            }
                        }
                    }
                } else if (PdfName.UR3.equals(dictKey)){}
                else {
                    throw new PdfAConformanceException(PdfAConformanceException.NoKeysOtherUr3andDocMdpShallBePresentInPerDict);
                }
            }
        }

        PdfDictionary namesDictionary = catalogDict.getAsDictionary(PdfName.Names);
        if (namesDictionary != null && namesDictionary.containsKey(PdfName.AlternatePresentations)) {
            throw new PdfAConformanceException(PdfAConformanceException.CatalogDictionaryShallNotContainAlternatepresentationsNamesEntry);
        }

        PdfDictionary oCProperties = catalogDict.getAsDictionary(PdfName.OCProperties);
        if (oCProperties != null) {
            ArrayList<PdfDictionary> configList = new ArrayList<>();
            PdfDictionary d = oCProperties.getAsDictionary(PdfName.D);
            if (d != null) {
                configList.add(d);
            }
            PdfArray configs = oCProperties.getAsArray(PdfName.Configs);
            if (configs != null) {
                for (PdfObject config : configs) {
                    configList.add((PdfDictionary) config);
                }
            }

            HashSet<PdfObject> ocgs = new HashSet<>();
            PdfArray ocgsArray = oCProperties.getAsArray(PdfName.OCGs);
            if (ocgsArray != null) {
                ocgs.addAll(ocgsArray);
            }

            HashSet<String> names = new HashSet<>();
            HashSet<PdfObject> order = new HashSet<>();
            for (PdfDictionary config : configList) {
                PdfString name = config.getAsString(PdfName.Name);
                if (name == null) {
                    throw new PdfAConformanceException(PdfAConformanceException.OptionalContentConfigurationDictionaryShallContainNameEntry);
                }
                if (!names.add(name.toUnicodeString())) {
                    throw new PdfAConformanceException(PdfAConformanceException.ValueOfNameEntryShallBeUniqueAmongAllOptionalContentConfigurationDictionaries);
                }
                if (config.containsKey(PdfName.AS)) {
                    throw new PdfAConformanceException(PdfAConformanceException.TheAsKeyShallNotAppearInAnyOptionalContentConfigurationDictionary);
                }
                PdfArray orderArray = config.getAsArray(PdfName.Order);
                if (orderArray != null) {
                    fillOrderRecursively(orderArray, order);
                }
            }

            if (order.size() != ocgs.size()) {
                throw new PdfAConformanceException(PdfAConformanceException.OrderArrayShallContainReferencesToAllOcgs);
            }
            order.retainAll(ocgs);
            if (order.size() != ocgs.size()) {
                throw new PdfAConformanceException(PdfAConformanceException.OrderArrayShallContainReferencesToAllOcgs);
            }
        }
    }

    @Override
    protected  void checkPageSize(PdfDictionary  page){
        PdfName[] boxNames = new PdfName[] {PdfName.MediaBox, PdfName.CropBox, PdfName.TrimBox, PdfName.ArtBox, PdfName.BleedBox};
        for (PdfName boxName: boxNames) {
            Rectangle box =  page.getAsRectangle(boxName);
            if (box !=null ) {
                float width = box.getWidth();
                float height = box.getHeight();
                if (width < MIN_PAGE_SIZE || width > MAX_PAGE_SIZE || height < MIN_PAGE_SIZE || height > MAX_PAGE_SIZE)
                    throw new PdfAConformanceException(PdfAConformanceException.PageLess3UnitsNoGreater14400InEitherDirection);
            }
        }
    }

    @Override
    protected void checkPdfStream(PdfStream stream) {

        if (stream.containsKey(PdfName.F) || stream.containsKey(PdfName.FFilter) || stream.containsKey(PdfName.FDecodeParams)) {
            throw new PdfAConformanceException(PdfAConformanceException.StreamObjDictShallNotContainForFFilterOrFDecodeParams);
        }

        PdfObject filter = stream.get(PdfName.Filter);
        if (filter instanceof PdfName) {
            if (filter.equals(PdfName.LZWDecode))
                throw new PdfAConformanceException(PdfAConformanceException.LZWDecodeFilterIsNotPermitted);
            if (filter.equals(PdfName.Crypt)) {
                PdfDictionary decodeParams = stream.getAsDictionary(PdfName.DecodeParms);
                if (decodeParams != null) {
                    PdfString cryptFilterName = decodeParams.getAsString(PdfName.Name);
                    if (cryptFilterName != null && !cryptFilterName.equals(PdfName.Identity)) {
                        throw new PdfAConformanceException(PdfAConformanceException.NotIdentityCryptFilterIsNotPermitted);
                    }
                }
            }
        } else if (filter instanceof PdfArray) {
            for (int i = 0; i < ((PdfArray) filter).size(); i++) {
                PdfName f = ((PdfArray) filter).getAsName(i);
                if (f.equals(PdfName.LZWDecode))
                    throw new PdfAConformanceException(PdfAConformanceException.LZWDecodeFilterIsNotPermitted);
                if (f.equals(PdfName.Crypt)) {
                    PdfArray decodeParams = stream.getAsArray(PdfName.DecodeParms);
                    if (decodeParams != null && i < decodeParams.size()) {
                        PdfDictionary decodeParam = decodeParams.getAsDictionary(i);
                        PdfString cryptFilterName = decodeParam.getAsString(PdfName.Name);
                        if (cryptFilterName != null && !cryptFilterName.equals(PdfName.Identity)) {
                            throw new PdfAConformanceException(PdfAConformanceException.NotIdentityCryptFilterIsNotPermitted);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void checkPage(PdfPage page) {
        PdfDictionary pageDict = page.getPdfObject();

        if (pageDict.containsKey(PdfName.AA)) {
            throw new PdfAConformanceException(PdfAConformanceException.PageDictionaryShallNotContainAAEntry);
        }

        if (pageDict.containsKey(PdfName.PresSteps)) {
            throw new PdfAConformanceException(PdfAConformanceException.PageDictionaryShallNotContainPressstepsEntry);
        }

        if (pageDict.containsKey(PdfName.Group) && PdfName.Transparency.equals(pageDict.getAsDictionary(PdfName.Group).getAsName(PdfName.S))) {
            transparencyIsUsed = true;
            PdfObject cs = pageDict.getAsDictionary(PdfName.Group).get(PdfName.CS);
            if (cs != null) {
                PdfDictionary currentColorSpaces = page.getResources().getPdfObject().getAsDictionary(PdfName.ColorSpace);
                checkColorSpace(PdfColorSpace.makeColorSpace(cs, null), currentColorSpaces, true, null);
            }
        }
    }

    @Override
    protected void checkOutputIntents(PdfDictionary catalog) {
        PdfArray outputIntents = catalog.getAsArray(PdfName.OutputIntents);
        if (outputIntents == null)
            return;

        int i;
        PdfObject destOutputProfile = null;
        for (i = 0; i < outputIntents.size() && destOutputProfile == null; ++i) {
            destOutputProfile = outputIntents.getAsDictionary(i).get(PdfName.DestOutputProfile);
        }
        for (; i < outputIntents.size(); ++i) {
            PdfObject otherDestOutputProfile = outputIntents.getAsDictionary(i).get(PdfName.DestOutputProfile);
            if (otherDestOutputProfile != null && destOutputProfile != otherDestOutputProfile) {
                throw new PdfAConformanceException(PdfAConformanceException.IfOutputintentsArrayHasMoreThanOneEntryWithDestoutputprofileKeyTheSameIndirectObjectShallBeUsedAsTheValueOfThatObject);
            }
        }

        if (destOutputProfile != null) {
            String deviceClass = IccProfile.getIccDeviceClass(((PdfStream) destOutputProfile).getBytes());
            if (!ICC_DEVICE_CLASS_OUTPUT_PROFILE.equals(deviceClass) && !ICC_DEVICE_CLASS_MONITOR_PROFILE.equals(deviceClass)) {
                throw new PdfAConformanceException(PdfAConformanceException.ProfileStreamOfOutputintentShallBeOutputProfilePrtrOrMonitorProfileMntr);
            }

            String cs = IccProfile.getIccColorSpaceName(((PdfStream) destOutputProfile).getBytes());
            if (!ICC_COLOR_SPACE_RGB.equals(cs) && !ICC_COLOR_SPACE_CMYK.equals(cs) && !ICC_COLOR_SPACE_GRAY.equals(cs)) {
                throw new PdfAConformanceException(PdfAConformanceException.OutputIntentColorSpaceShallBeEitherGrayRgbOrCmyk);
            }
        }
    }

    @Override
    protected HashSet<PdfName> getForbiddenActions() {
        return forbiddenActions;
    }

    @Override
    protected HashSet<PdfName> getAllowedNamedActions() {
        return allowedNamedActions;
    }

    @Override
    protected void checkColorsUsages() {
        if (transparencyIsUsed && pdfAOutputIntentColorSpace == null) {
            throw new PdfAConformanceException(PdfAConformanceException.IfTheDocumentDoesNotContainAPdfAOutputIntentTransparencyIsForbidden);
        }

        if ((rgbIsUsed || cmykIsUsed || grayIsUsed) && pdfAOutputIntentColorSpace == null) {
            throw new PdfAConformanceException(PdfAConformanceException.IfDeviceRgbCmykGrayUsedInFileThatFileShallContainPdfaOutputIntentOrDefaultRgbCmykGrayInUsageContext);
        }

        if (rgbIsUsed) {
            if (!ICC_COLOR_SPACE_RGB.equals(pdfAOutputIntentColorSpace)) {
                throw new PdfAConformanceException(PdfAConformanceException.DevicergbMayBeUsedOnlyIfTheFileHasARgbPdfAOutputIntentOrDefaultRgbInUsageContext);
            }
        }
        if (cmykIsUsed) {
            if (!ICC_COLOR_SPACE_CMYK.equals(pdfAOutputIntentColorSpace)) {
                throw new PdfAConformanceException(PdfAConformanceException.DevicecmykMayBeUsedOnlyIfTheFileHasACmykPdfAOutputIntentOrDefaultCmykInUsageContext);
            }
        }
    }

    @Override
    protected void checkImage(PdfStream image, PdfDictionary currentColorSpaces) {
        if (image.containsKey(PdfName.Alternates)) {
            throw new PdfAConformanceException(PdfAConformanceException.AnImageDictionaryShallNotContainAlternatesKey);
        }
        if (image.containsKey(PdfName.OPI)) {
            throw new PdfAConformanceException(PdfAConformanceException.AnImageDictionaryShallNotContainOpiKey);
        }

        if (image.containsKey(PdfName.Interpolate) && image.getAsBool(PdfName.Interpolate)) {
            throw new PdfAConformanceException(PdfAConformanceException.TheValueOfInterpolateKeyShallNotBeTrue);
        }
        checkRenderingIntent(image.getAsName(PdfName.Intent));

        PdfObject colorSpaceObj = image.get(PdfName.ColorSpace);
        if (colorSpaceObj != null) {
            checkColorSpace(PdfColorSpace.makeColorSpace(colorSpaceObj, null), currentColorSpaces, true, null);
        }

        if (image.getAsStream(PdfName.SMask) != null) {
            transparencyIsUsed = true;
        }

        if (image.containsKey(PdfName.SMaskInData) && image.getAsInt(PdfName.SMaskInData) > 0) {
            transparencyIsUsed = true;
        }

        if (PdfName.JPXDecode.equals(image.get(PdfName.Filter))) {
            Jpeg2000Image jpgImage = (Jpeg2000Image) ImageFactory.getJpeg2000Image(image.getBytes());
            Jpeg2000Image.Parameters params = jpgImage.getParameters();

            /* Concerning !params.isJpxBaseline check
             *
             * In pdf/a-2 ISO (ISO 19005-2:2011  6.2.8.3 JPEG2000) is stated that:
             * "Only the JPX baseline set of features, ... , shall be used."
             *
             * Also in jpeg2000 ISO (ISO/IEC 15444-2:2004   Annex M: M.9.2 Support for JPX feature set) is stated that:
             * "In general, a JPX reader is not required to support the entire set of features defined within this Recommendation |International Standard.
             * However, to promote interoperability, the following baseline set of features is defined. Files that
             * are written in such a way as to allow a reader that supports only this JPX baseline set of features to properly open the
             * file shall contain a CLi field in the File Type box with the value 'jpxb' (0x6a70 7862); all JPX baseline readers are
             * required to properly support all files with this code in the compatibility list in the File Type box."
             *
             * Therefore, I assumed that a file, which doesn't has the jpxb flag (which can be checked with the isJpxBaseline flag)
             * uses not only JPX baseline set of features.
             *
             * But, all the test files used in iText5 failed on this check, so may be my assumption is wrong.
             */
            if (!params.isJp2 /*|| !params.isJpxBaseline*/) {
                throw new PdfAConformanceException(PdfAConformanceException.OnlyJpxBaselineSetOfFeaturesShallBeUsed);
            }

            if (params.numOfComps != 1 && params.numOfComps != 3 && params.numOfComps != 4) {
                throw new PdfAConformanceException(PdfAConformanceException.TheNumberOfColourChannelsInTheJpeg2000DataShallBe123);
            }

            if (params.colorSpecBoxes != null && params.colorSpecBoxes.size() > 1) {
                int numOfApprox0x01 = 0;
                for (Jpeg2000Image.ColorSpecBox colorSpecBox : params.colorSpecBoxes) {
                    if (colorSpecBox.getApprox() == 1) {
                        ++numOfApprox0x01;
                        if (numOfApprox0x01 == 1 &&
                                colorSpecBox.getMeth() != 1 && colorSpecBox.getMeth() != 2 && colorSpecBox.getMeth() != 3) {
                            throw new PdfAConformanceException(PdfAConformanceException.TheValueOfTheMethEntryInColrBoxShallBe123);
                        }

                        if (image.get(PdfName.ColorSpace) == null) {
                            switch(colorSpecBox.getEnumCs()) {
                                case 1:
                                    checkColorSpace(new PdfDeviceCs.Gray(), currentColorSpaces, true, null);
                                    break;
                                case 3:
                                    checkColorSpace(new PdfDeviceCs.Rgb(), currentColorSpaces, true, null);
                                    break;
                                case 12:
                                    checkColorSpace(new PdfDeviceCs.Cmyk(), currentColorSpaces, true, null);
                                    break;
                            }
                        }
                    }
                    if (colorSpecBox.getEnumCs() == 19) {
                        throw new PdfAConformanceException(PdfAConformanceException.Jpeg2000EnumeratedColourSpace19CIEJabShallNotBeUsed);
                    }
                }
                if (numOfApprox0x01 != 1) {
                    throw new PdfAConformanceException(PdfAConformanceException.ExactlyOneColourSpaceSpecificationShallHaveTheValue0x01InTheApproxField);
                }
            }

            if (jpgImage.getBpc() < 1 || jpgImage.getBpc() > 38) {
                throw new PdfAConformanceException(PdfAConformanceException.TheBitDepthOfTheJpeg2000DataShallHaveAValueInTheRange1To38);
            }

            // The Bits Per Component box specifies the bit depth of each component.
            // If the bit depth of all components in the codestream is the same (in both sign and precision),
            // then this box shall not be found. Otherwise, this box specifies the bit depth of each individual component.
            if (params.bpcBoxData != null) {
                throw new PdfAConformanceException(PdfAConformanceException.AllColourChannelsInTheJpeg2000DataShallHaveTheSameBitDepth);
            }
        }
    }

    @Override
     protected void checkFormXObject(PdfStream form) {
        if (form.containsKey(PdfName.OPI)) {
            throw new PdfAConformanceException(PdfAConformanceException.AFormXobjectDictionaryShallNotContainOpiKey);
        }
        if (form.containsKey(PdfName.PS)) {
            throw new PdfAConformanceException(PdfAConformanceException.AFormXobjectDictionaryShallNotContainPSKey);
        }
        if (PdfName.PS.equals(form.getAsName(PdfName.Subtype2))) {
            throw new PdfAConformanceException(PdfAConformanceException.AFormXobjectDictionaryShallNotContainSubtype2KeyWithAValueOfPS);
        }

        if (form.containsKey(PdfName.Group) && PdfName.Transparency.equals(form.getAsDictionary(PdfName.Group).getAsName(PdfName.S))) {
            transparencyIsUsed = true;
            PdfObject cs = form.getAsDictionary(PdfName.Group).get(PdfName.CS);
            PdfDictionary resources = form.getAsDictionary(PdfName.Resources);
            if (cs != null && resources != null) {
                PdfDictionary currentColorSpaces = resources.getAsDictionary(PdfName.ColorSpace);
                checkColorSpace(PdfColorSpace.makeColorSpace(cs, null), currentColorSpaces, true, null);
            }
        }
    }

    private void checkSeparationInsideDeviceN(PdfArray separation, PdfObject deviceNColorSpace, PdfIndirectReference deviceNTintTransform) {
        if (!isAltCSIsTheSame(separation.get(2), deviceNColorSpace) ||
                !deviceNTintTransform.equals(separation.getAsDictionary(3).getIndirectReference())) {
            throw new PdfAConformanceException(PdfAConformanceException.TintTransformAndAlternateSpaceOfSeparationArraysInTheColorantsOfDeviceNShallBeConsistentWithSameAttributesOfDeviceN);
        }

        checkSeparationCS(separation);
    }

    private void checkSeparationCS(PdfArray separation) {
        if (separationColorSpaces.containsKey(separation.getAsName(0))) {
            boolean altCSIsTheSame = false;
            boolean tintTransformIsTheSame = false;

            PdfArray sameNameSeparation = separationColorSpaces.get(separation.getAsName(0));
            PdfObject cs1 = separation.get(2);
            PdfObject cs2 = sameNameSeparation.get(2);
            altCSIsTheSame = isAltCSIsTheSame(cs1, cs2);

            PdfDictionary f1 = separation.getAsDictionary(3);
            PdfDictionary f2 = sameNameSeparation.getAsDictionary(3);
            //todo compare dictionaries or stream references
            tintTransformIsTheSame = f1.getIndirectReference().equals(f2.getIndirectReference());

            if (!altCSIsTheSame || !tintTransformIsTheSame) {
                throw new PdfAConformanceException(PdfAConformanceException.TintTransformAndAlternateSpaceShallBeTheSameForTheAllSeparationCSWithTheSameName);
            }
        } else {
            separationColorSpaces.put(separation.getAsName(0), separation);
        }

    }

    private boolean isAltCSIsTheSame(PdfObject cs1, PdfObject cs2) {
        boolean altCSIsTheSame = false;
        if (cs1 instanceof PdfName) {
            altCSIsTheSame = cs1.equals(cs2);
        } else if (cs1 instanceof PdfArray && cs2 instanceof PdfArray) {
            //todo compare cs dictionaries or stream reference
            altCSIsTheSame = ((PdfArray)cs1).get(0).equals(((PdfArray)cs1).get(0));
        }
        return altCSIsTheSame;
    }

    private void fillOrderRecursively(PdfArray orderArray, HashSet<PdfObject> order) {
        for (PdfObject orderItem : orderArray) {
            if (!orderItem.isArray()) {
                order.add(orderItem);
            } else {
                fillOrderRecursively((PdfArray) orderItem, order);
            }
        }
    }

    private boolean checkDefaultCS(PdfDictionary currentColorSpaces, Boolean fill, PdfName defaultCsName, int numOfComponents) {
        if (currentColorSpaces == null)
            return false;
        if (!currentColorSpaces.containsKey(defaultCsName))
            return false;

        PdfObject defaultCsObj = currentColorSpaces.get(defaultCsName);
        PdfColorSpace defaultCs = PdfColorSpace.makeColorSpace(defaultCsObj, null);
        if (defaultCs instanceof PdfDeviceCs)
            throw new PdfAConformanceException(PdfAConformanceException.ColorSpace1ShallBeDeviceIndependent).setMessageParams(defaultCsName.toString());

        if (defaultCs.getNumOfComponents() != numOfComponents)
            throw new PdfAConformanceException(PdfAConformanceException.ColorSpace1ShallHave2Components).setMessageParams(defaultCsName.toString(), numOfComponents);

        checkColorSpace(defaultCs, currentColorSpaces, false, fill);
        return true;
    }
}