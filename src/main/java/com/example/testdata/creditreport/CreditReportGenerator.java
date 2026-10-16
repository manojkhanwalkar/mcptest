package com.example.testdata.creditreport;

import com.example.testdata.creditreport.CreditReport.Account;
import com.example.testdata.creditreport.CreditReport.AccountType;
import com.example.testdata.creditreport.CreditReport.Address;
import com.example.testdata.creditreport.CreditReport.Creditor;
import com.example.testdata.creditreport.CreditReport.Employer;
import com.example.testdata.creditreport.CreditReport.Inquiry;
import com.example.testdata.creditreport.CreditReport.PaymentCode;
import com.example.testdata.creditreport.CreditReport.PersonalInformation;
import com.example.testdata.creditreport.CreditReport.PublicRecord;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Generates fictitious {@link CreditReport}s for testing.
 *
 * <p>The same seed and report date always produce the same report. Generated data is kept
 * clearly fake: creditor names are invented, phone numbers use the reserved 555-01xx range,
 * and SSNs are masked and use the never-issued 9xx area number.
 */
public class CreditReportGenerator {

    /** Payment-history months shown per account, matching Experian's 24-month grid. */
    static final int HISTORY_MONTHS = 24;

    private static final String[] FIRST_NAMES = {
            "James", "Maria", "Robert", "Linda", "Michael", "Priya", "David", "Aisha",
            "Daniel", "Elena", "Kevin", "Grace", "Samuel", "Nora", "Thomas", "Mei"};
    private static final String[] MIDDLE_INITIALS = {"A", "B", "C", "D", "J", "K", "L", "M", "R", "S"};
    private static final String[] LAST_NAMES = {
            "Testerson", "Sampleton", "Doe", "Placeholder", "Mockridge", "Fakeworth",
            "Dummond", "Exampley", "Stubbs", "Fixture"};
    private static final String[] STREET_NAMES = {
            "Maple", "Oak", "Cedar", "Elm", "Pine", "Willow", "Birch", "Lakeview", "Hillcrest", "Sunset"};
    private static final String[] STREET_SUFFIXES = {"St", "Ave", "Blvd", "Dr", "Ln", "Ct", "Way"};
    private static final String[][] CITIES = {
            {"Springfield", "IL", "627"}, {"Riverton", "WY", "825"}, {"Fairview", "TX", "750"},
            {"Greenville", "SC", "296"}, {"Madison", "WI", "537"}, {"Franklin", "TN", "370"},
            {"Clinton", "IA", "527"}, {"Georgetown", "KY", "403"}, {"Salem", "OR", "973"}};
    private static final String[] EMPLOYERS = {
            "Acme Widgets Inc", "Globex Corporation", "Initech LLC", "Umbrella Logistics",
            "Stark Fabrication Co", "Wayne Analytics", "Hooli Services", "Vandelay Industries"};
    private static final String[] BANKS = {
            "Contoso Bank", "Fabrikam Credit Union", "Northwind Financial", "Tailspin Savings",
            "Woodgrove Bank", "Litware National Bank"};
    private static final String[] CARD_ISSUERS = {
            "Contoso Card Services", "Woodgrove Rewards Card", "Proseware Retail Card",
            "Northwind Platinum Card", "Adatum Store Card"};
    private static final String[] AUTO_LENDERS = {"Tailspin Auto Finance", "Fabrikam Motor Credit"};
    private static final String[] MORTGAGE_LENDERS = {"Woodgrove Home Loans", "Litware Mortgage Co"};
    private static final String[] STUDENT_LENDERS = {"Adatum Education Servicing", "Proseware Student Loans"};
    private static final String[] SOFT_INQUIRERS = {
            "Contoso Insurance Agency", "Northwind Card Offers", "Fabrikam Account Review",
            "Woodgrove Preapproval Services"};
    private static final String[] SOFT_REASONS = {
            "Promotional inquiry", "Account review", "Insurance underwriting", "Consumer disclosure"};

