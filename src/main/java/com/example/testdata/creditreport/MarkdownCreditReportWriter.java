package com.example.testdata.creditreport;

import com.example.testdata.creditreport.CreditReport.Account;
import com.example.testdata.creditreport.CreditReport.Address;
import com.example.testdata.creditreport.CreditReport.Employer;
import com.example.testdata.creditreport.CreditReport.Inquiry;
import com.example.testdata.creditreport.CreditReport.PaymentCode;
import com.example.testdata.creditreport.CreditReport.PersonalInformation;
import com.example.testdata.creditreport.CreditReport.PublicRecord;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders a {@link CreditReport} as Markdown, following the section order and field names of
 * the Experian consumer credit report: At a glance, Personal information, Accounts, Public
 * records, Inquiries, Personal statements, and Contact/dispute information.
 */
public class MarkdownCreditReportWriter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.US);
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.US);

    static {
        MONEY.setMaximumFractionDigits(0);
    }

    public String write(CreditReport report) {
        StringBuilder md = new StringBuilder();
        PersonalInformation person = report.personalInformation();

        md.append("# Credit Report (Experian consumer report format)\n\n");
        md.append("> **TEST DATA — FICTITIOUS.** This report was machine-generated for software testing. ")
                .append("It does not describe a real person and is not issued by Experian.\n\n");
        md.append("| | |\n|---|---|\n");
        md.append("| **Prepared for** | ").append(person.names().getFirst()).append(" |\n");
        md.append("| **Date generated** | ").append(date(report.reportDate())).append(" |\n");
        md.append("| **Report number** | ").append(report.reportNumber()).append(" |\n\n");

        atAGlance(md, report);
        personalInformation(md, person);
        accounts(md, report.accounts(), report.reportDate());
        publicRecords(md, report.publicRecords());
        inquiries(md, report.hardInquiries(), report.softInquiries());
        personalStatements(md, report.personalStatements());
        contact(md, report.reportNumber());
        return md.toString();
    }

    private static void atAGlance(StringBuilder md, CreditReport report) {
        List<Account> accounts = report.accounts();
        long open = accounts.stream().filter(Account::isOpen).count();
        long late = accounts.stream().filter(a -> a.paymentHistory().stream().anyMatch(PaymentCode::isLate)).count();
        long revolvingBalance = accounts.stream().filter(a -> a.isOpen() && a.type().isRevolving())
                .mapToLong(Account::balance).sum();
        long revolvingLimit = accounts.stream().filter(a -> a.isOpen() && a.type().isRevolving())
                .mapToLong(Account::creditLimitOrOriginalAmount).sum();
        long totalBalance = accounts.stream().mapToLong(Account::balance).sum();
        long monthly = accounts.stream().mapToLong(Account::monthlyPayment).sum();
        LocalDate oldest = accounts.stream().map(Account::dateOpened).min(LocalDate::compareTo).orElse(null);

        md.append("## At a glance\n\n");
        md.append("| Summary | |\n|---|---|\n");
        md.append("| Payment history | ").append(late == 0 ? "All accounts current — no late payments"
                : late + " account(s) with late payments").append(" |\n");
        md.append("| Credit card and credit line utilization | ")
                .append(revolvingLimit == 0 ? "N/A" : Math.round(100.0 * revolvingBalance / revolvingLimit) + "%")
                .append(" (").append(money(revolvingBalance)).append(" of ").append(money(revolvingLimit)).append(") |\n");
        md.append("| Total accounts | ").append(accounts.size()).append(" (")
                .append(open).append(" open, ").append(accounts.size() - open).append(" closed) |\n");
        md.append("| Total debt | ").append(money(totalBalance)).append(" |\n");
        md.append("| Total monthly payments | ").append(money(monthly)).append(" |\n");
        md.append("| Age of oldest account | ").append(oldest == null ? "N/A" : date(oldest)).append(" |\n");
        md.append("| Hard inquiries (last 2 years) | ").append(report.hardInquiries().size()).append(" |\n");
        md.append("| Public records | ").append(report.publicRecords().size()).append(" |\n\n");

        md.append("### Account summary by type\n\n");
        md.append("| Account type | Count | Balance | Monthly payment |\n|---|---:|---:|---:|\n");
        for (CreditReport.AccountType type : CreditReport.AccountType.values()) {
            List<Account> ofType = accounts.stream().filter(a -> a.type() == type).toList();
            if (!ofType.isEmpty()) {
                md.append("| ").append(type.label()).append(" | ").append(ofType.size()).append(" | ")
                        .append(money(ofType.stream().mapToLong(Account::balance).sum())).append(" | ")
                        .append(money(ofType.stream().mapToLong(Account::monthlyPayment).sum())).append(" |\n");
            }
        }
        md.append('\n');
    }

    private static void personalInformation(StringBuilder md, PersonalInformation person) {
        md.append("## Personal information\n\n");
        md.append("The following information is reported to us by you, your creditors and other sources. ")
                .append("Each source may report your personal information differently, which may result in ")
                .append("variations of your name, address, Social Security number, etc.\n\n");

        md.append("### Names\n\n");
        person.names().forEach(n -> md.append("- ").append(n).append('\n'));
        md.append("\n### Year of birth\n\n").append(person.yearOfBirth()).append("\n\n");
        md.append("### Social Security number variations\n\n");
        person.ssnVariations().forEach(s -> md.append("- ").append(s).append('\n'));

        md.append("\n### Addresses\n\n| Address | Type of residence | First reported |\n|---|---|---|\n");
        for (Address a : person.addresses()) {
            md.append("| ").append(a.street()).append(", ").append(a.city()).append(", ").append(a.state())
                    .append(' ').append(a.zip()).append(" | ").append(a.type()).append(" | ")
                    .append(date(a.firstReported())).append(" |\n");
        }

        md.append("\n### Phone numbers\n\n");
        person.phoneNumbers().forEach(p -> md.append("- ").append(p).append('\n'));
        md.append("\n### Employers\n\n| Employer | First reported |\n|---|---|\n");
        for (Employer e : person.employers()) {
            md.append("| ").append(e.name()).append(" | ").append(date(e.firstReported())).append(" |\n");
        }
        md.append('\n');
    }

    private static void accounts(StringBuilder md, List<Account> accounts, LocalDate reportDate) {
        md.append("## Accounts\n\n");
        int n = 1;
        for (Account a : accounts) {
            md.append("### ").append(n++).append(". ").append(a.creditorName()).append(" — ")
                    .append(a.type().label()).append("\n\n");
            md.append("| Account info | |\n|---|---|\n");
            row(md, "Account name", a.creditorName().toUpperCase());
            row(md, "Account number", a.accountNumberMasked());
            row(md, "Account type", a.type().label());
            row(md, "Responsibility", a.responsibility());
            row(md, "Date opened", date(a.dateOpened()));
            row(md, "Date closed", a.dateClosed() == null ? "—" : date(a.dateClosed()));
            row(md, "Status", "**" + a.status() + "**");
            row(md, "Status updated", date(a.statusUpdated()));
            row(md, "Balance", money(a.balance()));
            row(md, "Balance updated", date(a.statusUpdated()));
            row(md, "Recent payment", money(a.recentPayment()));
            row(md, "Monthly payment", money(a.monthlyPayment()));
            row(md, a.type().isRevolving() ? "Credit limit" : "Original balance", money(a.creditLimitOrOriginalAmount()));
            row(md, "Highest balance", money(a.highestBalance()));
            row(md, "Terms", a.terms());
            row(md, "On record until", a.onRecordUntil() == null ? "—" : date(a.onRecordUntil()));
            md.append('\n');

            paymentHistory(md, a, reportDate);

            md.append("**Contact info:** ").append(a.creditorContact().name()).append(", ")
                    .append(a.creditorContact().address()).append(", ").append(a.creditorContact().phone())
                    .append("\n\n---\n\n");
        }
    }

    /**
     * Renders the history as month-by-month grids of up to 12 months, oldest month on the left.
     * The newest entry is the month before the report date.
     */
    private static void paymentHistory(StringBuilder md, Account a, LocalDate reportDate) {
        List<PaymentCode> history = a.paymentHistory().reversed();
        if (history.isEmpty()) {
            md.append("**Payment history:** No payment history reported yet.\n\n");
            return;
        }
        int size = history.size();
        md.append("**Payment history** (last ").append(size).append(" months)\n\n");
        for (int from = 0; from < size; from += 12) {
            StringBuilder header = new StringBuilder("|");
            StringBuilder divider = new StringBuilder("|");
            StringBuilder codes = new StringBuilder("|");
            for (int i = from; i < Math.min(from + 12, size); i++) {
                header.append(' ').append(MONTH.format(reportDate.minusMonths(size - i))).append(" |");
                divider.append(":---:|");
                codes.append(' ').append(history.get(i).code()).append(" |");
            }
            md.append(header).append('\n').append(divider).append('\n').append(codes).append("\n\n");
        }
    }

    private static void publicRecords(StringBuilder md, List<PublicRecord> records) {
        md.append("## Public records\n\n");
        if (records.isEmpty()) {
            md.append("No public records reported.\n\n");
            return;
        }
        for (PublicRecord r : records) {
            md.append("### ").append(r.type()).append("\n\n| | |\n|---|---|\n");
            row(md, "Court", r.court());
            row(md, "Reference number", r.referenceNumber());
            row(md, "Date filed", date(r.dateFiled()));
            row(md, "Date resolved", r.dateResolved() == null ? "—" : date(r.dateResolved()));
            row(md, "Status", r.status());
            row(md, "On record until", date(r.onRecordUntil()));
            md.append('\n');
        }
    }

    private static void inquiries(StringBuilder md, List<Inquiry> hard, List<Inquiry> soft) {
        md.append("## Inquiries\n\n### Hard inquiries\n\n");
        md.append("Hard inquiries are requests for your credit history made when you apply for credit. ")
                .append("They may be seen by others and may affect your credit score.\n\n");
        if (hard.isEmpty()) {
            md.append("No hard inquiries reported.\n\n");
        } else {
            md.append("| Company | Inquired on | On record until |\n|---|---|---|\n");
            for (Inquiry i : hard) {
                md.append("| ").append(i.company()).append(" | ").append(date(i.date())).append(" | ")
                        .append(date(i.onRecordUntil())).append(" |\n");
            }
            md.append('\n');
        }

        md.append("### Soft inquiries\n\n");
        md.append("Soft inquiries are seen only by you and do not affect your credit score.\n\n");
        if (soft.isEmpty()) {
            md.append("No soft inquiries reported.\n\n");
        } else {
            md.append("| Company | Inquired on | Reason |\n|---|---|---|\n");
            for (Inquiry i : soft) {
                md.append("| ").append(i.company()).append(" | ").append(date(i.date())).append(" | ")
                        .append(i.reason()).append(" |\n");
            }
            md.append('\n');
        }
    }

    private static void personalStatements(StringBuilder md, List<String> statements) {
        md.append("## Personal statements\n\n");
        if (statements.isEmpty()) {
            md.append("No personal statements on file.\n\n");
        } else {
            statements.forEach(s -> md.append("> ").append(s).append("\n\n"));
        }
    }

    private static void contact(StringBuilder md, String reportNumber) {
        md.append("## Contact us\n\n");
        md.append("If you believe information in this report is inaccurate or incomplete, you may dispute it ")
                .append("online, by mail or by phone. Please have your report number (**").append(reportNumber)
                .append("**) ready.\n\n");
        md.append("| Payment history legend | |\n|---|---|\n");
        for (PaymentCode code : PaymentCode.values()) {
            row(md, code.code(), code.description());
        }
        md.append('\n');
    }

    private static void row(StringBuilder md, String label, String value) {
        md.append("| ").append(label).append(" | ").append(value).append(" |\n");
    }

    private static String date(LocalDate d) {
        return DATE.format(d);
    }

    private static String money(long amount) {
        return MONEY.format(amount);
    }
}
