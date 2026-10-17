# Proposal

## Why

The dummy credit report generator saves reports as Markdown files in `test-data/credit-reports/`. The only way to find out which reports have a recently closed account is to open each file and read every account's "Date closed" row. A recently closed account is one of the things a credit alert is raised for, so testers need a quick way to see which reports would trigger that alert.

## What Changes

- Add a command-line tool that reads every generated credit report in a directory and prints, one per line, the name ("Prepared for") on each report that has a recently closed account.
- An account counts as recently closed when its closed date falls in the 12 months up to and including the report's own "Date generated". The report's date is used, not today's, so the result for a file never changes.
- The directory is an optional argument and defaults to `test-data/credit-reports`, where the generator writes.
- Files that are not credit reports, or that are missing the name or report date, are skipped with a warning. A missing directory is an error.
- The output is printed to the command line only. There is no REST endpoint or MCP tool.

## Capabilities

### New Capabilities
- `credit-report-alerts`: finding generated credit reports that should raise an alert. This change adds the recently-closed-account alert.

### Modified Capabilities

None.

## Impact

- New class in `src/main/java/com/example/testdata/creditreport/` with a `main` method, next to `CreditReportCreator`.
- New unit tests in `src/test/java/com/example/testdata/creditreport/`.
- README: a short section on how to run the tool.
- No change to the generator, the Markdown writer, the `CreditReport` model, the REST API or the MCP modules.
