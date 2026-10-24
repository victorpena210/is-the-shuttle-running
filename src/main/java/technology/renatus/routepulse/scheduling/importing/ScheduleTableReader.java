package technology.renatus.routepulse.scheduling.importing;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.apache.commons.csv.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

/** Bounded input decoding; all schedule validation remains shared with the CSV importer. */
final class ScheduleTableReader {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_EXPANDED_BYTES = 20 * 1024 * 1024;
    private static final Set<String> DATES = Set.of("shiftstart", "shiftend", "scheduledshiftstartdatetime", "scheduledshiftenddatetime");
    private static final java.time.format.DateTimeFormatter DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("M/d/uuuu h:mm a", Locale.US);
    record Row(List<String> values, String location) {
        int size() { return values.size(); }
        String get(int index) { return values.get(index); }
    }
    static List<List<Row>> read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalStateException("Please select a Dayforce CSV or XLSX file to upload.");
        if (file.getSize() > MAX_BYTES) throw new IllegalStateException("Upload a CSV or XLSX no larger than 5 MB.");
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".csv") && !name.endsWith(".xlsx"))
            throw new IllegalStateException("Choose a .csv or .xlsx schedule. Save older Excel files as .xlsx first.");
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IllegalStateException("Upload a CSV or XLSX no larger than 5 MB.");
            return name.endsWith(".xlsx") ? excel(bytes) : csv(bytes);
        } catch (IOException | UncheckedIOException e) {
            throw new IllegalStateException("Could not read the schedule. Save it as a CSV or an unprotected XLSX and try again.");
        }
    }
    private static List<List<Row>> csv(byte[] bytes) throws IOException {
        var rows = new ArrayList<Row>();
        try (var parser = CSVFormat.DEFAULT.builder().setTrim(true).setIgnoreEmptyLines(true).get()
                .parse(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            for (var row : parser) {
                if (rows.size() >= 50000 || row.size() > 128) throw tooLarge();
                rows.add(new Row(row.stream().toList(), "record " + row.getRecordNumber()));
            }
        }
        return List.of(rows);
    }
    private static List<List<Row>> excel(byte[] bytes) throws IOException {
        // Bound the complete archive before POI builds its in-memory document objects.
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            int entries = 0, expanded = 0; byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entries > 1000) throw tooLarge();
                int n;
                while ((n = zip.read(buffer)) != -1) { expanded += n; if (expanded > MAX_EXPANDED_BYTES) throw tooLarge(); }
            }
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() > 100) throw tooLarge();
            var tables = new ArrayList<List<Row>>(); var formatter = new DataFormatter(Locale.US);
            int rowCount = 0;
            for (var sheet : workbook) {
                var rows = new ArrayList<Row>(); Set<Integer> dateColumns = new HashSet<>();
                Integer idColumn = null; boolean header = false;
                for (var row : sheet) {
                    if (++rowCount > 50000 || row.getLastCellNum() > 128) throw tooLarge();
                    String location = "worksheet " + (workbook.getSheetIndex(sheet) + 1) + ", row " + (row.getRowNum() + 1);
                    var values = new ArrayList<String>();
                    for (int i = 0; i < row.getLastCellNum(); i++) {
                        var cell = row.getCell(i);
                        if (cell == null) { values.add(""); continue; }
                        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)
                            throw new IllegalStateException("Invalid data in " + location + ": replace formulas or Excel errors with values before uploading.");
                        if (header && dateColumns.contains(i) && cell.getCellType() == CellType.NUMERIC) {
                            double serial = cell.getNumericCellValue();
                            if (!DateUtil.isValidExcelDate(serial) || serial < 1 || serial > 2958465)
                                throw new IllegalStateException("Invalid date in " + location + ". Include both the date and time.");
                            values.add(DateUtil.getLocalDateTime(serial, workbook.isDate1904()).format(DATE_FORMAT));
                        } else if (Objects.equals(idColumn, i) && cell.getCellType() == CellType.NUMERIC
                                && "General".equals(cell.getCellStyle().getDataFormatString())) {
                            values.add(BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString());
                        } else values.add(formatter.formatCellValue(cell));
                    }
                    if (!header && values.stream().anyMatch(v -> normalize(v).equals("employeeid"))) {
                        header = true;
                        for (int i = 0; i < values.size(); i++) {
                            String heading = normalize(values.get(i));
                            if (DATES.contains(heading)) dateColumns.add(i);
                            if (heading.equals("employeeid")) idColumn = i;
                        }
                    }
                    rows.add(new Row(values, location));
                }
                tables.add(rows);
            }
            return tables;
        } catch (IllegalStateException e) { throw e; }
        catch (Exception e) {
            throw new IllegalStateException("Could not read the Excel workbook. Save it as an unprotected .xlsx file and try again.");
        }
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private static IllegalStateException tooLarge() { return new IllegalStateException("The schedule is too large to import. Use at most 50,000 rows and 128 columns, and remove unused worksheets or formatting."); }
}
