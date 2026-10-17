# Design

## Context

See proposal.md for the motivation and specs/credit-report-alerts/spec.md for the behavior. Relevant facts about the existing code:

- `CreditReportCreator` generates a report and writes it with `MarkdownCreditReportWriter` to `test-data/credit-reports/credit-report-<reportNumber>.md` (`CreditReportCreator.DEFAULT_OUTPUT_DIR`). The Markdown file is the only thing saved; the `CreditReport` record is not.
- The writer puts the name and date in the header table as `| **Prepared for** | <name> |` and `| **Date generated** | MM/dd/yyyy |`, and writes one `| Date closed | MM/dd/yyyy |` row per account, with `—` instead of a date for open accounts.
- The generator guarantees every report has an account closed 1 to 12 months before the report date (credit-report-generation spec), so every freshly generated report qualifies. The tool still matters for edited or older files and gives the alert a tested definition.

## Goals / Non-Goals

**Goals:**
- A small command-line tool in the same package and style as `CreditReportCreator`.
- Logic that can be unit tested without capturing `System.out` or calling `System.exit`.

**Non-Goals:**
- A REST endpoint or MCP tool (the user asked for command-line output only).
- Other alert types (late payments, new accounts, inquiries). The capability is named `credit-report-alerts` so they can be added later.
- Parsing the whole report back into a `CreditReport`.

## Decisions

### 1. Read the Markdown files, not regenerate from seeds

The saved files are the "generated credit reports". Seeds are not stored in the files, and a seed only reproduces a report on the same day, so regenerating is not possible.

### 2. Parse only the three rows needed, with line-anchored patterns

Match whole lines with regular expressions for `| **Prepared for** | (.+) |`, `| **Date generated** | (MM/dd/yyyy) |` and `| Date closed | (MM/dd/yyyy) |`. Closed rows whose value is `—` don't match the date pattern and are treated as open. This keeps the tool independent of the rest of the layout.

*Alternative considered:* a full Markdown parser back to `CreditReport`. Rejected as far more code than the alert needs and fragile to layout changes in sections the alert doesn't use.

### 3. The window is measured from the report's date

Recently closed means `reportDate - 12 months <= closed <= reportDate`, inclusive on both ends. This matches the generator's "closed 1 to 12 months before the report date" guarantee while also accepting any closing up to the report date. Using the report date rather than today makes output stable for checked-in test data.

### 4. Structure

New class `RecentlyClosedAccountAlerts`:

- `main(String[] args)`: resolves the directory (default `CreditReportCreator.DEFAULT_OUTPUT_DIR`), prints an error and exits with status 1 if it isn't a directory, otherwise prints the names or the "No credit reports…" message.
- `static List<String> find(Path dir, PrintStream warnings)`: lists `*.md` regular files directly in `dir` sorted by file name, reads each, and returns the names of qualifying reports. Unreadable files are reported to `warnings` and skipped.
- `static boolean hasRecentlyClosedAccount(LocalDate reportDate, List<LocalDate> closedDates)`: the window rule, kept separate so the boundaries are easy to test.

## Risks / Trade-offs

- [The writer's layout changes] → one test runs the tool on a report written by `CreditReportCreator` and `MarkdownCreditReportWriter`, so a layout change that breaks parsing fails the tests. The other tests use small hand-written reports so the closed dates can be controlled.
- [Duplicate names across reports] → each qualifying report prints its name, so a name can appear twice. Acceptable for test data; the order is by file name so the output is deterministic.