    /**
     * A scenario forced onto one account so that every report contains it. {@code NONE} leaves
     * the account fully random.
     */
    private enum Role {
        NONE, PAST_LATE, CURRENTLY_PAST_DUE, RECENTLY_CLOSED, RECENTLY_OPENED;

        /** Roles whose account must still be open on the report date. */
        boolean staysOpen() {
            return this == PAST_LATE || this == CURRENTLY_PAST_DUE || this == RECENTLY_OPENED;
        }
    }

    private final Random random;
    private final LocalDate reportDate;

    public CreditReportGenerator(long seed, LocalDate reportDate) {
        this.random = new Random(seed);
        this.reportDate = reportDate;
    }

    public CreditReport generate() {
        String first = pick(FIRST_NAMES);
        String middle = pick(MIDDLE_INITIALS);
        String last = pick(LAST_NAMES);
        int yearOfBirth = reportDate.getYear() - between(24, 70);
        // The adult credit history can start no earlier than age 18.
        LocalDate historyStart = LocalDate.of(Math.max(yearOfBirth + 18, reportDate.getYear() - 25), 1, 1);

        PersonalInformation personal = personalInformation(first, middle, last, yearOfBirth, historyStart);
        List<Account> accounts = accounts(historyStart);
        List<PublicRecord> publicRecords = random.nextInt(8) == 0 ? List.of(bankruptcy(historyStart)) : List.of();
        List<Inquiry> hardInquiries = inquiries(between(0, 4), true);
        List<Inquiry> softInquiries = inquiries(between(2, 6), false);
        List<String> statements = random.nextInt(4) == 0
                ? List.of("My identity was compromised in a data breach. Please verify my identity "
                + "before extending credit in my name.")
                : List.of();

        String reportNumber = String.format("%04d-%04d-%02d", random.nextInt(10_000), random.nextInt(10_000),
                random.nextInt(100));
        return new CreditReport(reportNumber, reportDate, personal, accounts, publicRecords,
                hardInquiries, softInquiries, statements);
    }

    private PersonalInformation personalInformation(String first, String middle, String last,
                                                    int yearOfBirth, LocalDate historyStart) {
        List<String> names = new ArrayList<>(List.of(
                (first + " " + middle + " " + last).toUpperCase(),
                (first + " " + last).toUpperCase()));
        if (random.nextBoolean()) {
            names.add((first.charAt(0) + " " + last).toUpperCase());
        }

        String lastFour = String.format("%04d", between(1, 9999));
        List<String> ssns = new ArrayList<>(List.of("9XX-XX-" + lastFour));
        if (random.nextInt(5) == 0) {
            ssns.add("9XX-XX-" + String.format("%04d", between(1, 9999)));  // a mis-keyed variation
        }

        List<Address> addresses = new ArrayList<>();
        int addressCount = between(1, 4);
        LocalDate firstReported = reportDate.minusYears(between(1, 4)).minusDays(between(0, 300));
        for (int i = 0; i < addressCount; i++) {
            String[] city = pick(CITIES);
            addresses.add(new Address(
                    between(100, 9999) + " " + pick(STREET_NAMES) + " " + pick(STREET_SUFFIXES),
                    city[0], city[1], city[2] + String.format("%02d", between(0, 99)),
                    i == 0 ? "Single family" : pick(new String[]{"Single family", "Apartment complex", "Multifamily"}),
                    firstReported));
            firstReported = later(historyStart, firstReported.minusYears(between(2, 6)).minusDays(between(0, 300)));
        }

        List<String> phones = new ArrayList<>();
        for (int i = 0, n = between(1, 2); i < n; i++) {
            phones.add(String.format("(%03d) 555-01%02d", between(201, 989), between(0, 99)));
        }

        List<Employer> employers = new ArrayList<>();
        List<String> employerNames = new ArrayList<>(List.of(EMPLOYERS));
        Collections.shuffle(employerNames, random);
        for (String name : employerNames.subList(0, between(1, 3))) {
            employers.add(new Employer(name, randomDate(historyStart, reportDate.minusMonths(3))));
        }
        employers.sort(Comparator.comparing(Employer::firstReported).reversed());

        return new PersonalInformation(names, yearOfBirth, ssns, addresses, phones, employers);
    }

