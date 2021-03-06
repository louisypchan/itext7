/*

    This file is part of the iText (R) project.
    Copyright (c) 1998-2017 iText Group NV
    Authors: Bruno Lowagie, Paulo Soares, et al.

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation with the addition of the
    following permission added to Section 15 as permitted in Section 7(a):
    FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
    ITEXT GROUP. ITEXT GROUP DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
    OF THIRD PARTY RIGHTS

    This program is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License
    along with this program; if not, see http://www.gnu.org/licenses or write to
    the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
    Boston, MA, 02110-1301 USA, or download the license from the following URL:
    http://itextpdf.com/terms-of-use/

    The interactive user interfaces in modified source and object code versions
    of this program must display Appropriate Legal Notices, as required under
    Section 5 of the GNU Affero General Public License.

    In accordance with Section 7(b) of the GNU Affero General Public License,
    a covered work must retain the producer line in every PDF that is created
    or manipulated using iText.

    You can be released from the requirements of the license by purchasing
    a commercial license. Buying such a license is mandatory as soon as you
    develop commercial activities involving the iText software without
    disclosing the source code of your own applications.
    These activities include: offering paid services to customers as an ASP,
    serving PDFs on the fly in a web application, shipping iText with a closed
    source product.

    For more information, please contact iText Software Corp. at this
    address: sales@itextpdf.com
 */
package com.itextpdf.layout.renderer;

