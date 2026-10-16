package com.example.testdata.creditreport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Command-line entry point that generates a dummy credit report and saves it as Markdown.
 *
 * <pre>
 * java -cp target/classes com.example.testdata.creditreport.CreditReportCreator [outputDir] [seed]
 * </pre>
 *
 * <p>{@code outputDir} defaults to {@code test-data/credit-reports}; {@code seed} defaults to a
 * random value. The seed is printed so a report can be regenerated exactly (on the same day).
 */
public class CreditReportCreator {

    static final Path DEFAULT_OUTPUT_DIR = Path.of("test-data", "credit-reports");

    public static void main(String[] args) throws IOException {
        Path outputDir = args.length > 0 ? Path.of(args[0]) : DEFAULT_OUTPUT_DIR;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : System.nanoTime();

        Path file = create(outputDir, seed, LocalDate.now());
        System.out.println("Wrote credit report (seed " + seed + ") to " + file.toAbsolutePath());
    }

    /** Generates a report and writes it to {@code outputDir/credit-report-<reportNumber>.md}. */
    static Path create(Path outputDir, long seed, LocalDate reportDate) throws IOException {
        CreditReport report = new CreditReportGenerator(seed, reportDate).generate();
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("credit-report-" + report.reportNumber() + ".md");
        Files.writeString(file, new MarkdownCreditReportWriter().write(report));
        return file;
    }
}
