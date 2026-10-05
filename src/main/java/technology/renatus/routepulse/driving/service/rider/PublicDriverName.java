package technology.renatus.routepulse.driving.service.rider;

/** Formats names for public rider pages without changing internal driver records. */
public final class PublicDriverName {

    private PublicDriverName() {
    }

    public static String firstName(String fullName) {
        if (fullName == null) {
            return "Driver";
        }

        // Drivers may be entered as First Middle Last or imported as Last, First Middle.
        String givenNames = fullName;
        int comma = givenNames.indexOf(',');
        if (comma >= 0) {
            givenNames = givenNames.substring(comma + 1);
        }
        givenNames = givenNames.replaceAll("(?U)\\s+", " ").trim();
        if (givenNames.isEmpty()) {
            return "Driver";
        }

        // Keep hyphens, apostrophes and accents; omit all remaining name parts.
        return givenNames.split("[ ,]", 2)[0];
    }
}
