## ADDED Requirements

### Requirement: Full positive reports with independent authorship
The system SHALL accept only full-completion reports by currently authorized actors for a specific occurrence, identifying author and patient separately. Reporting SHALL show the full scheduled dose or procedure instructions. Medication reports assert the full dose was taken. Procedure reports assert the whole procedure was completed. The system SHALL NOT require patient acknowledgment of caregiver care.

#### Scenario: Manager provides care
- **WHEN** the manager reports a dose they personally provided
- **THEN** the report records the manager as author and the linked patient as care recipient for that occurrence

#### Scenario: Procedure completion
- **WHEN** a caregiver reports a scheduled procedure
- **THEN** the procedure instructions are shown and the accepted assertion represents completion of the entire procedure

### Requirement: One report lineage per actor and occurrence
Each actor SHALL have one report lineage per occurrence; later changes SHALL be appended corrections or retractions. Retrying a submission SHALL NOT create duplicate reports. Different actors SHALL be able to report independently, while an occurrence SHALL count as completed once whenever at least one valid report remains.

#### Scenario: Retry a completion submission
- **WHEN** the same accepted submission is retried
- **THEN** no second report or second completed occurrence is created

#### Scenario: Two authors report one occurrence
- **WHEN** caregiver and patient submit valid independent reports confirming the same care
- **THEN** both authors' reports are retained and the occurrence counts as completed once

### Requirement: Actual care time and historical reporting
The system SHALL store actual completion and submission as separate absolute timestamps and reject future actual times. Actors SHALL be able to report before or after the scheduled time without the one-hour window. Today's tasks SHALL open by default, and late reports SHALL remain linked to their selected historical occurrence.

#### Scenario: Report yesterday's care today
- **WHEN** an actor opens yesterday's patient-local schedule and reports an occurrence today with a nonfuture actual care time
- **THEN** the report references yesterday's occurrence and retains distinct actual and submission times
- **AND** the manager receives the previous-day notification

#### Scenario: Report outside the prototype window
- **WHEN** an actor reports an open occurrence more than one hour after its scheduled time with a valid nonfuture actual time
- **THEN** the one-hour window does not prevent acceptance

#### Scenario: Reject future actual time
- **WHEN** an actor submits an actual completion time later than the current time
- **THEN** the system rejects the report

### Requirement: Append-only author corrections and retractions
Only the report author SHALL append a correction or retraction, with a required reason. The system SHALL preserve the original, all revisions, the correcting actor and timestamps. Retraction SHALL invalidate that author's assertion without erasing it. A remaining valid report SHALL keep the occurrence completed; removing the last valid report SHALL return it to pending or overdue and notify the manager. Visibility SHALL follow care-access permissions.

#### Scenario: Correct own report
- **WHEN** a report author submits an allowed correction with a reason
- **THEN** the system appends the correction with actor and time while preserving the original and earlier revisions

#### Scenario: Reason is missing
- **WHEN** an author submits a correction or retraction without a reason
- **THEN** the system rejects the mutation

#### Scenario: Another valid assertion remains
- **WHEN** the caregiver retracts their assertion while the patient's assertion remains valid
- **THEN** the occurrence stays completed and no last-report-retraction notification is created

#### Scenario: Final assertion is retracted
- **WHEN** the final valid report is retracted
- **THEN** the occurrence becomes pending if its scheduled time has not passed, otherwise overdue
- **AND** the manager receives an inbox notification

### Requirement: Shared completion warning and current-state acknowledgment
The system SHALL show shared completion status before reporting. When a valid report exists, it SHALL show “Already reported complete” and the recorded completion time, and require acknowledgment that the new report confirms the same care. Submission SHALL recheck current status without exposing other authors' details to caregivers. The system SHALL NOT present recorded completion as proof of the number of administrations.

#### Scenario: Confirm already reported care
- **WHEN** an actor attempts an additional report on a completed occurrence
- **THEN** the warning and completion time are shown and the actor must acknowledge the same care before acceptance

#### Scenario: Another actor completes after the screen loads
- **WHEN** a report is submitted from a previously uncompleted view after another actor's report has been accepted
- **THEN** the system requires acknowledgment against the updated completion status before accepting the new report

#### Scenario: Valid reports disagree on actual time
- **WHEN** valid reports give differing actual completion times
- **THEN** shared status displays the earliest currently valid actual time

### Requirement: Time-based occurrence status
An open occurrence without a valid report SHALL be pending until its scheduled time and overdue afterwards. Overdue SHALL NOT assert failed care or create a missed or not-done report.

#### Scenario: Scheduled time passes without a report
- **WHEN** an open unreported occurrence passes its scheduled time
- **THEN** its derived status becomes overdue without creating a missed or not-done assertion
