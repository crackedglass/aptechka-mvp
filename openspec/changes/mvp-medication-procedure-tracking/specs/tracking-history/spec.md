## ADDED Requirements

### Requirement: Event-sourced tracking core
The system SHALL persist immutable facts for instruction/plan changes, occurrence cancellation/reopening, completion reports, corrections/retractions and handling changes. Current tracking state SHALL be derivable from those facts with historical instruction versions intact. Completion-driven claim closure SHALL be reconstructable. Pending, overdue and completed SHALL be derived states; clock-driven status changes SHALL NOT require not-done events.

#### Scenario: Rebuild tracking state
- **WHEN** tracking projections are rebuilt from recorded facts
- **THEN** instruction versions, occurrence lifecycle, report revisions, completion status and handling state match the recorded history
- **AND** rebuilding does not manufacture care reports

#### Scenario: Replay completion-driven claim closure
- **WHEN** facts include a claim followed by another actor's accepted completion
- **THEN** replay yields a completed occurrence without an active claim and retains both actors' actions

### Requirement: Complete mutation history outside the tracking core
Accounts and access relationships SHALL use conventional storage with an append-only change log. All changes to instructions, schedules, occurrences, reports, patient details, access permissions and inbox read/unread state SHALL preserve who changed what, when and prior values or enough history to reconstruct them. Deletion and cancellation SHALL NOT erase history. Passwords and authentication secrets SHALL NOT be logged. Read-access audit logging SHALL remain outside this capability.

#### Scenario: Patient details or access change
- **WHEN** an authorized actor updates patient details or revokes caregiver access
- **THEN** an append-only change record identifies the actor, recording time and sufficient prior state to reconstruct the change

#### Scenario: Deletion or cancellation
- **WHEN** a supported deletion or cancellation changes a record's current availability
- **THEN** its historical changes and care facts are retained

#### Scenario: Account credentials change
- **WHEN** account authentication data changes
- **THEN** change logging does not include passwords, tokens or other authentication secrets
