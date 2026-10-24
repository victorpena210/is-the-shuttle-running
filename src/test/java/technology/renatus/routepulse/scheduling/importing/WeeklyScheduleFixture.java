package technology.renatus.routepulse.scheduling.importing;

import java.io.*;
import java.time.LocalDate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.mock.web.MockMultipartFile;

/** Fictional data with the observed BIRT/UKG merged-column and continuation-row layout. */
public final class WeeklyScheduleFixture {
    public static MockMultipartFile file(String name, String saturday, String continuation, String sunday) throws IOException {
        try (var wb = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet=wb.createSheet("Single Table");
            sheet.createRow(0).createCell(12).setCellValue("Employee Schedule - Weekly");
            sheet.createRow(1).createCell(4).setCellValue("11/02/2026 - 11/08/2026");
            sheet.createRow(5).createCell(5).setCellValue("[WFM-COMMON-1234] Failed to retrieve some data from the providers (Money Amount)");
            sheet.createRow(6).createCell(0).setCellValue("Sample/Operations/Wardrobe");
            var header=sheet.createRow(7);header.createCell(0).setCellValue("Employee");header.createCell(6).setCellValue("Primary Job");
            sheet.addMergedRegion(new CellRangeAddress(7,8,0,5));sheet.addMergedRegion(new CellRangeAddress(7,8,6,7));
            int[] cols={8,11,13,16,17,18,19};String[] days={"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
            var dates=sheet.createRow(8);var style=wb.createCellStyle();style.setDataFormat(wb.createDataFormat().getFormat("[$-409]M/dd/yyyy"));
            for(int i=0;i<7;i++){header.createCell(cols[i]).setCellValue(days[i]);var c=dates.createCell(cols[i]);c.setCellValue(LocalDate.of(2026,11,2).plusDays(i));c.setCellStyle(style);}
            var row=sheet.createRow(9);row.createCell(0).setCellValue(name);row.createCell(6).setCellValue("Wardrobe Attendant");
            row.createCell(18).setCellValue(saturday);row.createCell(19).setCellValue(sunday);
            sheet.createRow(10).createCell(18).setCellValue(continuation);
            wb.write(bytes);return new MockMultipartFile("scheduleFile","weekly-driver.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes.toByteArray());
        }
    }
}
