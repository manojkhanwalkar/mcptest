package com.example.testdata.creditreport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests for the dummy credit report generator and its Markdown output. */
class CreditReportCreatorTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 10, 15);

    @Test
    void sameSeedProducesSameReport() {
        assertEquals(new CreditReportGenerator(42, REPORT_DATE).generate(),
                new CreditReportGenerator(42, REPORT_DATE).generate());
    }

    @Test
    void generatedAccountsAreConsistent() {
        for (long seed = 0; seed < 200; seed++) {
            CreditReport report = new CreditReportGenerator(seed, REPORT_DATE).generate();
            assertFalse(report.accounts().isEmpty());
            for (CreditReport.Account a : report.accounts()) {
                assertTrue(a.dateOpened().isBefore(REPORT_DATE), "opened before report date");
                assertTrue(a.paymentHistory().size() <= CreditReportGenerator.HISTORY_MONTHS);
                assertTrue(a.balance() >= 0);
                if (!a.isOpen()) {
                    assertEquals(0, a.balance(), "closed accounts have no balance");
                }
            }
        }
    }

    @Test
    void writesMarkdownFileWithAllReportSections(@TempDir Path dir) throws IOException {
        Path file = CreditReportCreator.create(dir, 7, REPORT_DATE);

        String markdown = Files.readString(file);
        assertTrue(file.getFileName().toString().endsWith(".md"));
        assertTrue(markdown.contains("TEST DATA"));
        for (String section : new String[]{"## At a glance", "## Personal information", "## Accounts",
                "## Public records", "## Inquiries", "## Personal statements", "## Contact us"}) {
            assertTrue(markdown.contains(section), "missing section " + section);
        }
    }
}
