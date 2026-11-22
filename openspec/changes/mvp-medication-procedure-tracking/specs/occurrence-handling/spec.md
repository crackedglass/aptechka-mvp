## ADDED Requirements

### Requirement: Optional exclusive handling claims
Any currently authorized manager, caregiver or activated patient SHALL be able to claim an occurrence to indicate handling. The system SHALL allow at most one active handler per occurrence, show holder and claim time to all three actors, and SHALL NOT treat claiming as completion. A claim SHALL NOT be required to report care that already happened.

#### Scenario: Concurrent claims
- **WHEN** two actors attempt to claim the same unclaimed occurrence concurrently
- **THEN** only one becomes active handler and the other must use explicit takeover to replace them

#### Scenario: Report without claiming
- **WHEN** an actor reports care that already happened without first claiming the occurrence
- **THEN** absence of a claim does not prevent a valid completion report

### Requirement: Explicit release and takeover without expiry
An active claim SHALL remain active until its holder releases it, a valid completion closes it, or another authorized actor explicitly takes over. Takeover SHALL require a reason and preserve previous and new handlers, timestamps and history. Claims SHALL NOT automatically expire. Takeover SHALL NOT itself mark the occurrence complete.

#### Scenario: Holder releases
- **WHEN** the active handler releases their claim
- **THEN** the occurrence has no active handler and the release remains in history

#### Scenario: Another actor takes over
- **WHEN** another authorized actor explicitly takes over with a reason
- **THEN** that actor becomes the sole handler, the reason and transition are retained, and completion status is unchanged

#### Scenario: Takeover lacks a reason
- **WHEN** another actor attempts takeover without a reason
- **THEN** the current claim remains unchanged and the takeover is rejected

#### Scenario: Claim remains active over time
- **WHEN** time passes without release, takeover or accepted completion
- **THEN** the claim remains active even if the occurrence becomes overdue

### Requirement: Completion closes any active claim
A valid completion report from any authorized actor SHALL close the active claim even when held by another actor. Handling and reporting actions SHALL both remain reconstructable and completed status SHALL immediately be exposed as current shared state to all authorized actors. Claim closure SHALL NOT depend on a later manual release.

#### Scenario: Patient completes while caregiver handles
- **WHEN** the patient submits a valid completion report while the caregiver holds the claim
- **THEN** the occurrence becomes completed, its active claim closes and shared status reflects completion
- **AND** the caregiver's handling history and patient's report remain preserved
