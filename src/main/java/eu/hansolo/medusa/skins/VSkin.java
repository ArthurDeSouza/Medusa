/*
 * Copyright (c) 2016 by Gerrit Grunwald
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.hansolo.medusa.skins;

import eu.hansolo.medusa.Fonts;
import eu.hansolo.medusa.Gauge;
import eu.hansolo.medusa.Gauge.LedType;
import eu.hansolo.medusa.Gauge.NeedleType;
import eu.hansolo.medusa.Gauge.ScaleDirection;
import eu.hansolo.medusa.Needle;
import eu.hansolo.medusa.TickLabelLocation;
import eu.hansolo.medusa.Marker;
import eu.hansolo.medusa.Section;
import eu.hansolo.medusa.tools.AngleConicalGradient;
import eu.hansolo.medusa.tools.Helper;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Rotate;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Created by hansolo on 19.01.16.
 */
public class VSkin extends SkinBase<Gauge> implements Skin<Gauge> {
    private static final double             PREFERRED_WIDTH  = 125;
    private static final double             PREFERRED_HEIGHT = 250;
    private static final double             MINIMUM_WIDTH    = 50;
    private static final double             MINIMUM_HEIGHT   = 50;
    private static final double             MAXIMUM_WIDTH    = 1024;
    private static final double             MAXIMUM_HEIGHT   = 1024;
    private static final double             ASPECT_RATIO     = 2;
    private              Map<Marker, Shape> markerMap        = new ConcurrentHashMap<>();
    private double                   oldValue;
    private double                   width;
    private double                   height;
    private Pane                     pane;
    private InnerShadow              backgroundInnerShadow;
    private Canvas                   sectionsAndAreasCanvas;
    private GraphicsContext          sectionsAndAreasCtx;
    private Canvas                   tickMarkCanvas;
    private GraphicsContext          tickMarkCtx;
    private double                   ledSize;
    private InnerShadow              ledOnShadow;
    private InnerShadow              ledOffShadow;
    private Paint                    ledFramePaint;
    private Paint                    ledOnPaint;
    private Paint                    ledOffPaint;
    private Paint                    ledHighlightPaint;
    private Canvas                   ledCanvas;
    private GraphicsContext          ledCtx;
    private Pane                     markerPane;
    private Path                     threshold;
    private Path                     needle;
    private Rotate                   needleRotate;
    private Paint                    needlePaint;
    private Canvas                   knobCanvas;
    private GraphicsContext          knobCtx;
    private Group                    shadowGroup;
    private DropShadow               dropShadow;
    private Text                     titleText;
    private Text                     unitText;
    private Text                     valueText;
    private double                   startAngle;
    private double                   angleRange;
    private double                   angleStep;
    private EventHandler<MouseEvent> mouseHandler;
    private Tooltip                  buttonTooltip;
    private Tooltip                  thresholdTooltip;
    private String                   formatString;
    private double                   minValue;
    private double                   maxValue;
    private List<Section>            sections;
    private boolean                  highlightSections;
    private boolean                  sectionsVisible;
    private List<Section>            areas;
    private boolean                  highlightAreas;
    private boolean                  areasVisible;
    private TickLabelLocation        tickLabelLocation;
    private ScaleDirection           scaleDirection;


    // ******************** Constructors **************************************
    public VSkin(Gauge gauge) {
        super(gauge);
        if (gauge.isAutoScale()) gauge.calcAutoScale();
        angleRange        = Helper.clamp(90d, 180d, gauge.getAngleRange());
        startAngle        = getStartAngle();
        angleStep         = angleRange / gauge.getRange();
        oldValue          = gauge.getValue();
        minValue          = gauge.getMinValue();
        maxValue          = gauge.getMaxValue();
        formatString      = new StringBuilder("%.").append(Integer.toString(gauge.getDecimals())).append("f").toString();
        sections          = gauge.getSections();
        highlightSections = gauge.isHighlightSections();
        sectionsVisible   = gauge.getSectionsVisible();
        areas             = gauge.getAreas();
        highlightAreas    = gauge.isHighlightAreas();
        areasVisible      = gauge.getAreasVisible();
        tickLabelLocation = gauge.getTickLabelLocation();
        scaleDirection    = gauge.getScaleDirection();
        mouseHandler      = event -> handleMouseEvent(event);
        updateMarkers();

        init();
        initGraphics();
        registerListeners();
    }


    // ******************** Initialization ************************************
    private void init() {
        if (Double.compare(getSkinnable().getPrefWidth(), 0.0) <= 0 || Double.compare(getSkinnable().getPrefHeight(), 0.0) <= 0 ||
            Double.compare(getSkinnable().getWidth(), 0.0) <= 0 || Double.compare(getSkinnable().getHeight(), 0.0) <= 0) {
            if (getSkinnable().getPrefWidth() < 0 && getSkinnable().getPrefHeight() < 0) {
                getSkinnable().setPrefSize(PREFERRED_WIDTH, PREFERRED_HEIGHT);
            }
        }

        if (Double.compare(getSkinnable().getMinWidth(), 0.0) <= 0 || Double.compare(getSkinnable().getMinHeight(), 0.0) <= 0) {
            getSkinnable().setMinSize(MINIMUM_WIDTH, MINIMUM_HEIGHT);
        }

        if (Double.compare(getSkinnable().getMaxWidth(), 0.0) <= 0 || Double.compare(getSkinnable().getMaxHeight(), 0.0) <= 0) {
            getSkinnable().setMaxSize(MAXIMUM_WIDTH, MAXIMUM_HEIGHT);
        }
    }

    private void initGraphics() {
        backgroundInnerShadow = new InnerShadow(BlurType.TWO_PASS_BOX, Color.rgb(10, 10, 10, 0.45), 8, 0d, 8d, 0d);

        sectionsAndAreasCanvas = new Canvas();
        sectionsAndAreasCtx    = sectionsAndAreasCanvas.getGraphicsContext2D();
        Helper.enableNode(sectionsAndAreasCanvas, areasVisible | sectionsVisible);

        tickMarkCanvas = new Canvas();
        tickMarkCtx    = tickMarkCanvas.getGraphicsContext2D();

        ledCanvas = new Canvas();
        ledCtx    = ledCanvas.getGraphicsContext2D();
        Helper.enableNode(ledCanvas, getSkinnable().isLedVisible());

        thresholdTooltip = new Tooltip("Threshold\n(" + String.format(Locale.US, formatString, getSkinnable().getThreshold()) + ")");
        thresholdTooltip.setTextAlignment(TextAlignment.CENTER);

        threshold = new Path();
        Helper.enableNode(threshold, getSkinnable().isThresholdVisible());
        Tooltip.install(threshold, thresholdTooltip);

        markerPane = new Pane();

        needleRotate = new Rotate(180 - startAngle);
        needleRotate.setAngle(needleRotate.getAngle() + (getSkinnable().getValue() - oldValue - minValue) * angleStep);
        needle = new Path();
        needle.setFillRule(FillRule.EVEN_ODD);
        needle.getTransforms().setAll(needleRotate);
        needle.setStrokeType(StrokeType.INSIDE);
        needle.setStroke(Color.TRANSPARENT);

        buttonTooltip    = new Tooltip();
        buttonTooltip.setTextAlignment(TextAlignment.CENTER);

        knobCanvas = new Canvas();
        knobCtx    = knobCanvas.getGraphicsContext2D();
        knobCanvas.setPickOnBounds(false);
        Helper.enableNode(knobCanvas, getSkinnable().isKnobVisible());

        dropShadow = new DropShadow();
        dropShadow.setColor(Color.rgb(0, 0, 0, 0.25));
        dropShadow.setBlurType(BlurType.TWO_PASS_BOX);
        dropShadow.setRadius(0.015 * PREFERRED_WIDTH);
        dropShadow.setOffsetY(0.015 * PREFERRED_WIDTH);

        shadowGroup = new Group(needle, knobCanvas);
        shadowGroup.setEffect(getSkinnable().isShadowsEnabled() ? dropShadow : null);

        titleText = new Text(getSkinnable().getTitle());
        titleText.setTextOrigin(VPos.CENTER);
        titleText.setMouseTransparent(true);

        unitText = new Text(getSkinnable().getUnit());
        unitText.setMouseTransparent(true);
        unitText.setTextOrigin(VPos.CENTER);
        unitText.setMouseTransparent(true);

        valueText = new Text(String.format(Locale.US, formatString, getSkinnable().getValue()));
        valueText.setMouseTransparent(true);
        valueText.setTextOrigin(VPos.CENTER);
        valueText.setMouseTransparent(true);

        // Set initial value
        double targetAngle = 180 - startAngle + (getSkinnable().getCurrentValue() - minValue) * angleStep;
        targetAngle        = Helper.clamp(180 - startAngle, 180 - startAngle + angleRange, targetAngle);
        needleRotate.setAngle(targetAngle);

        // Add all nodes
        pane = new Pane(sectionsAndAreasCanvas,
                        tickMarkCanvas,
                        markerPane,
                        ledCanvas,
                        titleText,
                        unitText,
                        valueText,
                        shadowGroup);
        pane.setBorder(new Border(new BorderStroke(getSkinnable().getBorderPaint(), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(getSkinnable().getBorderWidth()))));
        pane.setBackground(new Background(new BackgroundFill(getSkinnable().getBackgroundPaint(), CornerRadii.EMPTY, Insets.EMPTY)));

        getChildren().setAll(pane);
    }

