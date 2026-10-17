package com.example.testdata.creditreport;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Command-line tool that lists, by name, the generated credit reports that have a recently
 * closed account: one closed in the 12 months up to and including the report's date generated.
 *
 * <pre>
 * java -cp target/classes com.example.testdata.creditreport.RecentlyClosedAccountAlerts [reportsDir]
 * </pre>
 *
 * <p>{@code reportsDir} defaults to {@code test-data/credit-reports}. Only the Markdown files
 * written by {@link MarkdownCreditReportWriter} directly in that directory are read. The row
 * labels and date format come from that writer, so the two stay in step.
 */
public class RecentlyClosedAccountAlerts {

    static final int WINDOW_MONTHS = 12;
    static final String NONE_FOUND = "No credit reports with a recently closed account.";

    private static final Pattern PREPARED_FOR = headerField(MarkdownCreditReportWriter.PREPARED_FOR);
    private static final Pattern DATE_GENERATED = headerField(MarkdownCreditReportWriter.DATE_GENERATED);
    private static final Pattern ACCOUNTS_HEADING =
            Pattern.compile("^" + Pattern.quote(MarkdownCreditReportWriter.ACCOUNTS_HEADING) + "$", Pattern.MULTILINE);
    private static final Pattern NEXT_SECTION = Pattern.compile("^## ", Pattern.MULTILINE);
    private static final Pattern DATE_CLOSED = Pattern.compile(
            "^\\| " + Pattern.quote(MarkdownCreditReportWriter.DATE_CLOSED) + " \\| (.+?) \\|$", Pattern.MULTILINE);

    public static void main(String[] args) {
        Path dir = args.length > 0 ? Path.of(args[0]) : CreditReportCreator.DEFAULT_OUTPUT_DIR;
        if (!Files.exists(dir)) {
            fail("Credit report directory not found: " + dir.toAbsolutePath());
        }
        if (!Files.isDirectory(dir)) {
            fail("Not a directory: " + dir.toAbsolutePath());
        }

        List<String> names;
        try {
            names = find(dir, System.err);
        } catch (IOException e) {
            fail("Cannot read credit report directory " + dir.toAbsolutePath() + ": " + e);
            return;
        }
        if (names.isEmpty()) {
            System.out.println(NONE_FOUND);
        } else {
            names.forEach(System.out::println);
        }
    }

    /**
     * Returns the "Prepared for" name of each report in {@code dir} with a recently closed account,
     * in file-name order. Files that cannot be read, or have no name or valid report date, are
     * reported to {@code warnings} and skipped.
     *
     * @throws IOException if the directory itself cannot be listed
     */
    static List<String> find(Path dir, PrintStream warnings) throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(dir)) {
            files = listing.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        }

        List<String> names = new ArrayList<>();
        for (Path file : files) {
            String markdown;
            try {
                markdown = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                warnings.println("Skipping " + file.getFileName() + ": cannot read file (" + e + ")");
                continue;
            }
            Matcher name = PREPARED_FOR.matcher(markdown);
            LocalDate reportDate = firstDate(DATE_GENERATED, markdown);
            if (!name.find() || reportDate == null) {
                warnings.println("Skipping " + file.getFileName() + ": not a credit report (no name or valid report date)");
                continue;
            }
            if (hasRecentlyClosedAccount(reportDate, closedDates(markdown))) {
                names.add(name.group(1).trim());
            }
        }
        return names;
    }

    /** True if any closed date is on or before {@code reportDate} and at most 12 months before it. */
    static boolean hasRecentlyClosedAccount(LocalDate reportDate, List<LocalDate> closedDates) {
        LocalDate windowStart = reportDate.minusMonths(WINDOW_MONTHS);
        return closedDates.stream().anyMatch(d -> !d.isBefore(windowStart) && !d.isAfter(reportDate));
    }

    /** Valid closed dates in the Accounts section; rows in other sections are ignored. */
    private static List<LocalDate> closedDates(String markdown) {
        List<LocalDate> dates = new ArrayList<>();
        Matcher m = DATE_CLOSED.matcher(accountsSection(markdown));
        while (m.find()) {
            LocalDate date = parse(m.group(1));
            if (date != null) dates.add(date);
        }
        return dates;
    }

    /** Text from the Accounts heading to the next {@code ## } heading, or empty if there is none. */
    private static String accountsSection(String markdown) {
        Matcher heading = ACCOUNTS_HEADING.matcher(markdown);
        if (!heading.find()) {
            return "";
        }
        Matcher next = NEXT_SECTION.matcher(markdown);
        int end = next.find(heading.end()) ? next.start() : markdown.length();
        return markdown.substring(heading.end(), end);
    }

    /** Matches a {@code | **label** | value |} row of the report's header table; group 1 is the value. */
    private static Pattern headerField(String label) {
        return Pattern.compile("^\\| \\*\\*" + Pattern.quote(label) + "\\*\\* \\| (.+?) \\|$", Pattern.MULTILINE);
    }

    private static LocalDate firstDate(Pattern pattern, String markdown) {
        Matcher m = pattern.matcher(markdown);
        return m.find() ? parse(m.group(1)) : null;
    }

    private static LocalDate parse(String date) {
        try {
            return LocalDate.parse(date.trim(), MarkdownCreditReportWriter.DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
