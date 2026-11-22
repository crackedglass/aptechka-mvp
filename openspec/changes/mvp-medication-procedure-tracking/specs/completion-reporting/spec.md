## ADDED Requirements

### Requirement: Full positive reports with independent authorship
The system SHALL accept full-completion assertions by the manager, caregiver or activated patient for a specific occurrence, showing its scheduled dose or procedure instructions while reporting. A medication report SHALL mean the full scheduled dose was taken; a procedure report SHALL mean the entire procedure was completed. Report author and patient receiving care SHALL be separate identities. Reports SHALL NOT represent partial care, not-done assertions or a required patient acknowledgment of caregiver administration.

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
The system SHALL store actual completion time separately from submission time as absolute timestamps and SHALL reject actual completion times in the future. It SHALL permit care before or after the scheduled time without the prototype's one-hour window. Today's tasks SHALL be the default view and previous days SHALL remain accessible for late reports. Submission SHALL stay linked to the selected occurrence. An occurrence with no valid report SHALL be pending until its scheduled time and overdue after that time; overdue SHALL NOT assert that care failed to happen or create an automatic missed/not-done report.

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

#### Scenario: Scheduled time passes without a report
- **WHEN** an open unreported occurrence passes its scheduled time
- **THEN** its derived status becomes overdue without creating a missed or not-done assertion

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
All authorized actors SHALL see shared completion status before reporting. When a valid report exists, the system SHALL display “Already reported complete” and the recorded completion time, and SHALL require explicit acknowledgment that a new report confirms the same care rather than another administration. The system SHALL refresh and recheck status before accepting the submission, including concurrent changes, without exposing other actors' detailed reports to caregivers. Recorded completion SHALL NOT be presented as proof of the number of physical administrations.

#### Scenario: Confirm already reported care
- **WHEN** an actor attempts an additional report on a completed occurrence
- **THEN** the warning and completion time are shown and the actor must acknowledge the same care before acceptance

#### Scenario: Another actor completes after the screen loads
- **WHEN** a report is submitted from a previously uncompleted view after another actor's report has been accepted
- **THEN** the system requires acknowledgment against the updated completion status before accepting the new report
