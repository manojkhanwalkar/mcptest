package com.example.testdata.creditreport;

import java.time.LocalDate;
import java.util.List;

/**
 * A consumer credit report, modelled on the sections of the Experian consumer credit report
 * that consumers receive (for example from annualcreditreport.com): personal information,
 * accounts with a 24-month payment history, public records, hard and soft inquiries, and
 * personal statements.
 *
 * <p>All data is fictitious test data produced by {@link CreditReportGenerator}.
 */
public record CreditReport(
        String reportNumber,
        LocalDate reportDate,
        PersonalInformation personalInformation,
        List<Account> accounts,
        List<PublicRecord> publicRecords,
        List<Inquiry> hardInquiries,
        List<Inquiry> softInquiries,
        List<String> personalStatements) {

    /** Identity details the bureau has on file, including name and address variations. */
    public record PersonalInformation(
            List<String> names,
            int yearOfBirth,
            List<String> ssnVariations,
            List<Address> addresses,
            List<String> phoneNumbers,
            List<Employer> employers) {
    }

    public record Address(String street, String city, String state, String zip,
                          String type, LocalDate firstReported) {
    }

    public record Employer(String name, LocalDate firstReported) {
    }

    /**
     * One tradeline. {@code paymentHistory} holds the most recent month first; each entry is a
     * code from {@link PaymentCode}. Months before the account opened are omitted.
     */
    public record Account(
            String creditorName,
            String accountNumberMasked,
            AccountType type,
            String responsibility,
            LocalDate dateOpened,
            LocalDate dateClosed,
            String status,
            LocalDate statusUpdated,
            long balance,
            long creditLimitOrOriginalAmount,
            long highestBalance,
            long monthlyPayment,
            long recentPayment,
            String terms,
            LocalDate onRecordUntil,
            List<PaymentCode> paymentHistory,
            Creditor creditorContact) {

        public boolean isOpen() {
            return dateClosed == null;
        }
    }

    public enum AccountType {
        CREDIT_CARD("Credit card", true),
        AUTO_LOAN("Auto loan", false),
        MORTGAGE("Mortgage", false),
        STUDENT_LOAN("Student loan", false),
        PERSONAL_LOAN("Personal loan", false),
        HOME_EQUITY_LINE("Home equity line of credit", true);

        private final String label;
        private final boolean revolving;

        AccountType(String label, boolean revolving) {
            this.label = label;
            this.revolving = revolving;
        }

        public String label() {
            return label;
        }

        /** Revolving accounts report a credit limit and count towards utilization. */
        public boolean isRevolving() {
            return revolving;
        }
    }

    /** Monthly payment-history codes, as shown in the Experian payment-history grid. */
    public enum PaymentCode {
        OK("OK", "Current / paid as agreed"),
        LATE_30("30", "30 days past due"),
        LATE_60("60", "60 days past due"),
        LATE_90("90", "90 days past due"),
        CHARGE_OFF("CO", "Charged off"),
        NO_DATA("ND", "No data for this month");

        private final String code;
        private final String description;

        PaymentCode(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String code() {
            return code;
        }

        public String description() {
            return description;
        }

        public boolean isLate() {
            return this == LATE_30 || this == LATE_60 || this == LATE_90 || this == CHARGE_OFF;
        }
    }

    public record Creditor(String name, String address, String phone) {
    }

    /** Since 2018 Experian reports only bankruptcies as public records. */
    public record PublicRecord(String type, String court, String referenceNumber,
                               LocalDate dateFiled, LocalDate dateResolved, String status,
                               LocalDate onRecordUntil) {
    }

    /** A request for the consumer's credit report; {@code reason} is shown for soft inquiries. */
    public record Inquiry(String company, LocalDate date, String reason, LocalDate onRecordUntil) {
    }
}
