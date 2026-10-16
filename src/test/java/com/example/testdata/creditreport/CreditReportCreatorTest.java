package com.example.testdata.creditreport;

import com.example.testdata.creditreport.CreditReport.Account;
import com.example.testdata.creditreport.CreditReport.PaymentCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void everyReportHasLatePaymentsAndRecentlyClosedAndOpenedAccounts() {
        for (long seed = 0; seed < 200; seed++) {
            List<Account> accounts = new CreditReportGenerator(seed, REPORT_DATE).generate().accounts();
            String context = "seed " + seed;

            assertTrue(accounts.stream().anyMatch(a -> a.paymentHistory().stream().anyMatch(PaymentCode::isLate)
                    && !a.paymentHistory().getFirst().isLate()
                    && a.status().endsWith("Was past due")), context + ": older late streak");
            assertTrue(accounts.stream().anyMatch(a -> a.isOpen()
                    && a.paymentHistory().getFirst() == PaymentCode.LATE_30
                    && a.status().equals("Open/Past due 30 days")), context + ": currently past due");
            assertTrue(accounts.stream().anyMatch(a -> !a.isOpen()
                    && !a.dateClosed().isBefore(REPORT_DATE.minusMonths(12))
                    && !a.dateClosed().isAfter(REPORT_DATE.minusMonths(1))), context + ": closed 1-12 months ago");
            assertTrue(accounts.stream().anyMatch(a -> a.isOpen()
                    && !a.dateOpened().isBefore(REPORT_DATE.minusMonths(6))
                    && !a.dateOpened().isAfter(REPORT_DATE.minusMonths(1))), context + ": opened 1-6 months ago");
        }
    }

    @Test
    void closedAccountsAreConsistentAndAccountsAreNewestFirst() {
        for (long seed = 0; seed < 200; seed++) {
            List<Account> accounts = new CreditReportGenerator(seed, REPORT_DATE).generate().accounts();
            for (int i = 1; i < accounts.size(); i++) {
                assertFalse(accounts.get(i - 1).dateOpened().isBefore(accounts.get(i).dateOpened()),
                        "seed " + seed + ": accounts newest first");
            }
            for (Account a : accounts) {
                String context = "seed " + seed + ", " + a.creditorName();
                List<PaymentCode> history = a.paymentHistory();
                for (int m = 0; m < history.size(); m++) {
                    LocalDate month = REPORT_DATE.minusMonths(m + 1L);
                    assertFalse(month.isBefore(a.dateOpened()), context + ": no history before opening");
                    if (!a.isOpen() && month.isAfter(a.dateClosed())) {
                        assertEquals(PaymentCode.NO_DATA, history.get(m), context + ": ND after closing");
                    }
                }
                if (!a.isOpen()) {
                    assertEquals(0, a.balance(), context);
                    assertEquals(0, a.monthlyPayment(), context);
                    assertEquals(0, a.recentPayment(), context);
                    assertTrue(a.status().startsWith("Closed") || a.status().startsWith("Paid, Closed"), context);
                    assertNotNull(a.onRecordUntil(), context + ": closed accounts have an on-record-until date");
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
