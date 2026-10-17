# Spec Delta

## Purpose

Defines what the dummy credit report generator guarantees about the fictitious reports it produces, so tests and downstream consumers can rely on realistic, varied account histories.

## ADDED Requirements

### Requirement: Reports are reproducible from a seed
The generator SHALL produce identical reports for the same seed and report date.

#### Scenario: Same seed and date
- **WHEN** two reports are generated with the same seed and the same report date
- **THEN** the two reports are equal

### Requirement: Every report includes a resolved late-payment history
Every generated report SHALL include at least one account whose 24-month payment history contains a late payment (30, 60 or 90 days past due) and whose most recent month is not late. Its status SHALL indicate it was past due.

#### Scenario: Older late streak present
- **WHEN** a report is generated for any seed
- **THEN** at least one account has a late code in its payment history, a non-late code for the most recent month, and a status ending in "Was past due"

### Requirement: Every report includes a currently past-due account
Every generated report SHALL include at least one open account whose most recent payment-history month is 30 days past due. Its status SHALL be "Open/Past due 30 days".

#### Scenario: Currently past-due account present
- **WHEN** a report is generated for any seed
- **THEN** at least one open account has "30" as its most recent payment-history code and the status "Open/Past due 30 days"

### Requirement: Every report includes a recently closed account
Every generated report SHALL include at least one account closed between 1 and 12 months, inclusive, before the report date.

#### Scenario: Recently closed account present
- **WHEN** a report is generated for any seed
- **THEN** at least one account has a closed date no earlier than 12 months and no later than 1 month before the report date

### Requirement: Closed accounts are internally consistent
Every closed account SHALL have a zero balance, zero monthly and recent payment, a status beginning with "Closed" or "Paid, Closed", "No data" for every payment-history month after the closed date, and an on-record-until date.

#### Scenario: Recently closed account data
- **WHEN** a report contains an account closed 3 months before the report date
- **THEN** that account's balance, monthly payment and recent payment are 0, its status begins with "Closed" or "Paid, Closed", its payment-history months after the closed date are "ND", and it has an on-record-until date

### Requirement: Every report includes a recently opened open account
Every generated report SHALL include at least one open account opened between 1 and 6 months, inclusive, before the report date.

#### Scenario: Recently opened account present
- **WHEN** a report is generated for any seed
- **THEN** at least one account with no closed date has an opened date no earlier than 6 months and no later than 1 month before the report date

### Requirement: Account details stay consistent with generated history
Every account SHALL be opened before the report date, have a non-negative balance, and have at most 24 payment-history months, none before the month the account was opened.

#### Scenario: Consistency across many seeds
- **WHEN** reports are generated for 200 different seeds
- **THEN** every account in every report is opened before the report date, has a balance of 0 or more, and has no more than 24 payment-history entries

### Requirement: Accounts are listed newest first
The generator SHALL list a report's accounts by opened date, most recent first.

#### Scenario: Account order
- **WHEN** a report is generated
- **THEN** each account's opened date is on or after the opened date of the account listed after it
