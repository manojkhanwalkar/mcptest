package com.example.testdata.creditreport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests for the recently-closed-account alert tool. */
class RecentlyClosedAccountAlertsTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 10, 16);

    private final ByteArrayOutputStream warningBytes = new ByteArrayOutputStream();
    private final PrintStream warnings = new PrintStream(warningBytes, true);

    @Test
    void windowCoversTheTwelveMonthsUpToTheReportDate() {
        assertTrue(RecentlyClosedAccountAlerts.hasRecentlyClosedAccount(REPORT_DATE, List.of(LocalDate.of(2026, 5, 25))));
        assertTrue(RecentlyClosedAccountAlerts.hasRecentlyClosedAccount(REPORT_DATE, List.of(LocalDate.of(2025, 10, 16))));
        assertTrue(RecentlyClosedAccountAlerts.hasRecentlyClosedAccount(REPORT_DATE, List.of(REPORT_DATE)));
        assertFalse(RecentlyClosedAccountAlerts.hasRecentlyClosedAccount(REPORT_DATE, List.of(LocalDate.of(2025, 10, 15))));
        assertFalse(RecentlyClosedAccountAlerts.hasRecentlyClosedAccount(REPORT_DATE, List.of(REPORT_DATE.plusDays(1))));
        assertFalse(RecentlyClosedAccountAlerts.hasRecentlyClosedAccount(REPORT_DATE, List.of()));
    }

    @Test
    void listsOnlyReportsWithAnAccountClosedInThePastYear(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("credit-report-a.md"), report("ELENA C MOCKRIDGE", "—", "05/25/2026"));
        Files.writeString(dir.resolve("credit-report-b.md"), report("OLD CLOSER", "—", "10/15/2025"));
        Files.writeString(dir.resolve("credit-report-c.md"), report("ALL OPEN", "—", "—"));
        Files.writeString(dir.resolve("credit-report-d.md"), report("BOUNDARY CASE", "10/16/2025"));

        assertEquals(List.of("ELENA C MOCKRIDGE", "BOUNDARY CASE"), RecentlyClosedAccountAlerts.find(dir, warnings));
        assertEquals("", warningBytes.toString());
    }

    @Test
    void readsOnlyMarkdownFilesDirectlyInTheDirectoryInFileNameOrder(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("credit-report-b.md"), report("SECOND", "09/01/2026"));
        Files.writeString(dir.resolve("credit-report-a.md"), report("FIRST", "09/01/2026"));
        Files.writeString(dir.resolve("credit-report-c.txt"), report("NOT MARKDOWN", "09/01/2026"));
        Path nested = Files.createDirectory(dir.resolve("nested"));
        Files.writeString(nested.resolve("credit-report-d.md"), report("NESTED", "09/01/2026"));

        assertEquals(List.of("FIRST", "SECOND"), RecentlyClosedAccountAlerts.find(dir, warnings));
    }

    @Test
    void skipsFilesThatAreNotCreditReportsWithAWarning(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("credit-report-a.md"), report("ELENA C MOCKRIDGE", "05/25/2026"));
        Files.writeString(dir.resolve("notes.md"), "# Notes\n\n| Date closed | 05/25/2026 |\n");

        assertEquals(List.of("ELENA C MOCKRIDGE"), RecentlyClosedAccountAlerts.find(dir, warnings));
        assertTrue(warningBytes.toString().contains("notes.md"), warningBytes.toString());
    }

    @Test
    void skipsReportsWithAnInvalidDateGenerated(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("credit-report-a.md"),
                report("BAD DATE", "09/01/2026").replace(date(REPORT_DATE), "02/30/2026"));

        assertEquals(List.of(), RecentlyClosedAccountAlerts.find(dir, warnings));
        assertTrue(warningBytes.toString().contains("credit-report-a.md"), warningBytes.toString());
    }

    @Test
    void ignoresInvalidClosedDates(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("credit-report-a.md"), report("IMPOSSIBLE CLOSE", "02/30/2026"));

        assertEquals(List.of(), RecentlyClosedAccountAlerts.find(dir, warnings));
    }

    @Test
    void ignoresClosedDatesOutsideTheAccountsSection(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("credit-report-a.md"),
                report("NO RECENT CLOSE", "—") + "## Public records\n\n| | |\n|---|---|\n| Date closed | 09/01/2026 |\n");

        assertEquals(List.of(), RecentlyClosedAccountAlerts.find(dir, warnings));
    }

    @Test
    void skipsUnreadableFilesWithAWarningAndCarriesOn(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("credit-report-a.md"), new byte[]{'#', ' ', (byte) 0xE9, (byte) 0xFF, '\n'});
        Files.writeString(dir.resolve("credit-report-b.md"), report("ELENA C MOCKRIDGE", "05/25/2026"));

        assertEquals(List.of("ELENA C MOCKRIDGE"), RecentlyClosedAccountAlerts.find(dir, warnings));
        assertTrue(warningBytes.toString().contains("credit-report-a.md"), warningBytes.toString());
    }

    @Test
    void generatedReportsQualify(@TempDir Path dir) throws IOException {
        Path file = CreditReportCreator.create(dir, 7, REPORT_DATE);
        String name = new CreditReportGenerator(7, REPORT_DATE).generate().personalInformation().names().getFirst();

        assertTrue(Files.exists(file));
        assertEquals(List.of(name), RecentlyClosedAccountAlerts.find(dir, warnings));
    }

    /** A minimal report in the layout {@link MarkdownCreditReportWriter} writes, dated {@link #REPORT_DATE}. */
    private static String report(String name, String... closedDates) {
        StringBuilder md = new StringBuilder("# Credit Report (Experian consumer report format)\n\n| | |\n|---|---|\n");
        md.append("| **Prepared for** | ").append(name).append(" |\n");
        md.append("| **Date generated** | ").append(date(REPORT_DATE)).append(" |\n\n## Accounts\n\n");
        for (String closed : closedDates) {
            md.append("| Account info | |\n|---|---|\n| Date closed | ").append(closed).append(" |\n\n");
        }
        return md.toString();
    }

    private static String date(LocalDate d) {
        return MarkdownCreditReportWriter.DATE.format(d);
    }
}