    private void registerListeners() {
        getSkinnable().widthProperty().addListener(o -> handleEvents("RESIZE"));
        getSkinnable().heightProperty().addListener(o -> handleEvents("RESIZE"));
        getSkinnable().getMarkers().addListener((ListChangeListener<Marker>) c -> {
            updateMarkers();
            redraw();
        });

        getSkinnable().setOnUpdate(e -> handleEvents(e.eventType.name()));
        getSkinnable().currentValueProperty().addListener(e -> rotateNeedle(getSkinnable().getCurrentValue()));

        handleEvents("INTERACTIVITY");
        handleEvents("VISIBILITY");
    }


    // ******************** Methods *******************************************
    protected void handleEvents(final String EVENT_TYPE) {
        if ("RESIZE".equals(EVENT_TYPE)) {
            resize();
            redraw();
        } else if ("FINISHED".equals(EVENT_TYPE)) {
            double currentValue = getSkinnable().getCurrentValue();
            // Check sections for value and fire section events
            if (getSkinnable().getCheckSectionsForValue()) {
                int listSize = sections.size();
                for (int i = 0 ; i < listSize ; i++) { sections.get(i).checkForValue(currentValue); }
            }

            // Check areas for value and fire section events
            if (getSkinnable().getCheckAreasForValue()) {
                int listSize = areas.size();
                for (int i = 0 ; i < listSize ; i++) { areas.get(i).checkForValue(currentValue); }
            }

            // Highlight Sections and/or Areas if enabled
            if (highlightSections | highlightAreas) {
                sectionsAndAreasCtx.clearRect(0, 0, height, height);
                drawAreasAndSections(sectionsAndAreasCtx);
            }
        } else if ("REDRAW".equals(EVENT_TYPE)) {
            redraw();
        } else if ("VISIBILITY".equals(EVENT_TYPE)) {
            Helper.enableNode(ledCanvas, getSkinnable().isLedVisible());
            Helper.enableNode(titleText, !getSkinnable().getTitle().isEmpty());
            Helper.enableNode(unitText, !getSkinnable().getUnit().isEmpty());
            Helper.enableNode(valueText, getSkinnable().isValueVisible());
            Helper.enableNode(knobCanvas, getSkinnable().isKnobVisible());
            Helper.enableNode(threshold, getSkinnable().isThresholdVisible());
            Helper.enableNode(sectionsAndAreasCanvas, areasVisible | sectionsVisible);
            boolean markersVisible = getSkinnable().getMarkersVisible();
            for (Shape shape : markerMap.values()) { Helper.enableNode(shape, markersVisible); }
            redraw();
        } else if ("LED".equals(EVENT_TYPE)) {
            if (getSkinnable().isLedVisible()) { drawLed(); }
        } else if ("RECALC".equals(EVENT_TYPE)) {
            if (getSkinnable().isAutoScale()) getSkinnable().calcAutoScale();
            angleRange = Helper.clamp(90d, 180d, getSkinnable().getAngleRange());
            startAngle = getStartAngle();
            minValue   = getSkinnable().getMinValue();
            maxValue   = getSkinnable().getMaxValue();
            angleStep  = angleRange / getSkinnable().getRange();
            needleRotate.setAngle((180 - startAngle) + (getSkinnable().getValue() - minValue) * angleStep);
            if (getSkinnable().getValue() < minValue) {
                getSkinnable().setValue(minValue);
                oldValue = minValue;
            }
            if (getSkinnable().getValue() > maxValue) {
                getSkinnable().setValue(maxValue);
                oldValue = maxValue;
            }
            resize();
            redraw();
        } else if ("SECTION".equals(EVENT_TYPE)) {
            sections          = getSkinnable().getSections();
            highlightSections = getSkinnable().isHighlightSections();
            sectionsVisible   = getSkinnable().getSectionsVisible();
            areas             = getSkinnable().getAreas();
            highlightAreas    = getSkinnable().isHighlightAreas();
            areasVisible      = getSkinnable().getAreasVisible();
            resize();
            redraw();
        } else if ("INTERACTIVITY".equals(EVENT_TYPE)) {
            if (getSkinnable().isInteractive()) {
                knobCanvas.setOnMousePressed(mouseHandler);
                knobCanvas.setOnMouseReleased(mouseHandler);
                if (!getSkinnable().getButtonTooltipText().isEmpty()) {
                    buttonTooltip.setText(getSkinnable().getButtonTooltipText());
                    Tooltip.install(knobCanvas, buttonTooltip);
                }
            } else {
                knobCanvas.removeEventHandler(MouseEvent.MOUSE_PRESSED, mouseHandler);
                knobCanvas.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseHandler);
                Tooltip.uninstall(knobCanvas, buttonTooltip);
            }
        }
    }

    public void handleMouseEvent(final MouseEvent EVENT) {
        if (getSkinnable().isDisabled()) return;
        final EventType TYPE = EVENT.getEventType();
        if (MouseEvent.MOUSE_PRESSED == TYPE) {
            getSkinnable().fireEvent(getSkinnable().BUTTON_PRESSED_EVENT);
            drawKnob(true);
        } else if (MouseEvent.MOUSE_RELEASED == TYPE) {
            getSkinnable().fireEvent(getSkinnable().BUTTON_RELEASED_EVENT);
            drawKnob(false);
        }
    }


    // ******************** Private Methods ***********************************
    private double getStartAngle() {
        ScaleDirection scaleDirection = getSkinnable().getScaleDirection();
        Pos            knobPosition   = getSkinnable().getKnobPosition();
        switch(knobPosition) {
            case CENTER_LEFT : return ScaleDirection.CLOCKWISE == scaleDirection ? angleRange * 0.5 + 90 : 90 - angleRange * 0.5;
            case CENTER_RIGHT:
            default          : return ScaleDirection.CLOCKWISE == scaleDirection ? angleRange * 0.5 - 90 : 270 -  angleRange * 0.5;
        }
    }

    private void rotateNeedle(final double VALUE) {
        double startOffsetAngle = 180 - startAngle;
        double targetAngle;
        if (ScaleDirection.CLOCKWISE == getSkinnable().getScaleDirection()) {
            targetAngle = startOffsetAngle + (VALUE - minValue) * angleStep;
            targetAngle = Helper.clamp(startOffsetAngle, startOffsetAngle + angleRange, targetAngle);
        } else {
            targetAngle = startOffsetAngle - (VALUE - minValue) * angleStep;
            targetAngle = Helper.clamp(startOffsetAngle - angleRange, startOffsetAngle, targetAngle);
        }
        needleRotate.setAngle(targetAngle);
        valueText.setText(String.format(Locale.US, formatString, VALUE));
        valueText.setTranslateX(Pos.CENTER_LEFT == getSkinnable().getKnobPosition() ? width * 0.6 - valueText.getLayoutBounds().getWidth() : width * 0.9 - valueText.getLayoutBounds().getWidth());
    }

    private void drawGradientBar() {
        TickLabelLocation  tickLabelLocation     = getSkinnable().getTickLabelLocation();
        double             scaledHeight          = height * 0.9;
        double             xy                    = TickLabelLocation.OUTSIDE == tickLabelLocation ? 0.1705 * scaledHeight : 0.107 * scaledHeight;
        double             wh                    = TickLabelLocation.OUTSIDE == tickLabelLocation ? scaledHeight * 0.77 : scaledHeight * 0.897;
        double             offset                = 90 - startAngle;
        double             offsetX               = -0.1 * width;
        double             knobPositionOffsetCW  = Pos.CENTER_LEFT == getSkinnable().getKnobPosition() ? 90 : 270;
        double             knobPositionOffsetCCW = Pos.CENTER_LEFT == getSkinnable().getKnobPosition() ? 180 : 0;
        ScaleDirection     scaleDirection        = getSkinnable().getScaleDirection();
        List<Stop>         stops                 = getSkinnable().getGradientBarStops();
        Map<Double, Color> stopAngleMap          = new HashMap<>(stops.size());
        for (Stop stop : stops) { stopAngleMap.put(stop.getOffset() * angleRange, stop.getColor()); }
        double               offsetFactor = ScaleDirection.CLOCKWISE == scaleDirection ? knobPositionOffsetCW - angleRange * 0.5 : angleRange - (angleRange / 180 * angleRange) + knobPositionOffsetCCW;
        AngleConicalGradient gradient     = new AngleConicalGradient(width * 0.5, width * 0.5, offsetFactor, stopAngleMap, getSkinnable().getScaleDirection());

        double barStartAngle  = ScaleDirection.CLOCKWISE == scaleDirection ? -minValue * angleStep : minValue * angleStep;
        double barAngleExtend = ScaleDirection.CLOCKWISE == scaleDirection ? getSkinnable().getRange() * angleStep : -getSkinnable().getRange() * angleStep;
        tickMarkCtx.save();
        tickMarkCtx.setStroke(gradient.getImagePattern(new Rectangle(xy - 0.026 * height + offsetX, xy - 0.026 * height, wh + 0.052 * height, wh + 0.052 * height)));
        tickMarkCtx.setLineWidth(scaledHeight * 0.052);
        tickMarkCtx.setLineCap(StrokeLineCap.BUTT);
        tickMarkCtx.strokeArc(xy + offsetX, xy, wh, wh, -(offset + barStartAngle), -barAngleExtend, ArcType.OPEN);
        tickMarkCtx.restore();
    }

    private void drawAreasAndSections(final GraphicsContext CTX) {
        if (areas.isEmpty() && sections.isEmpty()) return;

        double value        = getSkinnable().getCurrentValue();
        double scaledHeight = height * 0.9;
        double offset       = 90 - startAngle;
        double offsetX      = -0.1 * width;
        double offsetY      = 0.1 * width;
        double xy;
        double wh;
        int    listSize;

        // Draw Areas
        if (areasVisible && !areas.isEmpty()) {
            xy       = TickLabelLocation.OUTSIDE == tickLabelLocation ? 0.0895 * scaledHeight : 0.025 * scaledHeight;
            wh       = TickLabelLocation.OUTSIDE == tickLabelLocation ? scaledHeight * 0.821 : scaledHeight * 0.95;
            listSize = areas.size();
            for (int i = 0 ; i < listSize ; i++) {
                Section area = areas.get(i);
                double areaStartAngle;
                if (Double.compare(area.getStart(), maxValue) <= 0 && Double.compare(area.getStop(), minValue) >= 0) {
                    if (area.getStart() < minValue && area.getStop() < maxValue) {
                        areaStartAngle = 0;
                    } else {
                        areaStartAngle = ScaleDirection.CLOCKWISE == scaleDirection ? (area.getStart() - minValue) * angleStep : -(area.getStart() - minValue) * angleStep;
                    }
                    double areaAngleExtend;
                    if (area.getStop() > maxValue) {
                        areaAngleExtend = ScaleDirection.CLOCKWISE == scaleDirection ? (maxValue - area.getStart()) * angleStep : -(maxValue - area.getStart()) * angleStep;
                    } else {
                        areaAngleExtend = ScaleDirection.CLOCKWISE == scaleDirection ? (area.getStop() - area.getStart()) * angleStep : -(area.getStop() - area.getStart()) * angleStep;
                    }
                    CTX.save();
                    if (highlightAreas) {
                        CTX.setFill(area.contains(value) ? area.getHighlightColor() : area.getColor());
                    } else {
                        CTX.setFill(area.getColor());
                    }
                    CTX.fillArc(xy, xy + offsetY, wh, wh, -(offset + areaStartAngle), - areaAngleExtend, ArcType.ROUND);
                    CTX.restore();
                }
            }
        }

        // Draw Sections
        if (sectionsVisible && !sections.isEmpty()) {
            TickLabelLocation tickLabelLocation = getSkinnable().getTickLabelLocation();
            xy       = TickLabelLocation.OUTSIDE == tickLabelLocation ? 0.1705 * scaledHeight : 0.107 * scaledHeight;
            wh       = TickLabelLocation.OUTSIDE == tickLabelLocation ? scaledHeight * 0.77 : scaledHeight * 0.897;
            listSize = sections.size();
            CTX.setLineWidth(scaledHeight * 0.052);
            CTX.setLineCap(StrokeLineCap.BUTT);
            for (int i = 0; i < listSize; i++) {
                Section section = sections.get(i);
                double  sectionStartAngle;
                if (Double.compare(section.getStart(), maxValue) <= 0 && Double.compare(section.getStop(), minValue) >= 0) {
                    if (Double.compare(section.getStart(), minValue) < 0 && Double.compare(section.getStop(), maxValue) < 0) {
                        sectionStartAngle = 0;
                    } else {
                        sectionStartAngle = ScaleDirection.CLOCKWISE == scaleDirection ? (section.getStart() - minValue) * angleStep : -(section.getStart() - minValue) * angleStep;
                    }
                    double sectionAngleExtend;
                    if (Double.compare(section.getStop(), maxValue) > 0) {
                        sectionAngleExtend =
                            ScaleDirection.CLOCKWISE == scaleDirection ? (maxValue - section.getStart()) * angleStep : -(maxValue - section.getStart()) * angleStep;
                    } else {
                        sectionAngleExtend = ScaleDirection.CLOCKWISE == scaleDirection ?
                                             (section.getStop() - section.getStart()) * angleStep : -(section.getStop() - section.getStart()) * angleStep;
                    }
                    CTX.save();
                    if (highlightSections) {
                        CTX.setStroke(section.contains(value) ? section.getHighlightColor() : section.getColor());
                    } else {
                        CTX.setStroke(section.getColor());
                    }
                    CTX.strokeArc(xy + offsetX, xy, wh, wh, -(offset + sectionStartAngle), -sectionAngleExtend, ArcType.OPEN);
                    CTX.restore();
                }
            }
        }
    }

    private void drawLed() {
        ledCtx.clearRect(0, 0, ledSize, ledSize);

        boolean isFlatLed = LedType.FLAT == getSkinnable().getLedType();

        if (!isFlatLed) {
            ledCtx.setFill(ledFramePaint);
            ledCtx.fillOval(0, 0, ledSize, ledSize);
        } else {
            double lineWidth = 0.0037037 * width;
            ledCtx.setStroke(ledFramePaint);
            ledCtx.setLineWidth(lineWidth);
            ledCtx.strokeOval(lineWidth, lineWidth, ledSize - 2 * lineWidth, ledSize - 2 * lineWidth);
        }

        ledCtx.save();
        if (getSkinnable().isLedOn()) {
            ledCtx.setEffect(ledOnShadow);
            ledCtx.setFill(ledOnPaint);
        } else {
            ledCtx.setEffect(ledOffShadow);
            ledCtx.setFill(ledOffPaint);
        }
        if (isFlatLed) {
            ledCtx.fillOval(0.2 * ledSize, 0.2 * ledSize, 0.6 * ledSize, 0.6 * ledSize);
        } else {
            ledCtx.fillOval(0.14 * ledSize, 0.14 * ledSize, 0.72 * ledSize, 0.72 * ledSize);
        }
        ledCtx.restore();

        ledCtx.setFill(ledHighlightPaint);
        ledCtx.fillOval(0.21 * ledSize, 0.21 * ledSize, 0.58 * ledSize, 0.58 * ledSize);
    }

    private void drawMarkers() {
        markerPane.getChildren().setAll(markerMap.values());
        markerPane.getChildren().add(threshold);
        TickLabelLocation tickLabelLocation = getSkinnable().getTickLabelLocation();
        double            markerSize        = TickLabelLocation.OUTSIDE == tickLabelLocation ? 0.0125 * height : 0.015 * height;
        double            pathHalf          = markerSize * 0.3;
        double            scaledHeight      = height * 0.9;
        double            centerX           = Pos.CENTER_LEFT == getSkinnable().getKnobPosition() ? width * 0.1 : width * 0.9;
        double            centerY           = height * 0.5;
        ScaleDirection    scaleDirection    = getSkinnable().getScaleDirection();
        if (getSkinnable().getMarkersVisible()) {
            for (Map.Entry<Marker, Shape> entry : markerMap.entrySet()) {
                Marker marker = entry.getKey();
                Shape  shape  = entry.getValue();
                double valueAngle;
                if (ScaleDirection.CLOCKWISE == scaleDirection) {
                    valueAngle = startAngle - (marker.getValue() - minValue) * angleStep;
                } else {
                    valueAngle = startAngle + (marker.getValue() - minValue) * angleStep;
                }
                double sinValue = Math.sin(Math.toRadians(valueAngle));
                double cosValue = Math.cos(Math.toRadians(valueAngle));
                switch (marker.getMarkerType()) {
                    case TRIANGLE:
                        Path triangle = (Path) shape;
                        triangle.getElements().clear();
                        switch (tickLabelLocation) {
                            case OUTSIDE:
                                triangle.getElements().add(new MoveTo(centerX + scaledHeight * 0.38 * sinValue, centerY + scaledHeight * 0.38 * cosValue));
                                sinValue = Math.sin(Math.toRadians(valueAngle - pathHalf));
                                cosValue = Math.cos(Math.toRadians(valueAngle - pathHalf));
                                triangle.getElements().add(new LineTo(centerX + scaledHeight * 0.4075 * sinValue, centerY + scaledHeight * 0.4075 * cosValue));
                                sinValue = Math.sin(Math.toRadians(valueAngle + pathHalf));
                                cosValue = Math.cos(Math.toRadians(valueAngle + pathHalf));
                                triangle.getElements().add(new LineTo(centerX + scaledHeight * 0.4075 * sinValue, centerY + scaledHeight * 0.4075 * cosValue));
                                triangle.getElements().add(new ClosePath());
                                break;
                            case INSIDE:
                            default:
                                triangle.getElements().add(new MoveTo(centerX + scaledHeight * 0.465 * sinValue, centerY + scaledHeight * 0.465 * cosValue));
                                sinValue = Math.sin(Math.toRadians(valueAngle - pathHalf));
                                cosValue = Math.cos(Math.toRadians(valueAngle - pathHalf));
                                triangle.getElements().add(new LineTo(centerX + scaledHeight * 0.436 * sinValue, centerY + scaledHeight * 0.436 * cosValue));
                                sinValue = Math.sin(Math.toRadians(valueAngle + pathHalf));
                                cosValue = Math.cos(Math.toRadians(valueAngle + pathHalf));
                                triangle.getElements().add(new LineTo(centerX + scaledHeight * 0.436 * sinValue, centerY + scaledHeight * 0.436 * cosValue));
                                triangle.getElements().add(new ClosePath());
                                break;
                        }
                        break;
                    case DOT:
                        Circle dot = (Circle) shape;
                        dot.setRadius(markerSize);
                        switch (tickLabelLocation) {
                            case OUTSIDE:
                                dot.setCenterX(centerX + scaledHeight * 0.3945 * sinValue);
                                dot.setCenterY(centerY + scaledHeight * 0.3945 * cosValue);
                                break;
                            default:
                                dot.setCenterX(centerX + scaledHeight * 0.449 * sinValue);
                                dot.setCenterY(centerY + scaledHeight * 0.449 * cosValue);
                                break;
                        }
                        break;
                    case STANDARD:
                    default:
                        Path standard = (Path) shape;
                        standard.getElements().clear();
                        switch (tickLabelLocation) {
                            case OUTSIDE:
                                standard.getElements().add(new MoveTo(centerX + scaledHeight * 0.38 * sinValue, centerY + scaledHeight * 0.38 * cosValue));
                                sinValue = Math.sin(Math.toRadians(valueAngle - pathHalf));
                                cosValue = Math.cos(Math.toRadians(valueAngle - pathHalf));
                                standard.getElements().add(new LineTo(centerX + scaledHeight * 0.4075 * sinValue, centerY + scaledHeight * 0.4075 * cosValue));
                                standard.getElements().add(new LineTo(centerX + scaledHeight * 0.4575 * sinValue, centerY + scaledHeight * 0.4575 * cosValue));
                                sinValue = Math.sin(Math.toRadians(valueAngle + pathHalf));
                                cosValue = Math.cos(Math.toRadians(valueAngle + pathHalf));
                                standard.getElements().add(new LineTo(centerX + scaledHeight * 0.4575 * sinValue, centerY + scaledHeight * 0.4575 * cosValue));
                                standard.getElements().add(new LineTo(centerX + scaledHeight * 0.4075 * sinValue, centerY + scaledHeight * 0.4075 * cosValue));
                                standard.getElements().add(new ClosePath());
                                break;
                            case INSIDE:
                            default:
                                standard.getElements().add(new MoveTo(centerX + scaledHeight * 0.465 * sinValue, centerY + scaledHeight * 0.465 * cosValue));
                                sinValue = Math.sin(Math.toRadians(valueAngle - pathHalf));
                                cosValue = Math.cos(Math.toRadians(valueAngle - pathHalf));
                                standard.getElements().add(new LineTo(centerX + scaledHeight * 0.436 * sinValue, centerY + scaledHeight * 0.436 * cosValue));
                                standard.getElements().add(new LineTo(centerX + scaledHeight * 0.386 * sinValue, centerY + scaledHeight * 0.386 * cosValue));
                                sinValue = Math.sin(Math.toRadians(valueAngle + pathHalf));
                                cosValue = Math.cos(Math.toRadians(valueAngle + pathHalf));
                                standard.getElements().add(new LineTo(centerX + scaledHeight * 0.386 * sinValue, centerY + scaledHeight * 0.386 * cosValue));
                                standard.getElements().add(new LineTo(centerX + scaledHeight * 0.436 * sinValue, centerY + scaledHeight * 0.436 * cosValue));
                                standard.getElements().add(new ClosePath());
                                break;
                        }
                        break;
                }
                Color markerColor = marker.getColor();
                shape.setFill(markerColor);
                shape.setStroke(markerColor.darker());
                shape.setPickOnBounds(false);
                Tooltip markerTooltip;
                if (marker.getText().isEmpty()) {
                    markerTooltip = new Tooltip(Double.toString(marker.getValue()));
                } else {
                    markerTooltip = new Tooltip(new StringBuilder(marker.getText()).append("\n(").append(Double.toString(marker.getValue())).append(")").toString());
                }
                markerTooltip.setTextAlignment(TextAlignment.CENTER);
                Tooltip.install(shape, markerTooltip);
                shape.setOnMousePressed(e -> marker.fireMarkerEvent(marker.MARKER_PRESSED_EVENT));
                shape.setOnMouseReleased(e -> marker.fireMarkerEvent(marker.MARKER_RELEASED_EVENT));
            }
        }

        if (getSkinnable().isThresholdVisible()) {
            // Draw threshold
            threshold.getElements().clear();
            double thresholdAngle;
            if (ScaleDirection.CLOCKWISE == scaleDirection) {
                thresholdAngle = startAngle - (getSkinnable().getThreshold() - minValue) * angleStep;
            } else {
                thresholdAngle = startAngle + (getSkinnable().getThreshold() - minValue) * angleStep;
            }
            double thresholdSize = Helper.clamp(3d, 3.5, 0.01 * scaledHeight);
            double sinValue      = Math.sin(Math.toRadians(thresholdAngle));
            double cosValue      = Math.cos(Math.toRadians(thresholdAngle));
            switch (tickLabelLocation) {
                case OUTSIDE:
                    threshold.getElements().add(new MoveTo(centerX + scaledHeight * 0.38 * sinValue, centerY + scaledHeight * 0.38 * cosValue));
                    sinValue = Math.sin(Math.toRadians(thresholdAngle - thresholdSize));
                    cosValue = Math.cos(Math.toRadians(thresholdAngle - thresholdSize));
                    threshold.getElements().add(new LineTo(centerX + scaledHeight * 0.34 * sinValue, centerY + scaledHeight * 0.34 * cosValue));
                    sinValue = Math.sin(Math.toRadians(thresholdAngle + thresholdSize));
                    cosValue = Math.cos(Math.toRadians(thresholdAngle + thresholdSize));
                    threshold.getElements().add(new LineTo(centerX + scaledHeight * 0.34 * sinValue, centerY + scaledHeight * 0.34 * cosValue));
                    threshold.getElements().add(new ClosePath());
                    break;
                case INSIDE:
                default:
                    threshold.getElements().add(new MoveTo(centerX + scaledHeight * 0.465 * sinValue, centerY + scaledHeight * 0.465 * cosValue));
                    sinValue = Math.sin(Math.toRadians(thresholdAngle - thresholdSize));
                    cosValue = Math.cos(Math.toRadians(thresholdAngle - thresholdSize));
                    threshold.getElements().add(new LineTo(centerX + scaledHeight * 0.425 * sinValue, centerY + scaledHeight * 0.425 * cosValue));
                    sinValue = Math.sin(Math.toRadians(thresholdAngle + thresholdSize));
                    cosValue = Math.cos(Math.toRadians(thresholdAngle + thresholdSize));
                    threshold.getElements().add(new LineTo(centerX + scaledHeight * 0.425 * sinValue, centerY + scaledHeight * 0.425 * cosValue));
                    threshold.getElements().add(new ClosePath());
                    break;
            }
            threshold.setFill(getSkinnable().getThresholdColor());
            threshold.setStroke(getSkinnable().getTickMarkColor());
        }
    }

    private void updateMarkers() {
        markerMap.clear();
        for (Marker marker : getSkinnable().getMarkers()) {
            switch(marker.getMarkerType()) {
                case TRIANGLE: markerMap.put(marker, new Path()); break;
                case DOT     : markerMap.put(marker, new Circle()); break;
                case STANDARD:
                default:       markerMap.put(marker, new Path()); break;
            }
        }
    }

    private void drawKnob(final boolean PRESSED) {
        knobCanvas.setCache(false);
        double w = knobCanvas.getWidth();
        double h = knobCanvas.getHeight();
        knobCtx.clearRect(0, 0, w, h);

        Color  knobColor = getSkinnable().getKnobColor();
        double hue       = knobColor.getHue();
        double sat       = knobColor.getSaturation();
        double alp       = knobColor.getOpacity();
        double brg       = Color.BLACK.equals(knobColor) ? 0.2 : knobColor.getBrightness();
        double gradTop;
        double gradBot;

        switch (getSkinnable().getKnobType()) {
            case PLAIN:
                knobCtx.setFill(new LinearGradient(0, 0, 0, h, false, CycleMethod.NO_CYCLE,
                                                   new Stop(0.0, Color.rgb(180,180,180)),
                                                   new Stop(0.46, Color.rgb(63,63,63)),
                                                   new Stop(1.0, Color.rgb(40,40,40))));
                knobCtx.fillOval(0, 0, w, h);

                knobCtx.setFill(new LinearGradient(0, 0.11764706 * h, 0, 0.76470588 * h, false, CycleMethod.NO_CYCLE,
                                                   new Stop(0.0, Color.hsb(hue, sat, PRESSED ? brg * 0.9 : brg * 1.0, alp)),
                                                   new Stop(0.01, Color.hsb(hue, sat, PRESSED ? brg * 0.75 : brg * 0.85, alp)),
                                                   new Stop(0.5, Color.hsb(hue, sat, PRESSED ? brg * 0.4 : brg * 0.5, alp)),
                                                   new Stop(0.51, Color.hsb(hue, sat, PRESSED ? brg * 0.35 :brg *  0.45, alp)),
                                                   new Stop(1.0, Color.hsb(hue, sat, PRESSED ? brg * 0.7 : brg * 0.8, alp))
                                            ));
                knobCtx.fillOval(w * 0.11764706, h * 0.11764706, w - w * 0.23529412, h - h * 0.23529412);

                knobCtx.setFill(new RadialGradient(0, 0, 0.5 * w, 0.47 * h, w * 0.38, false, CycleMethod.NO_CYCLE,
                                                   new Stop(0, Color.TRANSPARENT),
                                                   new Stop(0.76, Color.TRANSPARENT),
                                                   new Stop(1.0, Color.rgb(0, 0, 0, PRESSED ? 0.5 : 0.2))));
                knobCtx.fillOval(w * 0.11764706, h * 0.11764706, w - w * 0.23529412, h - h * 0.23529412);
                break;
            case METAL:
                knobCtx.setFill(new LinearGradient(0, 0, 0, h,
                                                   false, CycleMethod.NO_CYCLE,
                                                   new Stop(0.0, Color.rgb(92,95,101)),
                                                   new Stop(0.47, Color.rgb(46,49,53)),
                                                   new Stop(1.0, Color.rgb(22,23,26))));
                knobCtx.fillOval(0, 0, w, h);

                knobCtx.setFill(new LinearGradient(0, 0.058823529411764705 * h, 0, 0.9411764705882353 * h,
                                                   false, CycleMethod.NO_CYCLE,
                                                   new Stop(0.0, Color.hsb(hue, sat, PRESSED ? brg * 0.7 : brg * 0.9, alp)),
                                                   new Stop(0.0, Color.hsb(hue, sat, PRESSED ? brg * 0.3 : brg * 0.5, alp))));
                knobCtx.fillOval(0.05882353 * w, 0.05882353 * h, w * 0.88235294, h * 0.88235294);

                knobCtx.beginPath();
                knobCtx.moveTo(0.17647058823529413 * w, 0.8235294117647058 * h);
                knobCtx.bezierCurveTo(0.29411764705882354 * w, 0.8823529411764706 * h, 0.35294117647058826 * w, 0.9411764705882353 * h, 0.5294117647058824 * w, 0.9411764705882353 * h);
                knobCtx.bezierCurveTo(0.6470588235294118 * w, 0.9411764705882353 * h, 0.7058823529411765 * w, 0.8823529411764706 * h, 0.8235294117647058 * w, 0.8235294117647058 * h);
                knobCtx.bezierCurveTo(0.7647058823529411 * w, 0.7058823529411765 * h, 0.6470588235294118 * w, 0.5882352941176471 * h, 0.5294117647058824 * w, 0.5882352941176471 * h);
                knobCtx.bezierCurveTo(0.35294117647058826 * w, 0.5882352941176471 * h, 0.23529411764705882 * w, 0.7058823529411765 * h, 0.17647058823529413 * w, 0.8235294117647058 * h);
                knobCtx.closePath();
                knobCtx.setFill(new RadialGradient(0, 0,
                                                0.47058823529411764 * w, 0.8823529411764706 * h,
                                                0.3235294117647059 * w,
                                                   false, CycleMethod.NO_CYCLE,
                                                   new Stop(0.0, Color.rgb(255, 255, 255, PRESSED ? 0.3 : 0.6)),
                                                   new Stop(1.0, Color.TRANSPARENT)));
                knobCtx.fill();

                knobCtx.beginPath();
                knobCtx.moveTo(0.058823529411764705 * w, 0.29411764705882354 * h);
                knobCtx.bezierCurveTo(0.17647058823529413 * w, 0.35294117647058826 * h, 0.35294117647058826 * w, 0.35294117647058826 * h, 0.5294117647058824 * w, 0.35294117647058826 * h);
                knobCtx.bezierCurveTo(0.6470588235294118 * w, 0.35294117647058826 * h, 0.8235294117647058 * w, 0.35294117647058826 * h, 0.9411764705882353 * w, 0.29411764705882354 * h);
                knobCtx.bezierCurveTo(0.8823529411764706 * w, 0.11764705882352941 * h, 0.7058823529411765 * w, 0.0 * h, 0.5294117647058824 * w, 0.0 * h);
                knobCtx.bezierCurveTo(0.29411764705882354 * w, 0.0 * h, 0.11764705882352941 * w, 0.11764705882352941 * h, 0.058823529411764705 * w, 0.29411764705882354 * h);
                knobCtx.closePath();
                knobCtx.setFill(new RadialGradient(0, 0,
                                                0.47058823529411764 * w, 0.0,
                                                0.4411764705882353 * w,
                                                   false, CycleMethod.NO_CYCLE,
                                                   new Stop(0.0, Color.rgb(255, 255, 255, PRESSED ? 0.45 : 0.75)),
                                                   new Stop(1.0, Color.TRANSPARENT)));
                knobCtx.fill();

                knobCtx.setFill(new LinearGradient(0.5294117647058824 * w, 0.23529411764705882 * h,
                                                0.5294117647058824 * w, 0.7647058823529411 * h,
                                                   false, CycleMethod.NO_CYCLE,
                                                   new Stop(0.0, Color.BLACK),
                                                   new Stop(1.0, Color.rgb(204, 204, 204))));
                knobCtx.fillOval(0.23529412 * w, 0.23529412 * h, 0.52941176 * w, 0.52941176 * h);

                knobCtx.setFill(new LinearGradient(0.5294117647058824 * w, 0.29411764705882354 * h,
                                                0.5294117647058824 * w, 0.7058823529411765 * h,
                                                   false, CycleMethod.NO_CYCLE,
                                                   new Stop(0.0, Color.rgb(1,6,11)),
                                                   new Stop(1.0, Color.rgb(50,52,56))));
                knobCtx.fillOval(0.29411765 * w, 0.29411765 * h, 0.41176471 * w, 0.41176471 * h);
                break;
            case FLAT:
                double lineWidth = 0.00740741 * width;
                double knobSize  = w - 2 * lineWidth;
                knobCtx.setFill(PRESSED ? knobColor.darker() : knobColor);
                knobCtx.setStroke(PRESSED ? Color.WHITE.darker() : Color.WHITE);
                knobCtx.setLineWidth(lineWidth);
                knobCtx.fillOval(lineWidth, lineWidth, knobSize, knobSize);
                knobCtx.strokeOval(lineWidth, lineWidth, knobSize, knobSize);
                break;
            case STANDARD:
            default:
                knobCtx.setFill(new LinearGradient(0, 0, 0, h,
                                                   false, CycleMethod.NO_CYCLE,
                                                   new Stop(0.0, Color.rgb(133, 133, 133).brighter().brighter()),
                                                   new Stop(0.52, Color.rgb(133, 133, 133)),
                                                   new Stop(1.0, Color.rgb(133, 133, 133).darker().darker())));
                knobCtx.fillOval(0, 0, w, h);
                gradTop = PRESSED ? h - width * 0.01 : width * 0.005;
                gradBot = PRESSED ? width * 0.005 : h - width * 0.01;
                knobCtx.setFill(new LinearGradient(0, gradTop, 0, gradBot,
                                                   false, CycleMethod.NO_CYCLE,
                                                   new Stop(0.0, Color.hsb(hue, sat, brg * 0.85, alp)),
                                                   new Stop(0.45, Color.hsb(hue, sat, brg * 0.65, alp)),
                                                   new Stop(1.0, Color.hsb(hue, sat, brg * 0.4, alp))));
                knobCtx.fillOval(width * 0.005, width * 0.005, w - width * 0.01, h - width * 0.01);
                break;
        }
        knobCanvas.setCache(true);
        knobCanvas.setCacheHint(CacheHint.QUALITY);
    }

    private void resizeText() {
        double scaledHeight = height * 0.9;
        Pos    knobPosition = getSkinnable().getKnobPosition();
        double maxWidth     = 0.4 * width;
        double fontSize     = 0.06 * scaledHeight;

        titleText.setFont(Fonts.robotoMedium(fontSize));
        titleText.setText(getSkinnable().getTitle());
        if (titleText.getLayoutBounds().getWidth() > maxWidth) { Helper.adjustTextSize(titleText, maxWidth, fontSize); }
        titleText.relocate(Pos.CENTER_LEFT == knobPosition ? width * 0.6 - titleText.getLayoutBounds().getWidth() : width * 0.4, (height - titleText.getLayoutBounds().getHeight()) * 0.5);

        fontSize = 0.04 * scaledHeight;
        unitText.setFont(Fonts.robotoRegular(fontSize));
        unitText.setText(getSkinnable().getUnit());
        if (unitText.getLayoutBounds().getWidth() > maxWidth) { Helper.adjustTextSize(unitText, maxWidth, fontSize); }
        unitText.relocate(Pos.CENTER_LEFT == knobPosition ? width * 0.6 - unitText.getLayoutBounds().getWidth() : width * 0.4, (height - unitText.getLayoutBounds().getHeight()) * 0.38);
    }

    private void resize() {
        width  = getSkinnable().getWidth() - getSkinnable().getInsets().getLeft() - getSkinnable().getInsets().getRight();
        height = getSkinnable().getHeight() - getSkinnable().getInsets().getTop() - getSkinnable().getInsets().getBottom();

        if (ASPECT_RATIO * width > height) {
            width = 1 / (ASPECT_RATIO / height);
        } else if (1 / (ASPECT_RATIO / height) > width) {
            height = ASPECT_RATIO * width;
        }

        if (width > 0 && height > 0) {
            Pos    knobPosition = getSkinnable().getKnobPosition();
            double centerX      = Pos.CENTER_LEFT == knobPosition ? width * 0.1 : width * 0.9;
            double centerY      = height * 0.5;
            double scaledHeight = height * 0.9;

            pane.setMaxSize(width, height);
            pane.relocate((getSkinnable().getWidth() - width) * 0.5, (getSkinnable().getHeight() - height) * 0.5);

            dropShadow.setRadius(0.008 * scaledHeight);
            dropShadow.setOffsetY(0.008 * scaledHeight);

            backgroundInnerShadow.setOffsetX(0);
            backgroundInnerShadow.setOffsetY(scaledHeight * 0.03);
            backgroundInnerShadow.setRadius(scaledHeight * 0.04);

            pane.setEffect(getSkinnable().isInnerShadowEnabled() ? backgroundInnerShadow : null);

            sectionsAndAreasCanvas.setWidth(height);
            sectionsAndAreasCanvas.setHeight(height);
            sectionsAndAreasCanvas.relocate(Pos.CENTER_LEFT == getSkinnable().getKnobPosition() ? -width * 0.8 : 0, 0);

            tickMarkCanvas.setWidth(height);
            tickMarkCanvas.setHeight(height);
            tickMarkCanvas.relocate(Pos.CENTER_LEFT == getSkinnable().getKnobPosition() ? -width * 0.8 : 0, 0);

            markerPane.setPrefSize(height, height);

            boolean isFlatLed = LedType.FLAT == getSkinnable().getLedType();
            ledSize = isFlatLed ? 0.05 * scaledHeight : 0.06 * scaledHeight;
            ledCanvas.setWidth(ledSize);
            ledCanvas.setHeight(ledSize);
            ledCanvas.relocate(Pos.CENTER_LEFT == knobPosition ? 0.025 * height : 0.425 * height, 0.35 * height);
            ledOffShadow = isFlatLed ? null : new InnerShadow(BlurType.TWO_PASS_BOX, Color.rgb(0, 0, 0, 0.65), 0.07 * ledSize, 0, 0, 0);
            ledOnShadow  = isFlatLed ? null : new InnerShadow(BlurType.TWO_PASS_BOX, Color.rgb(0, 0, 0, 0.65), 0.07 * ledSize, 0, 0, 0);
            if (!isFlatLed) ledOnShadow.setInput(new DropShadow(BlurType.TWO_PASS_BOX, getSkinnable().getLedColor(), 0.36 * ledSize, 0, 0, 0));

            resizeText();

            valueText.setFont(Fonts.robotoMedium(scaledHeight * 0.1));
            valueText.setTranslateX(Pos.CENTER_LEFT == getSkinnable().getKnobPosition() ? width * 0.6 - valueText.getLayoutBounds().getWidth() : width * 0.9 - valueText.getLayoutBounds().getWidth());
            valueText.setTranslateY(height * 0.6);

            double needleWidth;
            double needleHeight;
            needle.setCache(false);
            switch(getSkinnable().getNeedleType()) {
                case BIG:
                    needleWidth  = 0.06 * scaledHeight;
                    needleHeight = TickLabelLocation.INSIDE == tickLabelLocation ? 0.4975 * scaledHeight : 0.415 * scaledHeight;
                    Needle.INSTANCE.getPath(needle, needleWidth, needleHeight, NeedleType.BIG, tickLabelLocation);
                    needle.relocate(centerX - needle.getLayoutBounds().getWidth() * 0.5, centerY - needle.getLayoutBounds().getHeight() * (TickLabelLocation.INSIDE == tickLabelLocation ? 0.93969849 : 0.92771084));
                    needleRotate.setPivotX(needle.getLayoutBounds().getWidth() * 0.5);
                    needleRotate.setPivotY(needle.getLayoutBounds().getHeight() * (TickLabelLocation.INSIDE == tickLabelLocation ? 0.93969849 : 0.92771084));
                    break;
                case FAT:
                    needleWidth  = scaledHeight * 0.3;
                    needleHeight = scaledHeight * 0.505;
                    Needle.INSTANCE.getPath(needle, needleWidth, needleHeight, NeedleType.FAT, tickLabelLocation);
                    needle.relocate(centerX - needle.getLayoutBounds().getWidth() * 0.5, centerY - needle.getLayoutBounds().getHeight() * 0.7029703);
                    needleRotate.setPivotX(needle.getLayoutBounds().getWidth() * 0.5);
                    needleRotate.setPivotY(needle.getLayoutBounds().getHeight() * 0.7029703);
                    break;
                case SCIENTIFIC:
                    needleWidth  = 0.1 * scaledHeight;
                    needleHeight = TickLabelLocation.INSIDE == tickLabelLocation ? 0.645 * scaledHeight : 0.5625 * scaledHeight;
                    Needle.INSTANCE.getPath(needle, needleWidth, needleHeight, NeedleType.SCIENTIFIC, tickLabelLocation);
                    needle.relocate(centerX - needle.getLayoutBounds().getWidth() * 0.5, centerY - (TickLabelLocation.INSIDE == tickLabelLocation ? needle.getLayoutBounds().getHeight() * 0.7248062: needle.getLayoutBounds().getHeight() * 0.68444444));
                    needleRotate.setPivotX(needle.getLayoutBounds().getWidth() * 0.5);
                    needleRotate.setPivotY(needle.getLayoutBounds().getHeight() * (TickLabelLocation.INSIDE == tickLabelLocation ? 0.7248062 : 0.68444444));
                    break;
                case AVIONIC:
                    needleWidth  = 0.06 * scaledHeight;
                    needleHeight = TickLabelLocation.INSIDE == tickLabelLocation ? 0.5975 * scaledHeight : 0.515 * scaledHeight;
                    Needle.INSTANCE.getPath(needle, needleWidth, needleHeight, NeedleType.AVIONIC, tickLabelLocation);
                    needle.relocate(centerX - needle.getLayoutBounds().getWidth() * 0.5, centerY - needle.getLayoutBounds().getHeight() * (TickLabelLocation.INSIDE == tickLabelLocation ? 0.78242678 : 0.74757282));
                    needleRotate.setPivotX(needle.getLayoutBounds().getWidth() * 0.5);
                    needleRotate.setPivotY(needle.getLayoutBounds().getHeight() * (TickLabelLocation.INSIDE == tickLabelLocation ? 0.78242678 : 0.74757282));
                    break;
                case VARIOMETER:
                    needleWidth  = scaledHeight * getSkinnable().getNeedleSize().FACTOR;
                    needleHeight = TickLabelLocation.INSIDE == tickLabelLocation ? scaledHeight * 0.4675 : scaledHeight * 0.385;
                    Needle.INSTANCE.getPath(needle, needleWidth, needleHeight, NeedleType.VARIOMETER, tickLabelLocation);
                    needle.relocate(centerX - needle.getLayoutBounds().getWidth() * 0.5, centerY - needle.getLayoutBounds().getHeight());
                    needleRotate.setPivotX(needle.getLayoutBounds().getWidth() * 0.5);
                    needleRotate.setPivotY(needle.getLayoutBounds().getHeight());
                    break;
                case STANDARD:
                default      :
                    needleWidth  = scaledHeight * getSkinnable().getNeedleSize().FACTOR;
                    needleHeight = TickLabelLocation.OUTSIDE == tickLabelLocation ? scaledHeight * 0.3965 : scaledHeight * 0.455;
                    Needle.INSTANCE.getPath(needle, needleWidth, needleHeight, NeedleType.STANDARD, tickLabelLocation);
                    needle.relocate(centerX - needle.getLayoutBounds().getWidth() * 0.5, centerY - needle.getLayoutBounds().getHeight());
                    needleRotate.setPivotX(needle.getLayoutBounds().getWidth() * 0.5);
                    needleRotate.setPivotY(needle.getLayoutBounds().getHeight());
                    break;
            }
            needle.setCache(true);
            needle.setCacheHint(CacheHint.ROTATE);

            knobCanvas.setWidth(height * 0.1);
            knobCanvas.setHeight(height * 0.1);
            knobCanvas.relocate(centerX - height * 0.05, centerY - height * 0.05);

            buttonTooltip.setText(getSkinnable().getButtonTooltipText());
        }
    }

    private void redraw() {
        formatString = new StringBuilder("%.").append(Integer.toString(getSkinnable().getDecimals())).append("f").toString();
        shadowGroup.setEffect(getSkinnable().isShadowsEnabled() ? dropShadow : null);

        // Background stroke and fill
        pane.setBorder(new Border(new BorderStroke(getSkinnable().getBorderPaint(), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(getSkinnable().getBorderWidth() / PREFERRED_WIDTH * width))));
        pane.setBackground(new Background(new BackgroundFill(getSkinnable().getBackgroundPaint(), CornerRadii.EMPTY, Insets.EMPTY)));

        // Areas, Sections and Tick Marks
        tickLabelLocation = getSkinnable().getTickLabelLocation();
        scaleDirection    = getSkinnable().getScaleDirection();
        if (getSkinnable().getAreasVisible() | getSkinnable().getSectionsVisible()) {
            sectionsAndAreasCtx.clearRect(0, 0, height, height);
            drawAreasAndSections(sectionsAndAreasCtx);
        }
        tickMarkCanvas.setCache(false);
        tickMarkCtx.clearRect(0, 0, height, height);
        if (getSkinnable().isGradientBarEnabled() && getSkinnable().getGradientLookup() != null) {
            drawGradientBar();
        }
        Helper.drawRadialTickMarks(getSkinnable(), tickMarkCtx, minValue, maxValue, startAngle, angleRange, angleStep, width * 0.9, height * 0.5, height * 0.9);
        tickMarkCanvas.setCache(true);
        tickMarkCanvas.setCacheHint(CacheHint.QUALITY);

        // LED
        if (getSkinnable().isLedVisible()) {
            final Color LED_COLOR = getSkinnable().getLedColor();
            switch(getSkinnable().getLedType()) {
                case FLAT:
                    ledFramePaint = Color.WHITE;
                    ledOnPaint = new LinearGradient(0, 0.25 * ledSize,
                                                    0, 0.74 * ledSize,
                                                    false, CycleMethod.NO_CYCLE,
                                                    new Stop(0.0, LED_COLOR),
                                                    new Stop(1.0, LED_COLOR.deriveColor(0d, 1d, 0.5, 1d)));
                    ledOffPaint = new LinearGradient(0, 0.25 * ledSize,
                                                     0, 0.74 * ledSize,
                                                     false, CycleMethod.NO_CYCLE,
                                                     new Stop(0.0, LED_COLOR.deriveColor(0d, 1d, 0.5, 1d)),
                                                     new Stop(1.0, LED_COLOR.deriveColor(0d, 1d, 0.13, 1d)));
                    ledHighlightPaint = Color.TRANSPARENT;
                    break;
                case STANDARD:
                default:
                    ledFramePaint = new LinearGradient(0.14 * ledSize, 0.14 * ledSize,
                                                       0.84 * ledSize, 0.84 * ledSize,
                                                       false, CycleMethod.NO_CYCLE,
                                                       new Stop(0.0, Color.rgb(20, 20, 20, 0.65)),
                                                       new Stop(0.15, Color.rgb(20, 20, 20, 0.65)),
                                                       new Stop(0.26, Color.rgb(41, 41, 41, 0.65)),
                                                       new Stop(0.26, Color.rgb(41, 41, 41, 0.64)),
                                                       new Stop(0.85, Color.rgb(200, 200, 200, 0.41)),
                                                       new Stop(1.0, Color.rgb(200, 200, 200, 0.35)));
                    ledOnPaint = new LinearGradient(0.25 * ledSize, 0.25 * ledSize,
                                                    0.74 * ledSize, 0.74 * ledSize,
                                                    false, CycleMethod.NO_CYCLE,
                                                    new Stop(0.0, LED_COLOR.deriveColor(0d, 1d, 0.77, 1d)),
                                                    new Stop(0.49, LED_COLOR.deriveColor(0d, 1d, 0.5, 1d)),
                                                    new Stop(1.0, LED_COLOR));
                    ledOffPaint = new LinearGradient(0.25 * ledSize, 0.25 * ledSize,
                                                     0.74 * ledSize, 0.74 * ledSize,
                                                     false, CycleMethod.NO_CYCLE,
                                                     new Stop(0.0, LED_COLOR.deriveColor(0d, 1d, 0.20, 1d)),
                                                     new Stop(0.49, LED_COLOR.deriveColor(0d, 1d, 0.13, 1d)),
                                                     new Stop(1.0, LED_COLOR.deriveColor(0d, 1d, 0.2, 1d)));
                    ledHighlightPaint = new RadialGradient(0, 0,
                                                           0.3 * ledSize, 0.3 * ledSize,
                                                           0.29 * ledSize,
                                                           false, CycleMethod.NO_CYCLE,
                                                           new Stop(0.0, Color.WHITE),
                                                           new Stop(1.0, Color.TRANSPARENT));
                    break;
            }
            drawLed();
        }

        // Text
        titleText.setFill(getSkinnable().getTitleColor());
        unitText.setFill(getSkinnable().getUnitColor());
        valueText.setFill(getSkinnable().getValueColor());
        resizeText();

        // Needle
        Color needleColor = getSkinnable().getNeedleColor();
        switch(getSkinnable().getNeedleShape()) {
            case ROUND:
                needlePaint = new LinearGradient(needle.getLayoutBounds().getMinX(), 0,
                                                 needle.getLayoutBounds().getMaxX(), 0,
                                                 false, CycleMethod.NO_CYCLE,
                                                 new Stop(0.0, needleColor.darker()),
                                                 new Stop(0.5, needleColor.brighter().brighter()),
                                                 new Stop(1.0, needleColor.darker()));
                needle.setStrokeWidth(0);
                break;
            case FLAT:
                needlePaint = needleColor;
                needle.setStrokeWidth(0.0037037 * width);
                break;
            case ANGLED:
            default:
                needlePaint = new LinearGradient(needle.getLayoutBounds().getMinX(), 0,
                                                 needle.getLayoutBounds().getMaxX(), 0,
                                                 false, CycleMethod.NO_CYCLE,
                                                 new Stop(0.0, needleColor.darker()),
                                                 new Stop(0.5, needleColor.darker()),
                                                 new Stop(0.5, needleColor.brighter()),
                                                 new Stop(1.0, needleColor.brighter()));
                needle.setStrokeWidth(0);
                break;
        }
        if (NeedleType.AVIONIC == getSkinnable().getNeedleType()) {
            needlePaint = new LinearGradient(0, needle.getLayoutBounds().getMinY(),
                                             0, needle.getLayoutBounds().getMaxY(),
                                             false, CycleMethod.NO_CYCLE,
                                             new Stop(0.0, needleColor),
                                             new Stop(0.3, needleColor),
                                             new Stop(0.3, Color.BLACK),
                                             new Stop(1.0, Color.BLACK));
        }
        needle.setFill(needlePaint);
        needle.setStroke(getSkinnable().getNeedleBorderColor());

        // Knob
        drawKnob(false);

        // Markers
        drawMarkers();
        thresholdTooltip.setText("Threshold\n(" + String.format(Locale.US, formatString, getSkinnable().getThreshold()) + ")");
    }
}
