# Proposal

## Why

The dummy credit report generator leaves late payments, closed accounts and new accounts entirely to chance. Over 1,000 seeds, 45% of reports have no late payments, 81% have no account closed in the past year and 83% have no open account opened in the past year. The checked-in sample report is all open, never-late accounts. Anything that reads these reports (for example over the credit report MCP server) rarely sees the cases that matter most when assessing credit.

## What Changes

- Every generated report has at least one account with an older late-payment streak that is no longer past due ("Was past due").
- Every generated report has at least one open account that is past due in the most recent month ("Open/Past due 30 days").
- Every generated report has at least one account closed 1 to 12 months before the report date, with data consistent with being closed (zero balance, "No data" months after closing, a "Closed" status, an on-record-until date).
- Every generated report has at least one open account opened 1 to 6 months before the report date. Today no account can be opened less than 4 months before the report date.
- These guarantees apply to every report. There is no new flag or profile, and the command-line interface is unchanged.
- The rest of each report stays random, so reports still vary from seed to seed.
- **BREAKING (test data only)**: a given seed now produces a different report than before. The sample report in `test-data/credit-reports/` is regenerated.

## Capabilities

### New Capabilities
- `credit-report-generation`: what the dummy credit report generator guarantees about the reports it produces. This change adds the account-mix guarantees: late payments, a recently closed account and a recently opened account.

### Modified Capabilities

None. The project has no specs yet.

## Impact

- `src/main/java/com/example/testdata/creditreport/CreditReportGenerator.java`: how accounts and payment histories are generated.
- `src/test/java/com/example/testdata/creditreport/CreditReportCreatorTest.java`: new assertions for each guarantee.
- `test-data/credit-reports/`: the sample report is replaced by a regenerated one (new report number and file name).
- No change to `MarkdownCreditReportWriter`, the `CreditReport` model, the command-line interface or the MCP modules. The MCP server tests use their own inline copy of a report, so they do not depend on the sample file.
