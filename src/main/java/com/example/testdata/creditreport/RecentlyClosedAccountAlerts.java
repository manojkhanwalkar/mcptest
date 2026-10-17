package com.example.testdata.creditreport;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * written by {@link MarkdownCreditReportWriter} directly in that directory are read.
 */
public class RecentlyClosedAccountAlerts {

    static final int WINDOW_MONTHS = 12;
    static final String NONE_FOUND = "No credit reports with a recently closed account.";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final Pattern PREPARED_FOR = Pattern.compile("(?m)^\\| \\*\\*Prepared for\\*\\* \\| (.+?) \\|$");
    private static final Pattern DATE_GENERATED =
            Pattern.compile("(?m)^\\| \\*\\*Date generated\\*\\* \\| (\\d{2}/\\d{2}/\\d{4}) \\|$");
    private static final Pattern DATE_CLOSED = Pattern.compile("(?m)^\\| Date closed \\| (\\d{2}/\\d{2}/\\d{4}) \\|$");

    public static void main(String[] args) throws IOException {
        Path dir = args.length > 0 ? Path.of(args[0]) : CreditReportCreator.DEFAULT_OUTPUT_DIR;
        if (!Files.isDirectory(dir)) {
            System.err.println("Credit report directory not found: " + dir.toAbsolutePath());
            System.exit(1);
        }

        List<String> names = find(dir, System.err);
        if (names.isEmpty()) {
            System.out.println(NONE_FOUND);
        } else {
            names.forEach(System.out::println);
        }
    }

    /**
     * Returns the "Prepared for" name of each report in {@code dir} with a recently closed account,
     * in file-name order. Files without a name or report date are reported to {@code warnings}.
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
            String markdown = Files.readString(file);
            Matcher name = PREPARED_FOR.matcher(markdown);
            LocalDate reportDate = firstDate(DATE_GENERATED, markdown);
            if (!name.find() || reportDate == null) {
                warnings.println("Skipping " + file.getFileName() + ": not a credit report (no name or report date)");
                continue;
            }
            if (hasRecentlyClosedAccount(reportDate, closedDates(markdown))) {
                names.add(name.group(1));
            }
        }
        return names;
    }

    /** True if any closed date is on or before {@code reportDate} and at most 12 months before it. */
    static boolean hasRecentlyClosedAccount(LocalDate reportDate, List<LocalDate> closedDates) {
        LocalDate windowStart = reportDate.minusMonths(WINDOW_MONTHS);
        return closedDates.stream().anyMatch(d -> !d.isBefore(windowStart) && !d.isAfter(reportDate));
    }

    private static List<LocalDate> closedDates(String markdown) {
        List<LocalDate> dates = new ArrayList<>();
        Matcher m = DATE_CLOSED.matcher(markdown);
        while (m.find()) {
            LocalDate date = parse(m.group(1));
            if (date != null) dates.add(date);
        }
        return dates;
    }

    private static LocalDate firstDate(Pattern pattern, String markdown) {
        Matcher m = pattern.matcher(markdown);
        return m.find() ? parse(m.group(1)) : null;
    }

    private static LocalDate parse(String date) {
        try {
            return LocalDate.parse(date, DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
