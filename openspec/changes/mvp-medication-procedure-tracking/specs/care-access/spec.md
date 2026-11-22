## ADDED Requirements

### Requirement: Patient identity and MVP relationships
The system SHALL maintain a mandatory patient identity distinct from report authors, with optional login activation. Each patient SHALL have exactly one treatment manager and zero or one active caregiver; each manager SHALL have one patient in MVP. The manager SHALL create the patient identity and SHALL be able to begin tracking without caregiver participation or patient login.

#### Scenario: Begin tracking alone
- **WHEN** a manager creates a patient who has no login and no caregiver
- **THEN** the manager can create plans and report care for that patient
- **AND** the patient identity remains distinct from the manager's account

#### Scenario: Enforce MVP cardinality
- **WHEN** a relationship change would give a manager a second patient or a patient a second active caregiver or manager
- **THEN** the system rejects that relationship change

### Requirement: Accepted invitations grant access
Only the manager SHALL administer access and invite caregivers or invite the patient to activate login. An invitation SHALL grant access only after acceptance.

#### Scenario: Invitation is still pending
- **WHEN** an invited caregiver has not accepted the invitation
- **THEN** the invitation alone does not permit reading or changing the patient's tracking data

#### Scenario: Caregiver joins
- **WHEN** the invited caregiver accepts a valid invitation
- **THEN** the caregiver receives access to the linked patient's tasks within caregiver permissions

#### Scenario: Patient activates access
- **WHEN** the patient accepts their activation invitation and activates login
- **THEN** the patient receives patient permissions for the existing patient identity without creating a second care recipient

### Requirement: Role-specific commands and views
All three currently authorized actors SHALL be able to view scheduled tasks and shared completion/handling status, report full completion, claim, release their own claim, explicitly take over handling, and correct or retract their own reports. Only the manager SHALL create, edit or stop instructions/plans, administer invitations/access or reopen cancelled occurrences. Manager and activated patient SHALL see all care reports and corrections; caregivers SHALL see only their own detailed reports/corrections plus necessary instructions, shared completion status, recorded completion time and claim holder/time. No actor SHALL correct another author's report. Caregiver tasks SHALL mean the linked patient's occurrences, without exclusive per-task assignment.

#### Scenario: Restrict caregiver history
- **WHEN** a caregiver opens an occurrence reported by the patient
- **THEN** the caregiver sees necessary instructions, shared completion status and recorded completion time
- **AND** the patient's detailed report and correction history are excluded from the caregiver response

#### Scenario: Patient reads history without editing authority
- **WHEN** an activated patient reads care history and then attempts to edit a plan or invite a caregiver
- **THEN** all care reports and corrections are available to read
- **AND** plan editing and access administration are rejected

#### Scenario: Manager cannot rewrite another report
- **WHEN** the manager attempts to correct or retract a caregiver-authored report
- **THEN** the system rejects the mutation

### Requirement: Caregiver replacement preserves history and resolves handling
Replacing a caregiver SHALL revoke the former caregiver's access, preserve their reports and history, and require the manager to resolve their active claims explicitly. Replacement SHALL NOT silently erase claims or grant a second active caregiver access.

#### Scenario: Replace a caregiver with an active claim
- **WHEN** the manager replaces a caregiver who has an active claim
- **THEN** the workflow requires explicit claim resolution before replacement completes
- **AND** the former caregiver loses access after replacement while their authored history remains preserved

#### Scenario: Revoked actor attempts access
- **WHEN** the former caregiver attempts to read tasks or submit a report using their previous session
- **THEN** current relationship authorization denies access
