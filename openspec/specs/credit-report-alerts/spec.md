# credit-report-alerts Specification

## Purpose
Defines how generated credit reports are checked for conditions that should raise an alert, so testers can find the reports that exercise each alert.

## Requirements

### Requirement: List reports with a recently closed account
The alerts command-line tool SHALL read every generated credit report in a directory and print the name each qualifying report was prepared for, one per line. A report qualifies when at least one of its accounts has a closed date that is on or before the report's date generated and no more than 12 months before it.

#### Scenario: Report with an account closed within the past year
- **WHEN** a report dated 10/16/2026 and prepared for "ELENA C MOCKRIDGE" has an account closed on 05/25/2026
- **THEN** the tool prints "ELENA C MOCKRIDGE"

#### Scenario: Report whose closed accounts are older than a year
- **WHEN** a report dated 10/16/2026 has only open accounts and an account closed on 10/15/2025
- **THEN** the tool does not print that report's name

#### Scenario: Boundary of the 12-month window
- **WHEN** a report dated 10/16/2026 has an account closed on 10/16/2025
- **THEN** the tool prints that report's name

#### Scenario: Result does not depend on the current date
- **WHEN** the tool is run on any day against the same report files
- **THEN** it prints the same names, because the window is measured from each report's own date generated

### Requirement: Reports directory and file selection
The tool SHALL accept an optional directory argument, defaulting to `test-data/credit-reports`. It SHALL read only the Markdown (`.md`) files directly in that directory, in file-name order, and print the qualifying names in that order.

#### Scenario: Default directory
- **WHEN** the tool is run with no arguments
- **THEN** it reads the reports in `test-data/credit-reports`

#### Scenario: Other files are ignored
- **WHEN** the directory contains a `.txt` file and a subdirectory of reports
- **THEN** neither is read

#### Scenario: Several qualifying reports
- **WHEN** `credit-report-a.md` and `credit-report-b.md` both qualify
- **THEN** the name from `credit-report-a.md` is printed before the name from `credit-report-b.md`

### Requirement: Unreadable reports and missing directory
The tool SHALL skip a Markdown file that has no "Prepared for" name or no valid "Date generated" date, printing a warning that names the file to standard error, and continue with the other files. If the directory does not exist, the tool SHALL print an error to standard error and exit with a non-zero status. If no report qualifies, the tool SHALL print a message saying so and exit successfully.

#### Scenario: File that is not a credit report
- **WHEN** the directory contains `notes.md` with no "Prepared for" row
- **THEN** the tool prints a warning naming `notes.md` to standard error and still lists the qualifying reports

#### Scenario: Missing directory
- **WHEN** the tool is run with a directory that does not exist
- **THEN** it prints an error to standard error and exits with a non-zero status

#### Scenario: No qualifying reports
- **WHEN** no report in the directory has a recently closed account
- **THEN** the tool prints "No credit reports with a recently closed account." and exits with status 0
