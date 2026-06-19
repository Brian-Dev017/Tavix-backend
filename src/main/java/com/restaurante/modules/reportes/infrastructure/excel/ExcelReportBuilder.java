package com.restaurante.modules.reportes.infrastructure.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBarChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTCatAx;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTPlotArea;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTValAx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class ExcelReportBuilder implements AutoCloseable {

    public enum ValueType {
        TEXT,
        INTEGER,
        MONEY,
        PERCENT,
        DATE,
        DATE_TIME
    }

    public record Column(String header, ValueType type, int width) {}

    public record ChartSpec(
            ChartTypes type,
            String title,
            String seriesTitle,
            int categoryColumn,
            int valueColumn
    ) {}

    private static final String MONEY_FORMAT = "\"S/\" #,##0.00";
    private static final String PERCENT_FORMAT = "0.0%";
    private static final String DATE_FORMAT = "dd/mm/yyyy";
    private static final String DATE_TIME_FORMAT = "dd/mm/yyyy hh:mm";
    private static final byte[] TITLE_COLOR = rgb("993556");
    private static final byte[] HEADER_COLOR = rgb("243B53");
    private static final byte[] ALT_ROW_COLOR = rgb("F4F7FA");
    private static final AtomicLong AXIS_IDS = new AtomicLong(100_000);

    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final Styles styles = new Styles(workbook);
    private long drawingId = 1;

    public static Column column(String header, ValueType type, int width) {
        return new Column(header, type, width);
    }

    public XSSFWorkbook workbook() {
        return workbook;
    }

    public void createSummarySheet(
            String businessName,
            String reportTitle,
            Map<String, Object> metadata,
            Map<String, Object> indicators
    ) {
        XSSFSheet sheet = workbook.createSheet("Resumen");
        writeReportHeader(sheet, businessName, reportTitle, Math.max(3, indicators.size()));
        writeMetadata(sheet, metadata, 2);

        int rowIndex = 4;
        Row header = sheet.createRow(rowIndex++);
        writeHeaderCell(header.createCell(0), "Indicador");
        writeHeaderCell(header.createCell(1), "Valor");

        for (Map.Entry<String, Object> indicator : indicators.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            writeCell(row.createCell(0), indicator.getKey(), ValueType.TEXT, false);
            ValueType type = indicator.getValue() instanceof BigDecimal
                    || indicator.getValue() instanceof Double
                    || indicator.getValue() instanceof Float
                    ? ValueType.MONEY
                    : indicator.getValue() instanceof Number
                    ? ValueType.INTEGER
                    : ValueType.TEXT;
            writeCell(row.createCell(1), indicator.getValue(), type, rowIndex % 2 == 0);
        }
        sheet.setColumnWidth(0, 34 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.createFreezePane(0, 5);
        sheet.setAutoFilter(new CellRangeAddress(4, Math.max(4, rowIndex - 1), 0, 1));
        configurePrint(sheet, 2);
    }

    public void createTableSheet(
            String sheetName,
            String reportTitle,
            Map<String, Object> metadata,
            List<Column> columns,
            List<Object[]> rows,
            ChartSpec chart
    ) {
        XSSFSheet sheet = workbook.createSheet(safeSheetName(sheetName));
        writeReportHeader(sheet, "", reportTitle, Math.max(1, columns.size()));
        writeMetadata(sheet, metadata, 2);

        int headerRowIndex = 4;
        Row headerRow = sheet.createRow(headerRowIndex);
        for (int column = 0; column < columns.size(); column++) {
            writeHeaderCell(headerRow.createCell(column), columns.get(column).header());
            sheet.setColumnWidth(column, Math.max(10, columns.get(column).width()) * 256);
        }

        int rowIndex = headerRowIndex + 1;
        for (Object[] values : rows) {
            Row row = sheet.createRow(rowIndex);
            boolean alternate = rowIndex % 2 == 0;
            for (int column = 0; column < columns.size(); column++) {
                Object value = column < values.length ? values[column] : null;
                writeCell(row.createCell(column), value, columns.get(column).type(), alternate);
            }
            rowIndex++;
        }

        if (rows.isEmpty()) {
            Row empty = sheet.createRow(rowIndex++);
            Cell cell = empty.createCell(0);
            cell.setCellValue("Sin datos");
            cell.setCellStyle(styles.empty);
            if (columns.size() > 1) {
                sheet.addMergedRegion(new CellRangeAddress(
                        empty.getRowNum(), empty.getRowNum(), 0, columns.size() - 1));
            }
        }

        sheet.createFreezePane(0, headerRowIndex + 1);
        sheet.setAutoFilter(new CellRangeAddress(
                headerRowIndex,
                Math.max(headerRowIndex, rowIndex - 1),
                0,
                columns.size() - 1
        ));
        configurePrint(sheet, columns.size());

        if (chart != null && !rows.isEmpty()) {
            addChart(
                    sheet,
                    chart,
                    headerRowIndex + 1,
                    rowIndex - 1,
                    columns.size() + 1
            );
        }
    }

    public byte[] toBytes() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo generar el libro Excel", error);
        }
    }

    @Override
    public void close() throws IOException {
        workbook.close();
    }

    private void writeReportHeader(
            XSSFSheet sheet,
            String businessName,
            String reportTitle,
            int columns
    ) {
        int lastColumn = Math.max(1, columns - 1);
        Row businessRow = sheet.createRow(0);
        Cell business = businessRow.createCell(0);
        String title = businessName == null || businessName.isBlank()
                ? reportTitle
                : businessName.toUpperCase(Locale.ROOT) + " · " + reportTitle;
        business.setCellValue(title);
        business.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));
        businessRow.setHeightInPoints(28);
    }

    private void writeMetadata(XSSFSheet sheet, Map<String, Object> metadata, int startRow) {
        if (metadata == null || metadata.isEmpty()) return;
        Row labels = sheet.createRow(startRow);
        Row values = sheet.createRow(startRow + 1);
        int column = 0;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            Cell label = labels.createCell(column);
            label.setCellValue(entry.getKey());
            label.setCellStyle(styles.metadataLabel);
            writeCell(values.createCell(column), entry.getValue(), ValueType.TEXT, false);
            sheet.setColumnWidth(column, Math.max(16, entry.getKey().length() + 5) * 256);
            column++;
        }
    }

    private void writeHeaderCell(Cell cell, String value) {
        cell.setCellValue(value);
        cell.setCellStyle(styles.header);
    }

    private void writeCell(Cell cell, Object value, ValueType type, boolean alternate) {
        CellStyle style = styles.dataStyle(type, alternate);
        cell.setCellStyle(style);
        if (value == null) {
            cell.setBlank();
            return;
        }
        switch (type) {
            case MONEY, PERCENT -> cell.setCellValue(number(value));
            case INTEGER -> cell.setCellValue(value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(value.toString()));
            case DATE -> {
                if (value instanceof LocalDate date) cell.setCellValue(date);
                else cell.setCellValue(value.toString());
            }
            case DATE_TIME -> {
                if (value instanceof LocalDateTime dateTime) cell.setCellValue(dateTime);
                else cell.setCellValue(value.toString());
            }
            default -> cell.setCellValue(value.toString());
        }
    }

    private void addChart(
            XSSFSheet sheet,
            ChartSpec spec,
            int firstRow,
            int lastRow,
            int anchorColumn
    ) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(
                0, 0, 0, 0,
                anchorColumn, 4,
                anchorColumn + 9, 21
        );
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(spec.title());
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.RIGHT);

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet,
                new CellRangeAddress(firstRow, lastRow, spec.categoryColumn(), spec.categoryColumn())
        );
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet,
                new CellRangeAddress(firstRow, lastRow, spec.valueColumn(), spec.valueColumn())
        );

        if (spec.type() == ChartTypes.PIE) {
            XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
            XDDFChartData.Series series = data.addSeries(categories, values);
            series.setTitle(spec.seriesTitle(), null);
            chart.plot(data);
        } else {
            XDDFCategoryAxis categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            XDDFValueAxis valueAxis = chart.createValueAxis(AxisPosition.LEFT);
            valueAxis.setCrosses(AxisCrosses.AUTO_ZERO);
            XDDFBarChartData data = (XDDFBarChartData) chart.createData(
                    ChartTypes.BAR, categoryAxis, valueAxis);
            data.setBarDirection(BarDirection.COL);
            XDDFChartData.Series series = data.addSeries(categories, values);
            series.setTitle(spec.seriesTitle(), null);
            chart.plot(data);
            fixAxisIds(chart);
        }
        fixDrawingId(drawing);
    }

    private void fixAxisIds(XSSFChart chart) {
        CTPlotArea plot = chart.getCTChart().getPlotArea();
        if (plot.sizeOfCatAxArray() == 0 || plot.sizeOfValAxArray() == 0) return;
        long categoryId = AXIS_IDS.getAndAdd(2);
        long valueId = categoryId + 1;
        CTCatAx category = plot.getCatAxArray(0);
        CTValAx value = plot.getValAxArray(0);
        category.getAxId().setVal(categoryId);
        category.getCrossAx().setVal(valueId);
        value.getAxId().setVal(valueId);
        value.getCrossAx().setVal(categoryId);
        if (plot.sizeOfBarChartArray() > 0) {
            CTBarChart bar = plot.getBarChartArray(0);
            bar.getAxIdArray(0).setVal(categoryId);
            bar.getAxIdArray(1).setVal(valueId);
        }
    }

    private void fixDrawingId(XSSFDrawing drawing) {
        int index = drawing.getCTDrawing().sizeOfTwoCellAnchorArray() - 1;
        if (index < 0) return;
        var anchor = drawing.getCTDrawing().getTwoCellAnchorArray(index);
        if (!anchor.isSetGraphicFrame()) return;
        var properties = anchor.getGraphicFrame()
                .getNvGraphicFramePr()
                .getCNvPr();
        properties.setId(drawingId);
        properties.setName("Gráfico " + drawingId);
        drawingId++;
    }

    private void configurePrint(XSSFSheet sheet, int columns) {
        sheet.setDisplayGridlines(false);
        sheet.setFitToPage(true);
        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setLandscape(columns > 5);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);
        sheet.setAutobreaks(true);
    }

    private static String safeSheetName(String name) {
        String clean = name.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return clean.length() > 31 ? clean.substring(0, 31) : clean;
    }

    private static double number(Object value) {
        if (value instanceof BigDecimal decimal) return decimal.doubleValue();
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(value.toString());
    }

    private static byte[] rgb(String hex) {
        return new byte[]{
                (byte) Integer.parseInt(hex.substring(0, 2), 16),
                (byte) Integer.parseInt(hex.substring(2, 4), 16),
                (byte) Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    private static final class Styles {
        private final XSSFCellStyle title;
        private final XSSFCellStyle header;
        private final XSSFCellStyle metadataLabel;
        private final XSSFCellStyle empty;
        private final Map<String, XSSFCellStyle> data = new LinkedHashMap<>();

        private Styles(XSSFWorkbook workbook) {
            title = workbook.createCellStyle();
            title.setFillForegroundColor(new XSSFColor(TITLE_COLOR));
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setColor(IndexedColors.WHITE.getIndex());
            titleFont.setFontHeightInPoints((short) 15);
            title.setFont(titleFont);

            header = workbook.createCellStyle();
            header.setFillForegroundColor(new XSSFColor(HEADER_COLOR));
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            addBorders(header);
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont);

            metadataLabel = workbook.createCellStyle();
            metadataLabel.setFillForegroundColor(new XSSFColor(ALT_ROW_COLOR));
            metadataLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont metadataFont = workbook.createFont();
            metadataFont.setBold(true);
            metadataFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            metadataLabel.setFont(metadataFont);

            empty = workbook.createCellStyle();
            empty.setAlignment(HorizontalAlignment.CENTER);
            XSSFFont emptyFont = workbook.createFont();
            emptyFont.setItalic(true);
            emptyFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            empty.setFont(emptyFont);

            for (ValueType type : ValueType.values()) {
                data.put(key(type, false), createDataStyle(workbook, type, false));
                data.put(key(type, true), createDataStyle(workbook, type, true));
            }
        }

        private XSSFCellStyle dataStyle(ValueType type, boolean alternate) {
            return data.get(key(type, alternate));
        }

        private static String key(ValueType type, boolean alternate) {
            return type.name() + ":" + alternate;
        }

        private static XSSFCellStyle createDataStyle(
                XSSFWorkbook workbook,
                ValueType type,
                boolean alternate
        ) {
            XSSFCellStyle style = workbook.createCellStyle();
            addBorders(style);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            if (alternate) {
                style.setFillForegroundColor(new XSSFColor(ALT_ROW_COLOR));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            switch (type) {
                case MONEY -> {
                    style.setDataFormat(workbook.createDataFormat().getFormat(MONEY_FORMAT));
                    style.setAlignment(HorizontalAlignment.RIGHT);
                }
                case PERCENT -> {
                    style.setDataFormat(workbook.createDataFormat().getFormat(PERCENT_FORMAT));
                    style.setAlignment(HorizontalAlignment.RIGHT);
                }
                case INTEGER -> style.setAlignment(HorizontalAlignment.RIGHT);
                case DATE -> style.setDataFormat(workbook.createDataFormat().getFormat(DATE_FORMAT));
                case DATE_TIME -> style.setDataFormat(workbook.createDataFormat().getFormat(DATE_TIME_FORMAT));
                default -> style.setAlignment(HorizontalAlignment.LEFT);
            }
            return style;
        }

        private static void addBorders(CellStyle style) {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
    }
}
