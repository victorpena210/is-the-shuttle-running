package technology.renatus.routepulse.scheduling.importing;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.time.*;
import java.util.zip.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DayforceXlsxParserTests {
    private final DayforceCsvParser parser = new DayforceCsvParser();
    private MockMultipartFile file(XSSFWorkbook workbook) throws IOException {
        var output = new ByteArrayOutputStream(); workbook.write(output);
        return new MockMultipartFile("scheduleFile", "schedule.XLSX", "application/octet-stream", output.toByteArray());
    }
    private Sheet sheet(XSSFWorkbook workbook, String name) {
        var sheet = workbook.createSheet(name);
        sheet.createRow(0).createCell(0).setCellValue("Dayforce report");
        var header = sheet.createRow(2);
        String[] headings = {"Scheduled Shift End Date Time", "Employee Full Name", "Employee ID", "Scheduled Shift Start Date Time"};
        for (int i = 0; i < headings.length; i++) header.createCell(i).setCellValue(headings[i]);
        return sheet;
    }
    private void shift(Sheet sheet, int index) {
        var row = sheet.createRow(index);
        row.createCell(0).setCellValue("10/25/2026 12:15 AM"); row.createCell(1).setCellValue("Sample, Driver");
        row.createCell(2).setCellValue("00100"); row.createCell(3).setCellValue("10/24/2026 11:00 PM");
    }
    @Test void handlesPreamblesBlankRowsCoverSheetsTextDatesAndCrossSheetDuplicates() throws Exception {
        try (var wb = new XSSFWorkbook()) {
            wb.createSheet("Cover").createRow(0).createCell(0).setCellValue("Weekly schedules");
            var a = sheet(wb, "Week one"); shift(a, 4); shift(a, 6);
            a.createRow(8).createCell(2).setCellValue("200"); // Unscheduled employee.
            var b = sheet(wb, "Week two"); shift(b, 3);
            var rows = parser.parse(file(wb)); assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getEmployeeId()).isEqualTo("00100");
            assertThat(rows.get(0).getShiftDate()).isEqualTo(LocalDate.of(2026,10,24));
            assertThat(rows.get(0).getEndTime()).isEqualTo(LocalTime.of(0,15));
        }
    }
    @Test void readsNativeDatesIndependentOfDisplayFormatAndPreservesFormattedIds() throws Exception {
        for (boolean date1904 : new boolean[]{false, true}) try (var wb = new XSSFWorkbook()) {
            wb.getCTWorkbook().getWorkbookPr().setDate1904(date1904);
            var s = sheet(wb, "Schedule"); shift(s, 3); var r = s.getRow(3);
            r.getCell(3).setCellValue(DateUtil.getExcelDate(LocalDateTime.of(2026,10,24,9,7), date1904));
            r.getCell(0).setCellValue(DateUtil.getExcelDate(LocalDateTime.of(2026,10,24,17,14), date1904));
            var style = wb.createCellStyle(); style.setDataFormat(wb.createDataFormat().getFormat("00000"));
            r.getCell(2).setCellValue(100); r.getCell(2).setCellStyle(style);
            var rows = parser.parse(file(wb)); assertThat(rows.get(0).getEmployeeId()).isEqualTo("00100");
            assertThat(rows.get(0).getStartTime()).isEqualTo(LocalTime.of(9,7));
            assertThat(rows.get(0).getEndTime()).isEqualTo(LocalTime.of(17,14));
        }
    }
    @Test void generalNumericIdsAreImportedAsWholeNumbers() throws Exception {
        try (var wb = new XSSFWorkbook()) {
            var s = sheet(wb, "Schedule"); shift(s,3); s.getRow(3).getCell(2).setCellValue(123456789012d);
            assertThat(parser.parse(file(wb)).get(0).getEmployeeId()).isEqualTo("123456789012");
        }
    }
    @Test void rejectsInvalidLaterWorksheetWithRowLocation() throws Exception {
        try (var wb = new XSSFWorkbook()) {
            shift(sheet(wb,"Good"),3); var s = sheet(wb,"Bad"); shift(s,3); s.getRow(3).getCell(0).setCellValue("bad date");
            assertThatThrownBy(() -> parser.parse(file(wb))).hasMessageContaining("worksheet 2, row 4");
        }
    }
    @Test void rejectsFormulaAndErrorCellsWithoutEvaluatingThem() throws Exception {
        for (boolean formula : new boolean[]{true,false}) try (var wb = new XSSFWorkbook()) {
            var s = sheet(wb,"Schedule"); shift(s,3);
            if (formula) s.getRow(3).getCell(3).setCellFormula("NOW()"); else s.getRow(3).getCell(3).setCellErrorValue(FormulaError.VALUE.getCode());
            assertThatThrownBy(() -> parser.parse(file(wb))).hasMessageContaining("replace formulas or Excel errors with values");
        }
    }
    @Test void rejectsMissingDuplicateHeadersAndEmptyWorkbooks() throws Exception {
        for (int kind=0; kind<3; kind++) try (var wb = new XSSFWorkbook()) {
            var s=sheet(wb,"Schedule"); shift(s,3);
            if (kind==0) s.getRow(2).getCell(0).setCellValue("Other");
            if (kind==1) s.getRow(2).createCell(4).setCellValue("Shift End");
            if (kind==2) wb.removeSheetAt(0);
            assertThatThrownBy(() -> parser.parse(file(wb))).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void rejectsCorruptUnsupportedOversizedAndOverexpandedFiles() throws Exception {
        for (String name : new String[]{"bad.xlsx","old.xls","macro.xlsm"})
            assertThatThrownBy(() -> parser.parse(new MockMultipartFile("scheduleFile",name,"application/octet-stream",new byte[]{1,2,3})))
                    .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> parser.parse(new MockMultipartFile("scheduleFile","large.xlsx","application/octet-stream",new byte[5*1024*1024+1])))
                .hasMessageContaining("5 MB");
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("large.xml"));zip.write(new byte[20*1024*1024+1]);zip.closeEntry();}
        assertThatThrownBy(() -> parser.parse(new MockMultipartFile("scheduleFile","expanded.xlsx","application/octet-stream",bytes.toByteArray())))
                .hasMessageContaining("too large");
    }
    @Test void rejectsWideWorksheetsAndTimeOnlyNumericCells() throws Exception {
        try (var wb = new XSSFWorkbook()) {
            var s=sheet(wb,"Schedule"); shift(s,3); s.getRow(3).getCell(3).setCellValue(0.5);
            assertThatThrownBy(() -> parser.parse(file(wb))).hasMessageContaining("Include both the date and time");
            shift(s,3);s.getRow(3).createCell(128).setCellValue("Too wide");
            assertThatThrownBy(() -> parser.parse(file(wb))).hasMessageContaining("too large");
        }
    }
}