import com.itextpdf.io.LogMessageConstant;
import com.itextpdf.kernel.geom.AffineTransform;
import com.itextpdf.kernel.geom.Point;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.tagutils.IAccessibleElement;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.border.Border;
import com.itextpdf.layout.element.IElement;
import com.itextpdf.layout.layout.LayoutArea;
import com.itextpdf.layout.layout.LayoutContext;
import com.itextpdf.layout.layout.LayoutResult;
import com.itextpdf.layout.margincollapse.MarginsCollapseHandler;
import com.itextpdf.layout.margincollapse.MarginsCollapseInfo;
import com.itextpdf.layout.minmaxwidth.MinMaxWidth;
import com.itextpdf.layout.minmaxwidth.MinMaxWidthUtils;
import com.itextpdf.layout.property.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public abstract class BlockRenderer extends AbstractRenderer {

    protected BlockRenderer(IElement modelElement) {
        super(modelElement);
    }

    @Override
    public LayoutResult layout(LayoutContext layoutContext) {
        overrideHeightProperties();
        boolean wasHeightClipped = false;
        int pageNumber = layoutContext.getArea().getPageNumber();

        boolean isPositioned = isPositioned();

        Rectangle parentBBox = layoutContext.getArea().getBBox().clone();
        if (this.<Float>getProperty(Property.ROTATION_ANGLE) != null || isFixedLayout()) {
            parentBBox.moveDown(AbstractRenderer.INF - parentBBox.getHeight()).setHeight(AbstractRenderer.INF);
        }
        Float blockWidth = retrieveWidth(parentBBox.getWidth());

        List<Rectangle> floatRendererAreas = layoutContext.getFloatRendererAreas();
        FloatPropertyValue floatPropertyValue = this.<FloatPropertyValue>getProperty(Property.FLOAT);

        Float childrenMaxWidth = 0f;
        if (floatPropertyValue != null) {
            if (floatPropertyValue.equals(FloatPropertyValue.LEFT)) {
                setProperty(Property.HORIZONTAL_ALIGNMENT, HorizontalAlignment.LEFT);
            } else if (floatPropertyValue.equals(FloatPropertyValue.RIGHT)) {
                setProperty(Property.HORIZONTAL_ALIGNMENT, HorizontalAlignment.RIGHT);
            }
            Float minHeightProperty = this.<Float>getProperty(Property.MIN_HEIGHT);
            MinMaxWidth minMaxWidth = getMinMaxWidth(parentBBox.getWidth());
            childrenMaxWidth = minMaxWidth.getChildrenMaxWidth();
            if (minHeightProperty != null) {
                setProperty(Property.MIN_HEIGHT, minHeightProperty);
            } else {
                deleteProperty(Property.MIN_HEIGHT);
            }
        }


        if (blockWidth != null && blockWidth > childrenMaxWidth) {
            childrenMaxWidth = blockWidth;
        }

        MarginsCollapseHandler marginsCollapseHandler = null;
        boolean marginsCollapsingEnabled = Boolean.TRUE.equals(getPropertyAsBoolean(Property.COLLAPSING_MARGINS));
        boolean isCellRenderer = this instanceof CellRenderer;
        if (marginsCollapsingEnabled) {
            marginsCollapseHandler = new MarginsCollapseHandler(this, layoutContext.getMarginsCollapseInfo());
            if (!isCellRenderer) {
                marginsCollapseHandler.startMarginsCollapse(parentBBox);
            }
        }

        Border[] borders = getBorders();
        float[] paddings = getPaddings();
        applyBordersPaddingsMargins(parentBBox, borders, paddings);

        if (blockWidth != null && (blockWidth < parentBBox.getWidth() || isPositioned)) {
            parentBBox.setWidth((float) blockWidth);
        }
        if (floatPropertyValue != null && !FloatPropertyValue.NONE.equals(floatPropertyValue)) {
            Rectangle layoutBox = layoutContext.getArea().getBBox();
            float extremalRightBorder = layoutBox.getX() + layoutBox.getWidth();
            adjustBlockAreaAccordingToFloatRenderers(floatRendererAreas, parentBBox, extremalRightBorder, blockWidth, marginsCollapseHandler);
            if (parentBBox.getWidth() < childrenMaxWidth) {
                childrenMaxWidth = parentBBox.getWidth();
            }
        }

        Float blockMaxHeight = retrieveMaxHeight();
        if (!isFixedLayout() && null != blockMaxHeight && blockMaxHeight < parentBBox.getHeight()
                && !Boolean.TRUE.equals(getPropertyAsBoolean(Property.FORCED_PLACEMENT))) {
            float heightDelta = parentBBox.getHeight() - (float) blockMaxHeight;
            if (marginsCollapsingEnabled && !isCellRenderer) {
                marginsCollapseHandler.processFixedHeightAdjustment(heightDelta);
            }
            parentBBox.moveUp(heightDelta).setHeight((float) blockMaxHeight);
            wasHeightClipped = true;
        }

        float clearHeightCorrection = calculateClearHeightCorrection(floatRendererAreas, parentBBox);
        List<Rectangle> areas;
        if (isPositioned) {
            areas = Collections.singletonList(parentBBox);
        } else {
            areas = initElementAreas(new LayoutArea(pageNumber, parentBBox));
        }

        occupiedArea = new LayoutArea(pageNumber, new Rectangle(parentBBox.getX(), parentBBox.getY() + parentBBox.getHeight(), parentBBox.getWidth(), 0));
        shrinkOccupiedAreaForAbsolutePosition();
        int currentAreaPos = 0;

        Rectangle layoutBox = areas.get(0).clone();

        // the first renderer (one of childRenderers or their children) to produce LayoutResult.NOTHING
        IRenderer causeOfNothing = null;
        boolean anythingPlaced = false;
        for (int childPos = 0; childPos < childRenderers.size(); childPos++) {
            IRenderer childRenderer = childRenderers.get(childPos);
            LayoutResult result;
            childRenderer.setParent(this);
            MarginsCollapseInfo childMarginsInfo = null;
            if (marginsCollapsingEnabled) {
                childMarginsInfo = marginsCollapseHandler.startChildMarginsHandling(childRenderer, layoutBox);
            }
            while ((result = childRenderer.setParent(this).layout(new LayoutContext(new LayoutArea(pageNumber, layoutBox), childMarginsInfo, floatRendererAreas)))
                    .getStatus() != LayoutResult.FULL) {
                if (marginsCollapsingEnabled) {
                    if (result.getStatus() != LayoutResult.NOTHING) {
                        marginsCollapseHandler.endChildMarginsHandling(layoutBox);
                    }
                    if (!isCellRenderer) {
                        marginsCollapseHandler.endMarginsCollapse(layoutBox);
                    }
                }
                if (Boolean.TRUE.equals(getPropertyAsBoolean(Property.FILL_AVAILABLE_AREA_ON_SPLIT))
                        || Boolean.TRUE.equals(getPropertyAsBoolean(Property.FILL_AVAILABLE_AREA))) {
                    occupiedArea.setBBox(Rectangle.getCommonRectangle(occupiedArea.getBBox(), layoutBox));
                } else if (result.getOccupiedArea() != null && result.getStatus() != LayoutResult.NOTHING) {
                    occupiedArea.setBBox(Rectangle.getCommonRectangle(occupiedArea.getBBox(), result.getOccupiedArea().getBBox()));
                }

                if (result.getSplitRenderer() != null) {
                    // Use occupied area's bbox width so that for absolutely positioned renderers we do not align using full width
                    // in case when parent box should wrap around child boxes.
                    // TODO in the latter case, all elements should be layouted first so that we know maximum width needed to place all children and then apply horizontal alignment
                    alignChildHorizontally(result.getSplitRenderer(), occupiedArea.getBBox());
                }

                // Save the first renderer to produce LayoutResult.NOTHING
                if (null == causeOfNothing && null != result.getCauseOfNothing()) {
                    causeOfNothing = result.getCauseOfNothing();
                }

                // have more areas
                if (currentAreaPos + 1 < areas.size() && !(result.getAreaBreak() != null && result.getAreaBreak().getType() == AreaBreakType.NEXT_PAGE)) {
                    if (result.getStatus() == LayoutResult.PARTIAL) {
                        childRenderers.set(childPos, result.getSplitRenderer());
                        // TODO linkedList would make it faster
                        childRenderers.add(childPos + 1, result.getOverflowRenderer());
                    } else {
                        if (result.getOverflowRenderer() != null) {
                            childRenderers.set(childPos, result.getOverflowRenderer());
                        } else {
                            childRenderers.remove(childPos);
                        }
                        childPos--;
                    }
                    layoutBox = areas.get(++currentAreaPos).clone();
                    break;
                } else {
                    if (result.getStatus() == LayoutResult.PARTIAL) {
                        if (currentAreaPos + 1 == areas.size()) {
                            AbstractRenderer splitRenderer = createSplitRenderer(LayoutResult.PARTIAL);
                            splitRenderer.childRenderers = new ArrayList<>(childRenderers.subList(0, childPos));
                            splitRenderer.childRenderers.add(result.getSplitRenderer());
                            splitRenderer.occupiedArea = occupiedArea;

                            AbstractRenderer overflowRenderer = createOverflowRenderer(LayoutResult.PARTIAL);
                            // Apply forced placement only on split renderer
                            overflowRenderer.deleteOwnProperty(Property.FORCED_PLACEMENT);
                            List<IRenderer> overflowRendererChildren = new ArrayList<>();
                            overflowRendererChildren.add(result.getOverflowRenderer());
                            overflowRendererChildren.addAll(childRenderers.subList(childPos + 1, childRenderers.size()));
                            overflowRenderer.childRenderers = overflowRendererChildren;

                            if (hasProperty(Property.MAX_HEIGHT)) {
                                overflowRenderer.setProperty(Property.MAX_HEIGHT, retrieveMaxHeight() - occupiedArea.getBBox().getHeight());
                            }
                            if (hasProperty(Property.MIN_HEIGHT)) {
                                overflowRenderer.setProperty(Property.MIN_HEIGHT, retrieveMinHeight() - occupiedArea.getBBox().getHeight());
                            }
                            if (hasProperty(Property.HEIGHT)) {
                                overflowRenderer.setProperty(Property.HEIGHT, retrieveHeight() - occupiedArea.getBBox().getHeight());
                            }
                            if (wasHeightClipped) {
                                Logger logger = LoggerFactory.getLogger(TableRenderer.class);
                                logger.warn(LogMessageConstant.CLIP_ELEMENT);
                                occupiedArea.getBBox()
                                        .moveDown((float) blockMaxHeight - occupiedArea.getBBox().getHeight())
                                        .setHeight((float) blockMaxHeight);
                            }

                            applyPaddings(occupiedArea.getBBox(), paddings, true);
                            applyBorderBox(occupiedArea.getBBox(), borders, true);
                            applyMargins(occupiedArea.getBBox(), true);
                            if (wasHeightClipped) {
                                return new LayoutResult(LayoutResult.FULL, occupiedArea, splitRenderer, null);
                            } else {
                                return new LayoutResult(LayoutResult.PARTIAL, occupiedArea, splitRenderer, overflowRenderer, causeOfNothing);
                            }
                        } else {
                            childRenderers.set(childPos, result.getSplitRenderer());
                            childRenderers.add(childPos + 1, result.getOverflowRenderer());
                            layoutBox = areas.get(++currentAreaPos).clone();
                            break;
                        }
                    } else if (result.getStatus() == LayoutResult.NOTHING) {
                        boolean keepTogether = isKeepTogether();
                        int layoutResult = anythingPlaced && !keepTogether ? LayoutResult.PARTIAL : LayoutResult.NOTHING;
                        AbstractRenderer splitRenderer = createSplitRenderer(layoutResult);
                        splitRenderer.childRenderers = new ArrayList<>(childRenderers.subList(0, childPos));
                        for (IRenderer renderer : splitRenderer.childRenderers) {
                            renderer.setParent(splitRenderer);
                        }

                        AbstractRenderer overflowRenderer = createOverflowRenderer(layoutResult);
                        List<IRenderer> overflowRendererChildren = new ArrayList<>();
                        if (result.getOverflowRenderer() != null) {
                            overflowRendererChildren.add(result.getOverflowRenderer());
                        }
                        overflowRendererChildren.addAll(childRenderers.subList(childPos + 1, childRenderers.size()));

                        overflowRenderer.childRenderers = overflowRendererChildren;
                        if (isRelativePosition() && positionedRenderers.size() > 0) {
                            overflowRenderer.positionedRenderers = new ArrayList<>(positionedRenderers);
                        }
                        if (keepTogether) {
                            splitRenderer = null;
                            overflowRenderer.childRenderers.clear();
                            overflowRenderer.childRenderers = new ArrayList<>(childRenderers);
                        }

                        Float maxHeight = retrieveMaxHeight();
                        if (maxHeight != null) {
                            if (isPositioned) {
                                correctPositionedLayout(layoutBox);
                            }
                            overflowRenderer.setProperty(Property.MAX_HEIGHT, maxHeight - occupiedArea.getBBox().getHeight());
                        }
                        Float minHeight = retrieveMinHeight();
                        if (minHeight != null) {
                            overflowRenderer.setProperty(Property.MIN_HEIGHT, minHeight - occupiedArea.getBBox().getHeight());
                        }
                        Float height = retrieveHeight();
                        if (height != null) {
                            overflowRenderer.setProperty(Property.HEIGHT, height - occupiedArea.getBBox().getHeight());
                        }

                        if (wasHeightClipped) {
                            occupiedArea.getBBox()
                                    .moveDown((float) blockMaxHeight - occupiedArea.getBBox().getHeight())
                                    .setHeight((float) blockMaxHeight);
                            Logger logger = LoggerFactory.getLogger(TableRenderer.class);
                            logger.warn(LogMessageConstant.CLIP_ELEMENT);
                        }

                        applyPaddings(occupiedArea.getBBox(), paddings, true);
                        applyBorderBox(occupiedArea.getBBox(), borders, true);
                        applyMargins(occupiedArea.getBBox(), true);
                        //splitRenderer.occupiedArea = occupiedArea.clone();

                        if (Boolean.TRUE.equals(getPropertyAsBoolean(Property.FORCED_PLACEMENT)) || wasHeightClipped) {
                            return new LayoutResult(LayoutResult.FULL, occupiedArea, splitRenderer, null, null);
                        } else {
                            if (layoutResult != LayoutResult.NOTHING) {
                                return new LayoutResult(layoutResult, occupiedArea, splitRenderer, overflowRenderer, null).setAreaBreak(result.getAreaBreak());
                            } else {
                                return new LayoutResult(layoutResult, null, null, overflowRenderer, result.getCauseOfNothing()).setAreaBreak(result.getAreaBreak());
                            }
                        }
                    }
                }
            }
            anythingPlaced = true;

            if (result.getOccupiedArea() != null) {
                occupiedArea.setBBox(Rectangle.getCommonRectangle(occupiedArea.getBBox(), result.getOccupiedArea().getBBox()));
            }
            if (marginsCollapsingEnabled && !childRenderer.hasProperty(Property.FLOAT)) {
                marginsCollapseHandler.endChildMarginsHandling(layoutBox);
            }
            if (result.getStatus() == LayoutResult.FULL) {
                layoutBox.setHeight(result.getOccupiedArea().getBBox().getY() - layoutBox.getY());
                if (childRenderer.getOccupiedArea() != null) {
                    // Use occupied area's bbox width so that for absolutely positioned renderers we do not align using full width
                    // in case when parent box should wrap around child boxes.
                    // TODO in the latter case, all elements should be layouted first so that we know maximum width needed to place all children and then apply horizontal alignment
                    alignChildHorizontally(childRenderer, occupiedArea.getBBox());
                }
            }

            // Save the first renderer to produce LayoutResult.NOTHING
            if (null == causeOfNothing && null != result.getCauseOfNothing()) {
                causeOfNothing = result.getCauseOfNothing();
            }
        }
        if (marginsCollapsingEnabled && !isCellRenderer) {
            marginsCollapseHandler.endMarginsCollapse(layoutBox);
        }

        if (Boolean.TRUE.equals(getPropertyAsBoolean(Property.FILL_AVAILABLE_AREA))) {
            occupiedArea.setBBox(Rectangle.getCommonRectangle(occupiedArea.getBBox(), layoutBox));
        }

        IRenderer overflowRenderer = null;
        Float blockMinHeight = retrieveMinHeight();
        if (!Boolean.TRUE.equals(getPropertyAsBoolean(Property.FORCED_PLACEMENT)) && null != blockMinHeight && blockMinHeight > occupiedArea.getBBox().getHeight()) {
            if (isFixedLayout()) {
                occupiedArea.getBBox().moveDown((float) blockMinHeight - occupiedArea.getBBox().getHeight()).setHeight((float) blockMinHeight);
            } else {
                float blockBottom = Math.max(occupiedArea.getBBox().getBottom() - ((float) blockMinHeight - occupiedArea.getBBox().getHeight()), layoutBox.getBottom());
                occupiedArea.getBBox()
                        .increaseHeight(occupiedArea.getBBox().getBottom() - blockBottom)
                        .setY(blockBottom);
                blockMinHeight -= occupiedArea.getBBox().getHeight();
                if (!isFixedLayout() && blockMinHeight > AbstractRenderer.EPS) {
                    if (isKeepTogether()) {
                        return new LayoutResult(LayoutResult.NOTHING, null, null, this, this);
                    } else {
                        overflowRenderer = createOverflowRenderer(LayoutResult.PARTIAL);
                        overflowRenderer.setProperty(Property.MIN_HEIGHT, (float) blockMinHeight);
                        if (hasProperty(Property.HEIGHT)) {
                            overflowRenderer.setProperty(Property.HEIGHT, retrieveHeight() - occupiedArea.getBBox().getHeight());
                        }
                    }
                }
            }
        }

        if (isPositioned) {
            correctPositionedLayout(layoutBox);
        }

        float initialWidth = occupiedArea.getBBox().getWidth();
        applyPaddings(occupiedArea.getBBox(), paddings, true);
        applyBorderBox(occupiedArea.getBBox(), borders, true);
        if (positionedRenderers.size() > 0) {
            LayoutArea area = new LayoutArea(occupiedArea.getPageNumber(), occupiedArea.getBBox().clone());
            applyBorderBox(area.getBBox(), false);
            for (IRenderer childPositionedRenderer : positionedRenderers) {
                childPositionedRenderer.setParent(this).layout(new LayoutContext(area));
            }
            applyBorderBox(area.getBBox(), true);
        }
        Rectangle rect = applyMargins(occupiedArea.getBBox(), true);
        childrenMaxWidth = childrenMaxWidth != 0 ? childrenMaxWidth + rect.getWidth() - initialWidth : 0;
        if (this.<Float>getProperty(Property.ROTATION_ANGLE) != null) {
            applyRotationLayout(layoutContext.getArea().getBBox().clone());
            if (isNotFittingLayoutArea(layoutContext.getArea())) {
                if (!Boolean.TRUE.equals(getPropertyAsBoolean(Property.FORCED_PLACEMENT))) {
                    return new LayoutResult(LayoutResult.NOTHING, null, null, this, this);
                }
            }
        }
        applyVerticalAlignment();
        removeUnnecessaryFloatRendererAreas(floatRendererAreas);

        LayoutArea editedArea = applyFloatPropertyOnCurrentArea(floatRendererAreas, layoutContext.getArea().getBBox().getWidth(), childrenMaxWidth);

        if (floatPropertyValue != null && !floatPropertyValue.equals(FloatPropertyValue.NONE)) {
            Document document = getDocument();
            float bottomMargin = document == null ? 0 : document.getBottomMargin();
            if (occupiedArea.getBBox().getY() < bottomMargin) {
                floatRendererAreas.clear();
                return new LayoutResult(LayoutResult.NOTHING, null, null, this, null);
            }
        }

        adjustLayoutAreaIfClearPropertyPresent(clearHeightCorrection, editedArea, floatPropertyValue);

        if (null == overflowRenderer) {
            return new LayoutResult(LayoutResult.FULL, editedArea, null, null, causeOfNothing);
        } else {
            return new LayoutResult(LayoutResult.PARTIAL, editedArea, this, overflowRenderer, causeOfNothing);
        }
    }

    protected AbstractRenderer createSplitRenderer(int layoutResult) {
        AbstractRenderer splitRenderer = (AbstractRenderer) getNextRenderer();
        splitRenderer.parent = parent;
        splitRenderer.modelElement = modelElement;
        splitRenderer.occupiedArea = occupiedArea;
        splitRenderer.isLastRendererForModelElement = false;
        splitRenderer.properties = new HashMap<>(properties);
        return splitRenderer;
    }

    protected AbstractRenderer createOverflowRenderer(int layoutResult) {
        AbstractRenderer overflowRenderer = (AbstractRenderer) getNextRenderer();
        overflowRenderer.parent = parent;
        overflowRenderer.modelElement = modelElement;
        overflowRenderer.properties = new HashMap<>(properties);
        return overflowRenderer;
    }

    @Override
    public void draw(DrawContext drawContext) {
        if (occupiedArea == null) {
            Logger logger = LoggerFactory.getLogger(BlockRenderer.class);
            logger.error(LogMessageConstant.OCCUPIED_AREA_HAS_NOT_BEEN_INITIALIZED);
            return;
        }
        applyDestinationsAndAnnotation(drawContext);

        PdfDocument document = drawContext.getDocument();
        boolean isTagged = drawContext.isTaggingEnabled() && getModelElement() instanceof IAccessibleElement;
        TagTreePointer tagPointer = null;
        IAccessibleElement accessibleElement = null;
        if (isTagged) {
            accessibleElement = (IAccessibleElement) getModelElement();
            PdfName role = accessibleElement.getRole();
            if (role != null && !PdfName.Artifact.equals(role)) {
                tagPointer = document.getTagStructureContext().getAutoTaggingPointer();
                boolean alreadyCreated = tagPointer.isElementConnectedToTag(accessibleElement);
                tagPointer.addTag(accessibleElement, true);
                if (!alreadyCreated) {
                    if (role.equals(PdfName.L)) {
                        PdfDictionary listAttributes = AccessibleAttributesApplier.getListAttributes(this, tagPointer);
                        applyGeneratedAccessibleAttributes(tagPointer, listAttributes);
                    }

                    if (role.equals(PdfName.TD) || role.equals(PdfName.TH)) {
                        PdfDictionary tableAttributes = AccessibleAttributesApplier.getTableAttributes(this, tagPointer);
                        applyGeneratedAccessibleAttributes(tagPointer, tableAttributes);
                    }

                    PdfDictionary layoutAttributes = AccessibleAttributesApplier.getLayoutAttributes(role, this, tagPointer);
                    applyGeneratedAccessibleAttributes(tagPointer, layoutAttributes);
                }
            } else {
                isTagged = false;
            }
        }

        boolean isRelativePosition = isRelativePosition();
        if (isRelativePosition) {
            applyRelativePositioningTranslation(false);
        }

        beginElementOpacityApplying(drawContext);
        beginRotationIfApplied(drawContext.getCanvas());

        drawBackground(drawContext);
        drawBorder(drawContext);
        drawChildren(drawContext);
        drawPositionedChildren(drawContext);

        endRotationIfApplied(drawContext.getCanvas());
        endElementOpacityApplying(drawContext);

        if (isRelativePosition) {
            applyRelativePositioningTranslation(true);
        }

        if (isTagged) {
            tagPointer.moveToParent();
            if (isLastRendererForModelElement) {
                document.getTagStructureContext().removeElementConnectionToTag(accessibleElement);
            }
        }

        flushed = true;
    }

    @Override
    public Rectangle getOccupiedAreaBBox() {
        Rectangle bBox = occupiedArea.getBBox().clone();
        Float rotationAngle = this.<Float>getProperty(Property.ROTATION_ANGLE);
        if (rotationAngle != null) {
            if (!hasOwnProperty(Property.ROTATION_INITIAL_WIDTH) || !hasOwnProperty(Property.ROTATION_INITIAL_HEIGHT)) {
                Logger logger = LoggerFactory.getLogger(BlockRenderer.class);
                logger.error(MessageFormat.format(LogMessageConstant.ROTATION_WAS_NOT_CORRECTLY_PROCESSED_FOR_RENDERER, getClass().getSimpleName()));
            } else {
                bBox.setWidth((float) this.getPropertyAsFloat(Property.ROTATION_INITIAL_WIDTH));
                bBox.setHeight((float) this.getPropertyAsFloat(Property.ROTATION_INITIAL_HEIGHT));
            }
        }
        return bBox;
    }

    protected void applyVerticalAlignment() {
        VerticalAlignment verticalAlignment = this.<VerticalAlignment>getProperty(Property.VERTICAL_ALIGNMENT);
        if (verticalAlignment != null && verticalAlignment != VerticalAlignment.TOP && childRenderers.size() > 0) {
            LayoutArea lastChildOccupiedArea = childRenderers.get(childRenderers.size() - 1).getOccupiedArea();
            float deltaY = lastChildOccupiedArea.getBBox().getY() - getInnerAreaBBox().getY();
            switch (verticalAlignment) {
                case BOTTOM:
                    for (IRenderer child : childRenderers) {
                        child.move(0, -deltaY);
                    }
                    break;
                case MIDDLE:
                    for (IRenderer child : childRenderers) {
                        child.move(0, -deltaY / 2);
                    }
                    break;
            }
        }
    }

    protected void applyRotationLayout(Rectangle layoutBox) {
        float angle = (float) this.getPropertyAsFloat(Property.ROTATION_ANGLE);

        float x = occupiedArea.getBBox().getX();
        float y = occupiedArea.getBBox().getY();
        float height = occupiedArea.getBBox().getHeight();
        float width = occupiedArea.getBBox().getWidth();

        setProperty(Property.ROTATION_INITIAL_WIDTH, width);
        setProperty(Property.ROTATION_INITIAL_HEIGHT, height);

        AffineTransform rotationTransform = new AffineTransform();

        // here we calculate and set the actual occupied area of the rotated content
        if (isPositioned()) {
            Float rotationPointX = this.getPropertyAsFloat(Property.ROTATION_POINT_X);
            Float rotationPointY = this.getPropertyAsFloat(Property.ROTATION_POINT_Y);

            if (rotationPointX == null || rotationPointY == null) {
                // if rotation point was not specified, the most bottom-left point is used
                rotationPointX = x;
                rotationPointY = y;
            }

            // transforms apply from bottom to top
            rotationTransform.translate((float) rotationPointX, (float) rotationPointY); // move point back at place
            rotationTransform.rotate(angle); // rotate
            rotationTransform.translate((float) -rotationPointX, (float) -rotationPointY); // move rotation point to origin

            List<Point> rotatedPoints = transformPoints(rectangleToPointsList(occupiedArea.getBBox()), rotationTransform);
            Rectangle newBBox = calculateBBox(rotatedPoints);

            // make occupied area be of size and position of actual content
            occupiedArea.getBBox().setWidth(newBBox.getWidth());
            occupiedArea.getBBox().setHeight(newBBox.getHeight());
            float occupiedAreaShiftX = newBBox.getX() - x;
            float occupiedAreaShiftY = newBBox.getY() - y;
            move(occupiedAreaShiftX, occupiedAreaShiftY);
        } else {
            rotationTransform = AffineTransform.getRotateInstance(angle);
            List<Point> rotatedPoints = transformPoints(rectangleToPointsList(occupiedArea.getBBox()), rotationTransform);
            float[] shift = calculateShiftToPositionBBoxOfPointsAt(x, y + height, rotatedPoints);

            for (Point point : rotatedPoints) {
                point.setLocation(point.getX() + shift[0], point.getY() + shift[1]);
            }

            Rectangle newBBox = calculateBBox(rotatedPoints);

            occupiedArea.getBBox().setWidth(newBBox.getWidth());
            occupiedArea.getBBox().setHeight(newBBox.getHeight());

            float heightDiff = height - newBBox.getHeight();
            move(0, heightDiff);
        }
    }

    /**
     * @deprecated Will be removed in iText 7.1
     */
    @Deprecated
    protected float[] applyRotation() {
        float[] ctm = new float[6];
        createRotationTransformInsideOccupiedArea().getMatrix(ctm);
        return ctm;
    }

    /**
     * This method creates {@link AffineTransform} instance that could be used
     * to rotate content inside the occupied area. Be aware that it should be used only after
     * layout rendering is finished and correct occupied area for the rotated element is calculated.
     *
     * @return {@link AffineTransform} that rotates the content and places it inside occupied area.
     */
    protected AffineTransform createRotationTransformInsideOccupiedArea() {
        Float angle = this.<Float>getProperty(Property.ROTATION_ANGLE);
        AffineTransform rotationTransform = AffineTransform.getRotateInstance((float) angle);

        Rectangle contentBox = this.getOccupiedAreaBBox();
        List<Point> rotatedContentBoxPoints = transformPoints(rectangleToPointsList(contentBox), rotationTransform);
        // Occupied area for rotated elements is already calculated on layout in such way to enclose rotated content;
        // therefore we can simply rotate content as is and then shift it to the occupied area.
        float[] shift = calculateShiftToPositionBBoxOfPointsAt(occupiedArea.getBBox().getLeft(), occupiedArea.getBBox().getTop(), rotatedContentBoxPoints);
        rotationTransform.preConcatenate(AffineTransform.getTranslateInstance(shift[0], shift[1]));

        return rotationTransform;
    }

    protected void beginRotationIfApplied(PdfCanvas canvas) {
        Float angle = this.getPropertyAsFloat(Property.ROTATION_ANGLE);
        if (angle != null) {
            if (!hasOwnProperty(Property.ROTATION_INITIAL_HEIGHT)) {
                Logger logger = LoggerFactory.getLogger(BlockRenderer.class);
                logger.error(MessageFormat.format(LogMessageConstant.ROTATION_WAS_NOT_CORRECTLY_PROCESSED_FOR_RENDERER, getClass().getSimpleName()));
            } else {
                AffineTransform transform = createRotationTransformInsideOccupiedArea();
                canvas.saveState().concatMatrix(transform);
            }
        }
    }

    protected void endRotationIfApplied(PdfCanvas canvas) {
        Float angle = this.getPropertyAsFloat(Property.ROTATION_ANGLE);
        if (angle != null) {
            canvas.restoreState();
        }
    }

    protected void correctPositionedLayout(Rectangle layoutBox) {
        if (isFixedLayout()) {
            float y = (float) this.getPropertyAsFloat(Property.Y);
            float relativeY = isFixedLayout() ? 0 : layoutBox.getY();
            move(0, relativeY + y - occupiedArea.getBBox().getY());
        } else {
            //TODO
        }
    }

    protected float applyBordersPaddingsMargins(Rectangle parentBBox, Border[] borders, float[] paddings) {
        float parentWidth  = parentBBox.getWidth();

        applyMargins(parentBBox, false);
        applyBorderBox(parentBBox, borders, false);
        if (isPositioned()) {
            if (isFixedLayout()) {
                float x = (float) this.getPropertyAsFloat(Property.X);
                float relativeX = isFixedLayout() ? 0 : parentBBox.getX();
                parentBBox.setX(relativeX + x);
            }  else if (isAbsolutePosition()) {
                applyAbsolutePosition(parentBBox);
            }
        }
        applyPaddings(parentBBox, paddings, false);
        return parentWidth - parentBBox.getWidth();
    }

    @Override
    protected MinMaxWidth getMinMaxWidth(float availableWidth) {
        Rectangle area = new Rectangle(availableWidth, AbstractRenderer.INF);
        float additionalWidth = applyBordersPaddingsMargins(area, getBorders(), getPaddings());
        MinMaxWidth minMaxWidth = new MinMaxWidth(additionalWidth, availableWidth);
        AbstractWidthHandler handler = new MaxMaxWidthHandler(minMaxWidth);
        for (IRenderer childRenderer : childRenderers) {
            MinMaxWidth childMinMaxWidth;
            childRenderer.setParent(this);
            if (childRenderer instanceof AbstractRenderer) {
                childMinMaxWidth = ((AbstractRenderer)childRenderer).getMinMaxWidth(area.getWidth());
            } else {
                childMinMaxWidth = MinMaxWidthUtils.countDefaultMinMaxWidth(childRenderer, area.getWidth());
            }
            handler.updateMaxChildWidth(childMinMaxWidth.getMaxWidth());
            handler.updateMinChildWidth(childMinMaxWidth.getMinWidth());
        }
        return countRotationMinMaxWidth(correctMinMaxWidth(minMaxWidth));
    }

    //Heuristic method.
    //We assume that the area of block stays the same when we try to layout it
    //with different available width (available width is between min-width and max-width).
    MinMaxWidth countRotationMinMaxWidth(MinMaxWidth minMaxWidth) {
        Float rotation = this.getPropertyAsFloat(Property.ROTATION_ANGLE);
        if (rotation != null) {
            boolean restoreRendererRotation = hasOwnProperty(Property.ROTATION_ANGLE);
            setProperty(Property.ROTATION_ANGLE, null);
            LayoutResult result = layout(new LayoutContext(new LayoutArea(1, new Rectangle(minMaxWidth.getMaxWidth() + MinMaxWidthUtils.getEps(), AbstractRenderer.INF))));
            if (restoreRendererRotation) {
                setProperty(Property.ROTATION_ANGLE, rotation);
            } else {
                deleteOwnProperty(Property.ROTATION_ANGLE);
            }
            if (result.getOccupiedArea() != null) {
                double a = result.getOccupiedArea().getBBox().getWidth();
                double b = result.getOccupiedArea().getBBox().getHeight();
                double m = minMaxWidth.getMinWidth();
                double s = a * b;
                //Note, that the width of occupied area containing rotated block is less than the diagonal of this block, so:
                //width < sqrt(a^2 + b^2)
                //a^2 + b^2 = (s/b)^2 + b^2 >= 2s
                //(s/b)^2 + b^2 = 2s,  if b = s/b = sqrt(s)
                double resultMinWidth =  Math.sqrt(2 * s);
                //Note, that if the sqrt(s) < m (width of unrotated block is out of possible range), than the min value of (s/b)^2 + b^2 >= 2s should be when b = m
                if ( Math.sqrt(s) < minMaxWidth.getMinWidth()) {
                    resultMinWidth = Math.max(resultMinWidth, Math.sqrt((s / m) * (s / m) + m * m));
                }
                //We assume that the biggest diagonal is when block element have maxWidth.
                return new MinMaxWidth(0, minMaxWidth.getAvailableWidth(), (float) resultMinWidth, (float) Math.sqrt(a * a + b * b));
            }
        }
        return minMaxWidth;
    }

    MinMaxWidth correctMinMaxWidth(MinMaxWidth minMaxWidth) {
        Float width = retrieveWidth(-1);
        if (width != null && width >= 0 && width >= minMaxWidth.getChildrenMinWidth()) {
            minMaxWidth.setChildrenMaxWidth((float) width);
            minMaxWidth.setChildrenMinWidth((float) width);
        }
        return minMaxWidth;
    }

    private List<Point> clipPolygon(List<Point> points, Point clipLineBeg, Point clipLineEnd) {
        List<Point> filteredPoints = new ArrayList<>();

        boolean prevOnRightSide = false;
        Point filteringPoint = points.get(0);
        if (checkPointSide(filteringPoint, clipLineBeg, clipLineEnd) >= 0) {
            filteredPoints.add(filteringPoint);
            prevOnRightSide = true;
        }

        Point prevPoint = filteringPoint;
        for (int i = 1; i < points.size() + 1; ++i) {
            filteringPoint = points.get(i % points.size());
            if (checkPointSide(filteringPoint, clipLineBeg, clipLineEnd) >= 0) {
                if (!prevOnRightSide) {
                    filteredPoints.add(getIntersectionPoint(prevPoint, filteringPoint, clipLineBeg, clipLineEnd));
                }
                filteredPoints.add(filteringPoint);
                prevOnRightSide = true;
            } else if (prevOnRightSide) {
                filteredPoints.add(getIntersectionPoint(prevPoint, filteringPoint, clipLineBeg, clipLineEnd));
            }

            prevPoint = filteringPoint;
        }

        return filteredPoints;
    }

    private int checkPointSide(Point filteredPoint, Point clipLineBeg, Point clipLineEnd) {
        double x1, x2, y1, y2;
        x1 = filteredPoint.getX() - clipLineBeg.getX();
        y2 = clipLineEnd.getY() - clipLineBeg.getY();

        x2 = clipLineEnd.getX() - clipLineBeg.getX();
        y1 = filteredPoint.getY() - clipLineBeg.getY();

        double sgn = x1 * y2 - x2 * y1;

        if (Math.abs(sgn) < 0.001) return 0;
        if (sgn > 0) return 1;
        if (sgn < 0) return -1;

        return 0;
    }

    private Point getIntersectionPoint(Point lineBeg, Point lineEnd, Point clipLineBeg, Point clipLineEnd) {
        double A1 = lineBeg.getY() - lineEnd.getY(), A2 = clipLineBeg.getY() - clipLineEnd.getY();
        double B1 = lineEnd.getX() - lineBeg.getX(), B2 = clipLineEnd.getX() - clipLineBeg.getX();
        double C1 = lineBeg.getX() * lineEnd.getY() - lineBeg.getY() * lineEnd.getX();
        double C2 = clipLineBeg.getX() * clipLineEnd.getY() - clipLineBeg.getY() * clipLineEnd.getX();

        double M = B1 * A2 - B2 * A1;

        return new Point((B2 * C1 - B1 * C2) / M, (C2 * A1 - C1 * A2) / M);
    }
}