    /**
     * Picks the account types at random, then gives each forced {@link Role} to a different
     * account so every report has late payments and recently closed and opened accounts.
     */
    private List<Account> accounts(LocalDate historyStart) {
        List<AccountType> types = new ArrayList<>();
        for (int i = 0, n = between(2, 5); i < n; i++) {
            types.add(AccountType.CREDIT_CARD);
        }
        if (random.nextInt(4) != 0) types.add(AccountType.AUTO_LOAN);
        if (random.nextBoolean()) types.add(AccountType.MORTGAGE);
        if (random.nextBoolean()) types.add(AccountType.STUDENT_LOAN);
        if (random.nextInt(3) == 0) types.add(AccountType.PERSONAL_LOAN);
        if (random.nextInt(5) == 0) types.add(AccountType.HOME_EQUITY_LINE);

        // The cards come first in the list, so this index is always a credit card.
        int closedCard = random.nextInt((int) types.stream().filter(t -> t == AccountType.CREDIT_CARD).count());
        List<Role> roles = new ArrayList<>(Collections.nCopies(types.size(), Role.NONE));
        roles.set(closedCard, Role.RECENTLY_CLOSED);

        List<Role> otherRoles = List.of(Role.RECENTLY_OPENED, Role.CURRENTLY_PAST_DUE, Role.PAST_LATE);
        while (types.size() < otherRoles.size() + 1) {
            types.add(AccountType.CREDIT_CARD);
            roles.add(Role.NONE);
        }
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            if (i != closedCard) candidates.add(i);
        }
        Collections.shuffle(candidates, random);
        for (int i = 0; i < otherRoles.size(); i++) {
            roles.set(candidates.get(i), otherRoles.get(i));
        }

        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            accounts.add(account(types.get(i), historyStart, roles.get(i)));
        }
        accounts.sort(Comparator.comparing(Account::dateOpened).reversed());
        return accounts;
    }

    private Account account(AccountType type, LocalDate historyStart, Role role) {
        LocalDate opened = role == Role.RECENTLY_OPENED
                ? randomDate(reportDate.minusMonths(6), reportDate.minusMonths(1))
                : randomDate(historyStart, reportDate.minusMonths(4));
        int termMonths = switch (type) {
            case AUTO_LOAN -> pick(new Integer[]{48, 60, 72});
            case MORTGAGE -> pick(new Integer[]{180, 360});
            case STUDENT_LOAN -> 120;
            case PERSONAL_LOAN -> pick(new Integer[]{24, 36, 60});
            case CREDIT_CARD, HOME_EQUITY_LINE -> 0;
        };
        if (role.staysOpen() && termMonths > 0 && ChronoUnit.MONTHS.between(opened, reportDate) >= termMonths) {
            // Keep the loan within its term so it is still open.
            opened = randomDate(later(historyStart, reportDate.minusMonths(termMonths - 1)), reportDate.minusMonths(4));
        }
        long amount = switch (type) {
            case CREDIT_CARD -> roundTo(between(500, 25_000), 100);
            case AUTO_LOAN -> roundTo(between(12_000, 55_000), 100);
            case MORTGAGE -> roundTo(between(120_000, 650_000), 1_000);
            case STUDENT_LOAN -> roundTo(between(8_000, 90_000), 100);
            case PERSONAL_LOAN -> roundTo(between(2_000, 30_000), 100);
            case HOME_EQUITY_LINE -> roundTo(between(20_000, 150_000), 1_000);
        };

        long monthsOpen = ChronoUnit.MONTHS.between(opened, reportDate);
        // Installment loans past their term are paid off; some cards are closed by the consumer.
        LocalDate closed = null;
        if (role == Role.RECENTLY_CLOSED) {
            closed = randomDate(later(opened.plusMonths(3), reportDate.minusMonths(12)), reportDate.minusMonths(1));
        } else if (termMonths > 0 && monthsOpen >= termMonths) {
            closed = opened.plusMonths(termMonths);
        } else if (!role.staysOpen() && type.isRevolving() && random.nextInt(6) == 0) {
            closed = randomDate(opened.plusMonths(3), reportDate.minusMonths(1));
        }

        long balance;
        long monthlyPayment;
        if (closed != null) {
            balance = 0;
            monthlyPayment = 0;
        } else if (type.isRevolving()) {
            balance = Math.round(amount * random.nextDouble() * 0.6);
            if (role == Role.CURRENTLY_PAST_DUE) {
                balance = Math.max(balance, Math.round(amount * 0.1));  // a past-due account owes money
            }
            monthlyPayment = balance == 0 ? 0 : Math.max(25, Math.round(balance * 0.03));
        } else {
            monthlyPayment = amortizedPayment(amount, type == AccountType.MORTGAGE ? 0.065 : 0.08, termMonths);
            balance = Math.max(0, amount - Math.round(monthlyPayment * monthsOpen * 0.7));
        }
        long highest = type.isRevolving() ? Math.max(balance, Math.round(amount * (0.3 + random.nextDouble() * 0.7))) : amount;

        // Most accounts are clean; about one in five has a late streak.
        int lateStart = switch (role) {
            case CURRENTLY_PAST_DUE -> 0;
            case RECENTLY_OPENED -> -1;
            // Start at least 2 months back, within the months the account has existed.
            case PAST_LATE -> between(2, Math.max(2, Math.min(HISTORY_MONTHS - 3, historyLength(opened) - 1)));
            case NONE, RECENTLY_CLOSED -> random.nextInt(5) == 0 ? between(2, HISTORY_MONTHS - 3) : -1;
        };
        int lateLength = role == Role.CURRENTLY_PAST_DUE ? 1 : between(1, 3);
        List<PaymentCode> history = paymentHistory(opened, closed, lateStart, lateLength);
        String status = status(closed, history, type);
        String creditor = switch (type) {
            case CREDIT_CARD -> pick(CARD_ISSUERS);
            case AUTO_LOAN -> pick(AUTO_LENDERS);
            case MORTGAGE -> pick(MORTGAGE_LENDERS);
            case STUDENT_LOAN -> pick(STUDENT_LENDERS);
            case PERSONAL_LOAN, HOME_EQUITY_LINE -> pick(BANKS);
        };
        String[] city = pick(CITIES);
        Creditor contact = new Creditor(creditor.toUpperCase(),
                "PO Box " + between(1000, 99999) + ", " + city[0] + ", " + city[1] + " " + city[2] + "00",
                String.format("(800) 555-01%02d", between(0, 99)));

        LocalDate lastActivity = closed != null ? closed : reportDate.minusDays(between(3, 25));
        boolean everLate = history.stream().anyMatch(PaymentCode::isLate);
        // Negative accounts stay 7 years from first delinquency; positive closed accounts ~10 years.
        LocalDate onRecordUntil = everLate ? lastActivity.plusYears(7) : closed != null ? closed.plusYears(10) : null;

        return new Account(
                creditor,
                "XXXX-XXXX-XXXX-" + String.format("%04d", between(0, 9999)),
                type,
                random.nextInt(6) == 0 ? "Joint" : "Individual",
                opened,
                closed,
                status,
                lastActivity,
                balance,
                amount,
                highest,
                monthlyPayment,
                closed != null ? 0 : (type.isRevolving() ? Math.round(monthlyPayment * (1 + random.nextDouble() * 3)) : monthlyPayment),
                termMonths == 0 ? "Revolving" : termMonths + " months",
                onRecordUntil,
                history,
                contact);
    }

    /** Number of payment-history months shown for an account opened on {@code opened}. */
    private int historyLength(LocalDate opened) {
        int months = 0;
        while (months < HISTORY_MONTHS && !reportDate.minusMonths(months + 1L).isBefore(opened)) {
            months++;
        }
        return months;
    }

    /**
     * Most recent month first. {@code lateStart} is how many months back the late streak begins
     * (0 = the most recent month), or -1 for no streak.
     */
    private List<PaymentCode> paymentHistory(LocalDate opened, LocalDate closed, int lateStart, int lateLength) {
        List<PaymentCode> history = new ArrayList<>();
        for (int m = 0; m < HISTORY_MONTHS; m++) {
            LocalDate month = reportDate.minusMonths(m + 1L);
            if (month.isBefore(opened)) {
                break;
            }
            if (closed != null && month.isAfter(closed)) {
                history.add(PaymentCode.NO_DATA);
            } else if (lateStart >= 0 && m >= lateStart && m < lateStart + lateLength) {
                // Within a streak, delinquency deepens going forward in time (i.e. towards lower m).
                int depth = lateStart + lateLength - 1 - m;
                history.add(switch (depth) {
                    case 0 -> PaymentCode.LATE_30;
                    case 1 -> PaymentCode.LATE_60;
                    default -> PaymentCode.LATE_90;
                });
            } else {
                history.add(PaymentCode.OK);
            }
        }
        return history;
    }

    private static String status(LocalDate closed, List<PaymentCode> history, AccountType type) {
        boolean everLate = history.stream().anyMatch(PaymentCode::isLate);
        String suffix = everLate ? "Was past due" : "Never late";
        if (closed != null) {
            return (type.isRevolving() ? "Closed" : "Paid, Closed") + "/" + suffix;
        }
        if (!history.isEmpty() && history.getFirst().isLate()) {
            return "Open/Past due " + history.getFirst().code() + " days";
        }
        return "Open/" + suffix;
    }

    private PublicRecord bankruptcy(LocalDate historyStart) {
        LocalDate filed = randomDate(later(historyStart, reportDate.minusYears(9)), reportDate.minusYears(1));
        boolean chapter7 = random.nextBoolean();
        LocalDate resolved = filed.plusMonths(chapter7 ? 4 : 60);
        String[] city = pick(CITIES);
        return new PublicRecord(
                chapter7 ? "Chapter 7 bankruptcy" : "Chapter 13 bankruptcy",
                "US Bankruptcy Court, " + city[0] + ", " + city[1],
                String.format("%02d-%05d", filed.getYear() % 100, between(10000, 99999)),
                filed,
                resolved.isAfter(reportDate) ? null : resolved,
                resolved.isAfter(reportDate) ? "Petitioned" : "Discharged",
                filed.plusYears(chapter7 ? 10 : 7));
    }

    private List<Inquiry> inquiries(int count, boolean hard) {
        List<Inquiry> inquiries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate date = randomDate(reportDate.minusMonths(hard ? 24 : 12), reportDate.minusDays(1));
            inquiries.add(hard
                    ? new Inquiry(pick(random.nextBoolean() ? CARD_ISSUERS : BANKS), date, "Application for credit",
                    date.plusYears(2))
                    : new Inquiry(pick(SOFT_INQUIRERS), date, pick(SOFT_REASONS), null));
        }
        inquiries.sort(Comparator.comparing(Inquiry::date).reversed());
        return inquiries;
    }

    private static long amortizedPayment(long principal, double annualRate, int months) {
        double r = annualRate / 12;
        return Math.round(principal * r / (1 - Math.pow(1 + r, -months)));
    }

    private static long roundTo(long value, long unit) {
        return Math.round((double) value / unit) * unit;
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDate randomDate(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        return days <= 0 ? from : from.plusDays(random.nextLong(days + 1));
    }

    /** Inclusive on both ends. */
    private int between(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private <T> T pick(T[] values) {
        return values[random.nextInt(values.length)];
    }
}
