package technology.renatus.routepulse.driver.calendar;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/** Deliberately accepts UKG's expanded shift feed, not arbitrary calendar programs. */
public final class UkgCalendarParser {
    private UkgCalendarParser() { }
    public record Shift(Instant start, Instant end) { }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(ResolverStyle.STRICT);
    public static List<Shift> parse(byte[] bytes) {
        if (bytes.length > 1_048_576) throw invalid();
        String text = new String(bytes, StandardCharsets.UTF_8).replace("\ufeff", "").replace("\r\n", "\n");
        if (text.indexOf('\ufffd') >= 0 || !text.strip().startsWith("BEGIN:VCALENDAR") || !text.strip().endsWith("END:VCALENDAR")) throw invalid();
        var lines = new ArrayList<String>();
        for (String line : text.split("\n", -1)) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (lines.isEmpty()) throw invalid();
                int i = lines.size() - 1; lines.set(i, lines.get(i) + line.substring(1));
            } else lines.add(line);
        }
        var stack = new ArrayDeque<String>(); var latest = new HashMap<String,Map<String,Property>>();
        Map<String, Property> event = null; int events = 0;
        for (String line : lines) {
            if (line.isBlank()) continue;
            int colon = line.indexOf(':'); if (colon < 1) throw invalid();
            String key = line.substring(0, colon), value = line.substring(colon + 1);
            String name = key.split(";", 2)[0].toUpperCase(Locale.ROOT);
            if (name.equals("BEGIN")) {
                if (value.equals("VEVENT")) {
                    if (event != null || stack.size() != 1 || !"VCALENDAR".equals(stack.peek())) throw invalid();
                    if (++events > 2000) throw invalid(); event = new HashMap<>();
                }
                stack.push(value);
            } else if (name.equals("END")) {
                if (stack.isEmpty() || !stack.pop().equals(value)) throw invalid();
                if (value.equals("VEVENT")) {
                    if (event == null) throw invalid();
                    String uid=value(event,"UID");if(uid.isBlank()||uid.length()>1000)throw invalid();
                    var prior=latest.get(uid);
                    if(prior==null||sequence(event)>sequence(prior))latest.put(uid,event);
                    else if(sequence(event)==sequence(prior)&&!event.equals(prior))throw invalid();
                    event = null;
                }
            } else if (event != null && "VEVENT".equals(stack.peek())) {
                if (event.putIfAbsent(name, new Property(key, value)) != null && Set.of("DTSTART","DTEND","SUMMARY","STATUS").contains(name)) throw invalid();
            }
        }
        if (!stack.isEmpty() || event != null) throw invalid();
        var result=new LinkedHashSet<Shift>();
        for(var entry:latest.values()) {
            if("CANCELLED".equalsIgnoreCase(value(entry,"STATUS")))continue;
            if(entry.keySet().stream().anyMatch(Set.of("RRULE","RDATE","EXDATE","RECURRENCE-ID")::contains))
                throw new IllegalArgumentException("This calendar uses repeating shifts that cannot be synced. Use the expanded UKG shift subscription.");
            if(!value(entry,"SUMMARY").matches("(?i)ESS[- ]Shift(?:\\b.*)?"))
                throw new IllegalArgumentException("In UKG, select Shifts only and turn off Absences and Schedule tags, then copy a new subscription link.");
            Instant start=time(entry.get("DTSTART")),end=time(entry.get("DTEND"));
            if(!end.isAfter(start)||Duration.between(start,end).compareTo(Duration.ofHours(24))>0)throw invalid();
            result.add(new Shift(start,end));
        }
        return result.stream().sorted(Comparator.comparing(Shift::start)).toList();
    }
    private static int sequence(Map<String,Property> event) {
        try {int value=event.containsKey("SEQUENCE")?Integer.parseInt(value(event,"SEQUENCE")):0;if(value<0)throw invalid();return value;}
        catch(RuntimeException exception){throw invalid();}
    }
    private record Property(String key, String value) { }
    private static String value(Map<String,Property> event, String name) { return event.containsKey(name) ? event.get(name).value() : ""; }
    private static Instant time(Property property) {
        try {
            if (property == null) throw invalid();
            String value = property.value();
            if (value.endsWith("Z")) return LocalDateTime.parse(value.substring(0,value.length()-1),TIME).toInstant(ZoneOffset.UTC);
            String id = Arrays.stream(property.key().split(";")).filter(p -> p.startsWith("TZID=")).findFirst().orElseThrow().substring(5).replace("\"", "");
            var local = LocalDateTime.parse(value,TIME); var zone = ZoneId.of(id); var offsets = zone.getRules().getValidOffsets(local);
            if (offsets.size() != 1) throw invalid(); // Ambiguous/nonexistent times require UKG's UTC representation.
            return local.toInstant(offsets.get(0));
        } catch (RuntimeException exception) { throw invalid(); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("UKG returned an incomplete or unsupported shift calendar. Your saved availability was not changed."); }
}
