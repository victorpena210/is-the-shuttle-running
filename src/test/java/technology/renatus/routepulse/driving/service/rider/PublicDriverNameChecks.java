package technology.renatus.routepulse.driving.service.rider;

/** Also runnable directly with javac/java when Maven dependencies are unavailable. */
public final class PublicDriverNameChecks {
    public static void main(String[] args) {
        verify();
        System.out.println("PASS: 12 public driver name privacy checks");
    }

    public static void verify() {
        check("Victor", "Victor Peña");
        check("Victor", "Victor M. Peña");
        check("Victor", "Peña, Victor M.");
        check("Maria", "De La Cruz, Maria A.");
        check("Anne-Marie", "O'Neill, Anne-Marie J.");
        check("José", "José A. García");
        check("D'Andre", "D'Andre M. Jones");
        check("Victor", "  Peña,\tVictor\u00a0M.  ");
        check("Victor", "Victor");
        check("Driver", null);
        check("Driver", " \t\u00a0 ");
        check("Driver", "Peña, ");
    }

    private static void check(String expected, String fullName) {
        String actual = PublicDriverName.firstName(fullName);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
