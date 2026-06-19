package com.restaurante.modules.reportes.infrastructure.excel;

import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelReportBuilderTest {

    @Test
    void generaEstilosYDibujosCompatiblesConExcel() throws Exception {
        byte[] bytes;
        try (ExcelReportBuilder builder = new ExcelReportBuilder()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("Desde", "2026-06-12");
            metadata.put("Hasta", "2026-06-18");
            metadata.put("Moneda", "Soles (PEN)");

            builder.createSummarySheet(
                    "LA FLOR DEL TUMBO",
                    "REPORTE DE VENTAS",
                    metadata,
                    Map.of(
                            "Total de ventas", new BigDecimal("1250.50"),
                            "Comprobantes", 25
                    )
            );
            builder.createTableSheet(
                    "Ventas por día",
                    "VENTAS POR DÍA",
                    metadata,
                    List.of(
                            ExcelReportBuilder.column("Fecha", ExcelReportBuilder.ValueType.TEXT, 16),
                            ExcelReportBuilder.column("Comprobantes", ExcelReportBuilder.ValueType.INTEGER, 16),
                            ExcelReportBuilder.column("Total", ExcelReportBuilder.ValueType.MONEY, 18)
                    ),
                    List.of(
                            new Object[]{"2026-06-17", 10, new BigDecimal("500.00")},
                            new Object[]{"2026-06-18", 15, new BigDecimal("750.50")}
                    ),
                    new ExcelReportBuilder.ChartSpec(
                            ChartTypes.BAR,
                            "Ventas por día",
                            "Total vendido (S/)",
                            0,
                            2
                    )
            );
            bytes = builder.toBytes();
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Resumen"));
            assertNotNull(workbook.getSheet("Ventas por día"));
            assertFalse(workbook.getSheet("Ventas por día")
                    .getDrawingPatriarch().getCharts().isEmpty());
        }

        Map<String, String> entries = unzipText(bytes);
        String styles = entries.get("xl/styles.xml");
        String drawing = entries.get("xl/drawings/drawing1.xml");
        String chart = entries.get("xl/charts/chart1.xml");

        assertNotNull(styles);
        assertTrue(styles.contains("&quot;S/&quot; #,##0.00"));
        assertNotNull(drawing);
        assertFalse(drawing.contains("cNvPr id=\"0\""));
        assertNotNull(chart);
        assertTrue(chart.contains("<c:legend>"));
        assertTrue(chart.contains("Total vendido (S/)"));

        long[] axisIds = axisIds(chart);
        assertTrue(axisIds[0] > 0);
        assertTrue(axisIds[1] > 0);
        assertNotEquals(axisIds[0], axisIds[1]);
    }

    private static Map<String, String> unzipText(byte[] bytes) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".xml")) {
                    entries.put(entry.getName(), new String(zip.readAllBytes()));
                }
            }
        }
        return entries;
    }

    private static long[] axisIds(String chart) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<c:axId val=\"(\\d+)\"")
                .matcher(chart);
        long first = matcher.find() ? Long.parseLong(matcher.group(1)) : 0;
        long second = matcher.find() ? Long.parseLong(matcher.group(1)) : 0;
        return new long[]{first, second};
    }
}
