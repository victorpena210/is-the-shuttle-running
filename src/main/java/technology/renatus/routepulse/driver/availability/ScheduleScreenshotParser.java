package technology.renatus.routepulse.driver.availability;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Conservative list-view parser. Never infers a missing AM/PM, day off, or week. */
public final class ScheduleScreenshotParser {
    private ScheduleScreenshotParser() { }
    public record Row(String date, String start, String end, boolean off) { }
    public record Result(List<Row> rows, List<String> warnings) { }
    private static final Pattern DATE = Pattern.compile("(?i)\\b(?:(20\\d{2})-(\\d{1,2})-(\\d{1,2})|(\\d{1,2})/(\\d{1,2})(?:/(20\\d{2}))?|(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\.?\\s+(\\d{1,2})(?:,?\\s+(20\\d{2}))?)\\b");
    private static final Pattern TIME = Pattern.compile("(?i)(?<![\\d:/])(\\d{1,2})(?::([0-5]\\d))?\\s*([ap]\\.?m\\.?)?(?![\\d:/])");
    private static final List<String> MONTHS = List.of("jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec");

    public static Result parse(String text, int year) {
        List<Row> rows = new ArrayList<>(); Set<String> warnings = new LinkedHashSet<>();
        String currentDate = null; int distance = 0;
        for (String line : text.replace('\u2013','-').replace('\u2014','-').split("\\R")) {
            Matcher dateMatcher = DATE.matcher(line); List<String> dates = new ArrayList<>();
            while (dateMatcher.find()) {
                try {
                    int y, m, d;
                    if (dateMatcher.group(1) != null) {
                        y = Integer.parseInt(dateMatcher.group(1)); m = Integer.parseInt(dateMatcher.group(2)); d = Integer.parseInt(dateMatcher.group(3));
                    } else if (dateMatcher.group(4) != null) {
                        y = dateMatcher.group(6) == null ? year : Integer.parseInt(dateMatcher.group(6));
                        m = Integer.parseInt(dateMatcher.group(4)); d = Integer.parseInt(dateMatcher.group(5));
                    } else {
                        y = dateMatcher.group(9) == null ? year : Integer.parseInt(dateMatcher.group(9));
                        m = MONTHS.indexOf(dateMatcher.group(7).substring(0,3).toLowerCase(Locale.US)) + 1;
                        d = Integer.parseInt(dateMatcher.group(8));
                    }
                    dates.add(LocalDate.of(y,m,d).toString());
                } catch (DateTimeException ex) { currentDate = null; warnings.add("An unreadable date needs manual entry."); }
            }
            if (dates.size() > 1) {
                currentDate = null;
                warnings.add("A date range or calendar grid was found. Use a daily list screenshot, or enter each date manually.");
                continue;
            }
            if (dates.size() == 1) { currentDate = dates.get(0); distance = 0; }
            else if (++distance > 3) currentDate = null;
            String rest = DATE.matcher(line).replaceAll("");
            if (Pattern.compile("(?i)\\b(off|not scheduled|day off)\\b").matcher(rest).find() && currentDate != null) {
                rows.add(new Row(currentDate,"","",true)); currentDate = null; continue;
            }
            Matcher times = TIME.matcher(rest); List<String> values = new ArrayList<>(); boolean ambiguous = false;
            while (times.find()) {
                if (times.group(2) == null && times.group(3) == null) continue;
                int hour = Integer.parseInt(times.group(1)); int minute = times.group(2) == null ? 0 : Integer.parseInt(times.group(2));
                String suffix = times.group(3);
                if (hour > 23 || suffix != null && (hour < 1 || hour > 12)) { ambiguous = true; continue; }
                if (suffix != null) hour = hour % 12 + (suffix.toLowerCase(Locale.US).startsWith("p") ? 12 : 0);
                else if (hour >= 1 && hour <= 12) { ambiguous = true; }
                values.add(LocalTime.of(hour, minute).toString());
            }
            if (values.isEmpty()) continue;
            if (ambiguous || values.size() != 2) {
                warnings.add("Some times are unclear or missing AM/PM. Fill in those shifts manually.");
                if (currentDate != null) rows.add(new Row(currentDate,"","",false));
            } else {
                rows.add(new Row(currentDate == null ? "" : currentDate, values.get(0), values.get(1),false));
                if (currentDate == null) warnings.add("A shift had no clear date. Enter its date before confirming.");
            }
        }
        if (rows.isEmpty()) warnings.add("No complete shifts were detected. You can enter your schedule below.");
        if (rows.size() > 60) throw new IllegalArgumentException("Upload a smaller section of your schedule (up to 60 shifts).");
        return new Result(rows.stream().distinct().toList(), List.copyOf(warnings));
    }
}
