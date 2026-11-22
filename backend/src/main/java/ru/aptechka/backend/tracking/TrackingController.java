package ru.aptechka.backend.tracking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import ru.aptechka.backend.account.CurrentUser;
import ru.aptechka.backend.tracking.TrackingAccess.*;
import ru.aptechka.backend.tracking.TrackingService.*;
import ru.aptechka.backend.tracking.TrackingState.*;

@RestController
@RequestMapping("/api/tracking")
class TrackingController {
	record Acceptance(String token) {}

	private final TrackingAccess access;
	private final TrackingService tracking;

	TrackingController(TrackingAccess access, TrackingService tracking) {
		this.access = access;
		this.tracking = tracking;
	}

	@GetMapping("/patients")
	List<Profile> patients(@AuthenticationPrincipal CurrentUser user) {
		return access.profiles(user);
	}

	@PostMapping("/patients")
	Profile createPatient(@AuthenticationPrincipal CurrentUser user, @RequestBody ProfileInput input) {
		return access.create(user, input);
	}

	@PutMapping("/patients/{patient}")
	Profile updatePatient(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient, @RequestBody ProfileInput input) {
		return access.update(user, patient, input);
	}

	@GetMapping("/patients/{patient}/invitations")
	List<Invitation> invitations(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient) {
		return access.invitations(user, patient);
	}

	@PostMapping("/patients/{patient}/invitations")
	InviteResult invite(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient, @RequestBody InviteInput input) {
		return access.invite(user, patient, input);
	}

	@DeleteMapping("/patients/{patient}/invitations/{invitation}")
	void revokeInvitation(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient, @PathVariable UUID invitation) {
		access.revokeInvitation(user, patient, invitation);
	}

	@PostMapping("/invitations/accept")
	Profile accept(@AuthenticationPrincipal CurrentUser user, @RequestBody Acceptance input) {
		return access.accept(user, input.token());
	}

	@DeleteMapping("/patients/{patient}/caregiver")
	void revokeCaregiver(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient) {
		access.revokeCaregiver(user, patient);
	}

	@GetMapping("/patients/{patient}/day")
	DayView day(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return tracking.day(user, patient, date);
	}

	@GetMapping("/patients/{patient}/plans")
	List<Version> plans(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient) {
		return tracking.plans(user, patient);
	}

	@GetMapping("/patients/{patient}/claims")
	List<OccurrenceView> claims(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient) {
		return tracking.claims(user, patient);
	}

	@GetMapping("/patients/{patient}/plans/{plan}/preview")
	Preview preview(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient, @PathVariable UUID plan,
			@RequestParam Instant effective) {
		return tracking.preview(user, patient, plan, effective);
	}

	@PostMapping("/patients/{patient}/plans")
	Result plan(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient, @RequestBody PlanInput input) {
		return tracking.plan(user, patient, input);
	}

	@PostMapping("/patients/{patient}/occurrences")
	Result occurrence(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient, @RequestBody OccurrenceInput input) {
		return tracking.occurrence(user, patient, input);
	}

	@GetMapping("/patients/{patient}/inbox")
	List<Notice> inbox(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient) {
		return tracking.inbox(user, patient);
	}

	@PutMapping("/patients/{patient}/inbox/{notification}")
	Result read(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID patient, @PathVariable long notification, @RequestBody ReadInput input) {
		return tracking.read(user, patient, notification, input);
	}
}
