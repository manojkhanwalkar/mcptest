package com.example.mcp.creditreport.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreditReportStoreTest {

    static final String REPORT = """
            # Credit Report (Experian consumer report format)

            | | |
            |---|---|
            | **Prepared for** | ELENA C MOCKRIDGE |
            | **Date generated** | 10/15/2026 |
            | **Report number** | 2416-0737-69 |
            """;

    @TempDir
    Path dir;

    CreditReportStore store;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(dir.resolve("credit-report-2416-0737-69.md"), REPORT);
        Files.writeString(dir.resolve("credit-report-0001-0002-03.md"), "# no header table");
        Files.writeString(dir.resolve("notes.md"), "not a report");
        Files.writeString(dir.resolve("credit-report-bad.md"), "wrong name");
        Files.createDirectory(dir.resolve("credit-report-9999-9999-99.md"));
        store = new CreditReportStore(dir);
    }

    @Test
    void listsOnlyReportFilesSortedByNumber() throws IOException {
        List<CreditReportStore.ReportSummary> reports = store.list();

        assertEquals(List.of("0001-0002-03", "2416-0737-69"),
                reports.stream().map(CreditReportStore.ReportSummary::reportNumber).toList());
    }

    @Test
    void summaryReadsHeaderFields() throws IOException {
        CreditReportStore.ReportSummary report = store.list().get(1);

        assertEquals("ELENA C MOCKRIDGE", report.preparedFor());
        assertEquals("10/15/2026", report.dateGenerated());
        assertEquals("credit-report-2416-0737-69.md", report.fileName());
        assertEquals(Files.size(dir.resolve("credit-report-2416-0737-69.md")), report.sizeBytes());
    }

    @Test
    void summaryFieldsAreNullWhenHeaderIsMissing() throws IOException {
        CreditReportStore.ReportSummary report = store.list().getFirst();

        assertNull(report.preparedFor());
        assertNull(report.dateGenerated());
    }

    @Test
    void readsFullReport() throws Exception {
        assertEquals(REPORT, store.read("2416-0737-69"));
    }

    @Test
    void unknownReportNumberIsNotFound() {
        assertThrows(CreditReportStore.ReportNotFoundException.class, () -> store.read("1111-2222-33"));
    }

    @Test
    void directoryWithReportNameIsNotFound() {
        assertThrows(CreditReportStore.ReportNotFoundException.class, () -> store.read("9999-9999-99"));
    }

    @Test
    void malformedReportNumbersAreRejected() {
        for (String bad : new String[]{null, "", "bad", "../../etc/passwd", "2416-0737-69/../x", "2416-0737-690"}) {
            assertThrows(IllegalArgumentException.class, () -> store.read(bad), String.valueOf(bad));
        }
    }

    @Test
    void symbolicLinkOutOfTheDirectoryIsNotServed(@TempDir Path outside) throws IOException {
        Path secret = Files.writeString(outside.resolve("secret.txt"), "not a credit report");
        Files.createSymbolicLink(dir.resolve("credit-report-5555-5555-55.md"), secret);

        assertThrows(CreditReportStore.ReportNotFoundException.class, () -> store.read("5555-5555-55"));
        assertEquals(List.of("0001-0002-03", "2416-0737-69"),
                store.list().stream().map(CreditReportStore.ReportSummary::reportNumber).toList());
    }

    @Test
    void missingDirectoryFailsToList() {
        CreditReportStore missing = new CreditReportStore(dir.resolve("nope"));

        assertThrows(NoSuchFileException.class, missing::list);
    }
}
