package com.example.mcp.creditreport.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads credit reports from a directory on the file system. It knows nothing about MCP;
 * {@link CreditReportTools} maps tool calls onto it.
 *
 * <p>Reports are the Markdown files written by the api-server's dummy credit report generator,
 * named {@code credit-report-<reportNumber>.md} where the report number looks like
 * {@code 2416-0737-69}. Only files matching that name are served, and a report number must match
 * that pattern before it is turned into a path, so callers cannot read anything else in (or
 * outside) the directory.
 */
public class CreditReportStore {

    /** Report number format used by the generator: {@code dddd-dddd-dd}. */
    public static final Pattern REPORT_NUMBER = Pattern.compile("\\d{4}-\\d{4}-\\d{2}");

    private static final String FILE_PREFIX = "credit-report-";
    private static final String FILE_SUFFIX = ".md";
    private static final Pattern FILE_NAME = Pattern.compile(
            Pattern.quote(FILE_PREFIX) + "(" + REPORT_NUMBER.pattern() + ")" + Pattern.quote(FILE_SUFFIX));
    private static final Pattern PREPARED_FOR = headerField("Prepared for");
    private static final Pattern DATE_GENERATED = headerField("Date generated");

    /** One report in the directory listing. {@code preparedFor} and {@code dateGenerated} may be null. */
    public record ReportSummary(String reportNumber, String preparedFor, String dateGenerated,
                                String fileName, long sizeBytes) {
    }

    /** Thrown when no report with the requested number exists. */
    public static class ReportNotFoundException extends Exception {
        public ReportNotFoundException(String reportNumber) {
            super("No credit report with number " + reportNumber);
        }
    }

    private final Path directory;

    public CreditReportStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Path directory() {
        return directory;
    }

    /** All reports in the directory, sorted by report number. */
    public List<ReportSummary> list() throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new NoSuchFileException(directory.toString(), null, "credit report directory does not exist");
        }
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> reports = files
                    .filter(Files::isRegularFile)
                    .filter(f -> FILE_NAME.matcher(f.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(f -> f.getFileName().toString()))
                    .toList();
            List<ReportSummary> summaries = new ArrayList<>();
            for (Path file : reports) {
                summaries.add(summarize(file));
            }
            return summaries;
        }
    }

    /**
     * Full Markdown text of one report.
     *
     * @throws IllegalArgumentException if {@code reportNumber} is not in {@code dddd-dddd-dd} format
     * @throws ReportNotFoundException  if there is no such report
     */
    public String read(String reportNumber) throws IOException, ReportNotFoundException {
        if (reportNumber == null || !REPORT_NUMBER.matcher(reportNumber).matches()) {
            throw new IllegalArgumentException("Report number must look like 1234-5678-90, got: " + reportNumber);
        }
        Path file = directory.resolve(FILE_PREFIX + reportNumber + FILE_SUFFIX);
        if (!Files.isRegularFile(file)) {
            throw new ReportNotFoundException(reportNumber);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static ReportSummary summarize(Path file) throws IOException {
        Matcher name = FILE_NAME.matcher(file.getFileName().toString());
        name.matches();
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return new ReportSummary(name.group(1),
                firstGroup(PREPARED_FOR, text).orElse(null),
                firstGroup(DATE_GENERATED, text).orElse(null),
                file.getFileName().toString(),
                Files.size(file));
    }

    /** Matches a {@code | **label** | value |} row of the report's header table; group 1 is the value. */
    private static Pattern headerField(String label) {
        return Pattern.compile("^\\| \\*\\*" + Pattern.quote(label) + "\\*\\* \\| (.+?) \\|$", Pattern.MULTILINE);
    }

    private static Optional<String> firstGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }
}
