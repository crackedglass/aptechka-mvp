## ADDED Requirements

### Requirement: Persistent manager inbox
The system SHALL provide the treatment manager a persistent in-app inbox with read and unread states. Inbox entries and state changes SHALL survive sessions. Push, SMS, email and additional reminder triggers SHALL NOT be required by this capability.

#### Scenario: Mark and revisit a notification
- **WHEN** the manager marks a notification read and later opens a new session
- **THEN** the entry remains in the inbox with read status
- **AND** the manager can mark it unread again

### Requirement: Notify previous-patient-local-day submissions
The system SHALL notify the manager when a completion report is submitted for an occurrence whose patient-local calendar day precedes the patient-local submission day. A later report on the same patient-local day SHALL NOT trigger this notification. The occurrence's day, not the report's actual care time or the actor's device time zone, SHALL determine the comparison.

#### Scenario: Yesterday's occurrence is reported today
- **WHEN** an actor submits a report for an occurrence on a previous day in the patient's time zone
- **THEN** the manager receives a persistent inbox notification associated with that report and occurrence

#### Scenario: Same-day late report
- **WHEN** a report is submitted after its scheduled time but on the same patient-local calendar day
- **THEN** no previous-day notification is generated

### Requirement: Notify retraction of the last valid report
The system SHALL notify the manager when a retraction removes an occurrence's final valid completion assertion and returns it to pending or overdue. It SHALL NOT emit this notification when another valid assertion remains.

#### Scenario: Last report is retracted
- **WHEN** a retraction leaves no valid completion reports
- **THEN** a persistent manager notification identifies the occurrence whose completion status reopened

#### Scenario: Other report remains valid
- **WHEN** one author retracts but another valid report remains
- **THEN** no last-report-retraction notification is created

### Requirement: Notification processing preserves logical delivery
Retries and replay of the same triggering fact SHALL NOT duplicate its inbox entry or overwrite its read/unread state. Accepted triggering facts SHALL remain recoverable for inbox delivery after processing interruption.

#### Scenario: Retry or rebuild after delivery
- **WHEN** the same notification-triggering fact is processed again after its notification was marked read
- **THEN** the existing notification remains single and read

#### Scenario: Recover interrupted delivery
- **WHEN** processing resumes after interruption between recording a triggering fact and inbox delivery
- **THEN** the manager's inbox receives the corresponding notification without losing the trigger
