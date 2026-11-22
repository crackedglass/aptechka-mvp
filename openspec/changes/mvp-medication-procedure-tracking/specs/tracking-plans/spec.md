## ADDED Requirements

### Requirement: Manual medication and procedure instructions
The system SHALL allow the manager to enter medication instructions containing medication name, full scheduled dose and unit, and procedure instructions containing title and instructions. Both SHALL contain a start date, scheduled times and optional end date. Supported schedules SHALL be one-off activities or recurrence on selected weekdays with one or more scheduled times. Partial completion, as-needed, tapering and every-N-hours schedules SHALL NOT be introduced by this capability.

#### Scenario: Create a recurring medication instruction
- **WHEN** the manager enters a medication name, dose, unit, start date, selected weekdays and two daily times
- **THEN** the schedule represents a separate full-dose occurrence at each selected time on each selected day within the date bounds

#### Scenario: Create a one-off procedure
- **WHEN** the manager enters a procedure title and instructions for one date and time
- **THEN** the system represents one procedure occurrence with those instructions

### Requirement: Patient-local occurrence identity
The system SHALL use one explicit patient time zone to determine occurrence calendar days and SHALL keep recurring activities at the same local clock times in that zone. Each occurrence SHALL identify one scheduled activity and its instruction/schedule version, independently from other days or times. Reports SHALL reference that occurrence rather than the report submission day.

#### Scenario: Separate same-day and next-day doses
- **WHEN** a plan schedules Monday at 09:00 and 18:00 and Tuesday at 09:00
- **THEN** the system identifies three distinct occurrences

#### Scenario: Actor is in a different time zone
- **WHEN** an actor views the patient's daily schedule from another time zone
- **THEN** the patient's time zone determines which occurrences belong to that day

### Requirement: Versioned effective edits preserve exceptions
Edits SHALL create instruction and schedule versions at an explicit effective time for eligible future occurrences. Past unreported occurrences SHALL remain available for late reports. Historical occurrences and reports SHALL retain their versions. Preview and commit SHALL preserve future occurrences that have reports or active claims while allowing edits to other eligible occurrences.

#### Scenario: Edit with historical and reported exceptions
- **WHEN** the manager edits a recurring instruction with a past unreported occurrence and a future reported occurrence
- **THEN** both occurrences retain their prior occurrence identity and applicable version
- **AND** the future reported exception is shown before save while eligible future occurrences use the new version from the effective time

#### Scenario: Edit while care is underway
- **WHEN** the manager edits a plan with one actively claimed occurrence and other eligible future occurrences
- **THEN** the claimed occurrence is identified and protected
- **AND** the other eligible occurrences can receive the edit

#### Scenario: A claim appears after preview
- **WHEN** an occurrence becomes claimed after the edit preview but before save
- **THEN** saving does not overwrite that actively handled occurrence

### Requirement: Cancellation preserves history and requires reopening for reports
Cancelling a plan SHALL stop future eligible activities without erasing history or past unreported occurrences. Cancellation SHALL respect reported-occurrence preservation and active-claim protection. A cancelled occurrence SHALL reject ordinary completion reporting until the manager explicitly reopens it. Takeover alone SHALL NOT imply completion or silently authorize cancellation of an active claim.

#### Scenario: Stop a plan
- **WHEN** the manager cancels a plan
- **THEN** future eligible activities stop and historical occurrences and reports remain
- **AND** past unreported occurrences remain available for late reports

#### Scenario: Report care despite cancellation
- **WHEN** an actor attempts to report a cancelled occurrence
- **THEN** reporting is rejected until the manager explicitly reopens it
- **AND** after reopening the ordinary reporting rules apply

#### Scenario: Cancellation meets an active claim
- **WHEN** the manager attempts to cancel an actively claimed occurrence
- **THEN** cancellation requires explicit resolution of handling and cannot silently discard the active claim

### Requirement: Future scheduling after a patient time zone change
A manager's patient time zone change SHALL apply to eligible future occurrences from the change time while preserving historical and protected occurrences.

#### Scenario: Patient time zone changes
- **WHEN** the manager changes the patient time zone
- **THEN** historical and protected occurrences retain their original version and scheduled instant
- **AND** future eligible occurrences use the new zone and their scheduled local clock times
