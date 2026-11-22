package ru.aptechka.backend.tracking;

import static ru.aptechka.backend.common.Errors.*;
import static ru.aptechka.backend.tracking.TrackingState.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.aptechka.backend.account.AccountService;
import ru.aptechka.backend.account.CurrentUser;
import ru.aptechka.backend.tracking.TrackingStore.Patient;

@Service
@Transactional
class TrackingAccess {
	record Profile(UUID id, String name, String zone, Role role, UUID caregiver, UUID patientLogin) {}
	record ProfileInput(String name, String zone) {}
	record InviteInput(Role role, String email) {}
	record Invitation(UUID id, UUID patient, Role role, String email, Instant expires, String state) {
		Invitation withState(String next) {
			return new Invitation(id, patient, role, email, expires, next);
		}
	}
	record InviteResult(Invitation invitation, String token) {}

	private final JdbcTemplate jdbc;
	private final TrackingStore store;
	private final AccessAudit audit;
	private final Clock clock;

	TrackingAccess(JdbcTemplate jdbc, TrackingStore store, AccessAudit audit, Clock clock) {
		this.jdbc = jdbc;
		this.store = store;
		this.audit = audit;
		this.clock = clock;
	}

	List<Profile> profiles(CurrentUser actor) {
		return store.patients(actor.id()).stream().map(p -> profile(p, actor.id())).toList();
	}

	Profile create(CurrentUser actor, ProfileInput input) {
		checkProfile(input);
		jdbc.queryForObject("SELECT id FROM app_user WHERE id = ? FOR UPDATE", UUID.class, actor.id());
		if (store.patients(actor.id()).stream().anyMatch(patient -> patient.manager().equals(actor.id()))) {
			throw conflict("У менеджера уже есть подопечный");
		}
		Patient patient = new Patient(UUID.randomUUID(), actor.id(), null, null, input.name().strip(), input.zone());
		jdbc.update("INSERT INTO tracking_patient (id, manager, name, zone) VALUES (?, ?, ?, ?)",
				patient.id(), patient.manager(), patient.name(), patient.zone());
		audit.record(actor.id(), clock.instant(), patient.id(), "PATIENT_CREATED", null, patient);
		return profile(patient, actor.id());
	}

	Profile update(CurrentUser actor, UUID id, ProfileInput input) {
		Patient before = manager(id, actor);
		checkProfile(input);
		Instant now = clock.instant();
		if (!before.zone().equals(input.zone())) {
			TrackingState state = store.replay(id);
			for (Version version : List.copyOf(state.versions.values())) {
				Instant end = state.closures.get(version.version());
				Instant from = max(now, version.from());
				if (!state.active.containsKey(version.plan()) || end != null && !end.isAfter(from)) continue;
				ZoneChange change = new ZoneChange(version.version(),
						new Version(version.plan(), state.sequence + 1, input.zone(), from, version.instruction()),
						now, TrackingService.protectedSlots(state, version.plan(), now));
				store.append(state, actor.id(), now, "ZONE", change);
				state.apply(change);
			}
		}
		Patient after = new Patient(id, before.manager(), before.caregiver(), before.patientLogin(), input.name().strip(), input.zone());
		jdbc.update("UPDATE tracking_patient SET name = ?, zone = ? WHERE id = ?", after.name(), after.zone(), id);
		audit.record(actor.id(), now, id, "PATIENT_UPDATED", before, after);
		return profile(after, actor.id());
	}

	List<Invitation> invitations(CurrentUser actor, UUID id) {
		manager(id, actor);
		return invitations("WHERE patient_id = ? ORDER BY expires DESC, id", id);
	}

	InviteResult invite(CurrentUser actor, UUID id, InviteInput input) {
		Patient patient = manager(id, actor);
		if (input.role() == null || input.role() == Role.MANAGER || !text(input.email(), 254)
				|| !input.email().strip().matches("[^\\s@]+@[^\\s@]+")) {
			throw badRequest("Укажите роль и почту приглашённого");
		}
		String email = AccountService.normalizeEmail(input.email());
		if (actor.email().equals(email) || input.role() == Role.PATIENT && patient.patientLogin() != null) {
			throw conflict("Доступ уже настроен");
		}
		Instant now = clock.instant();
		for (Invitation pending : invitations("WHERE patient_id = ? AND role = ? AND state = 'PENDING'", id, input.role().name())) {
			changeInvitation(pending, "SUPERSEDED", actor.id(), now);
		}
		String token = UUID.randomUUID() + "." + UUID.randomUUID();
		Invitation invite = new Invitation(UUID.randomUUID(), id, input.role(), email, now.plus(7, ChronoUnit.DAYS), "PENDING");
		jdbc.update("""
				INSERT INTO tracking_invitation (id, patient_id, role, email, digest, expires, state)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", invite.id(), id, invite.role().name(), email, digest(token), Timestamp.from(invite.expires()), invite.state());
		audit.record(actor.id(), now, invite.id(), "INVITATION_CREATED", null, invite);
		return new InviteResult(invite, token);
	}

	Profile accept(CurrentUser actor, String token) {
		if (!text(token, 100)) throw badRequest("Укажите код приглашения");
		String digest = digest(token.strip());
		Invitation candidate = invitation(digest);
		Patient before = store.lock(candidate.patient());
		Invitation invite = invitation(digest);
		Instant now = clock.instant();
		if (!actor.email().equals(invite.email()) || !invite.state().equals("PENDING") || !now.isBefore(invite.expires())) {
			throw notFound("Приглашение недоступно");
		}
		if (actor.id().equals(before.manager()) || actor.id().equals(before.caregiver()) || actor.id().equals(before.patientLogin())) {
			throw conflict("У аккаунта уже есть роль");
		}
		UUID caregiver = before.caregiver();
		UUID login = before.patientLogin();
		if (invite.role() == Role.PATIENT) {
			if (login != null) throw conflict("Доступ подопечного уже активирован");
			login = actor.id();
		} else {
			if (caregiver != null) ensureClaimsResolved(before.id(), caregiver);
			caregiver = actor.id();
		}
		Patient after = new Patient(before.id(), before.manager(), caregiver, login, before.name(), before.zone());
		jdbc.update("UPDATE tracking_patient SET caregiver = ?, patient_login = ? WHERE id = ?", caregiver, login, before.id());
		audit.record(actor.id(), now, before.id(), "ACCESS_ACCEPTED", before, after);
		changeInvitation(invite, "ACCEPTED", actor.id(), now);
		return profile(after, actor.id());
	}

	void revokeCaregiver(CurrentUser actor, UUID id) {
		Patient before = manager(id, actor);
		if (before.caregiver() == null) return;
		ensureClaimsResolved(id, before.caregiver());
		jdbc.update("UPDATE tracking_patient SET caregiver = NULL WHERE id = ?", id);
		Patient after = new Patient(id, before.manager(), null, before.patientLogin(), before.name(), before.zone());
		audit.record(actor.id(), clock.instant(), id, "ACCESS_REVOKED", before, after);
	}

	void revokeInvitation(CurrentUser actor, UUID id, UUID invitation) {
		manager(id, actor);
		Invitation pending = invitations("WHERE patient_id = ? AND id = ? AND state = 'PENDING'", id, invitation).stream()
				.findFirst().orElseThrow(() -> notFound("Приглашение недоступно"));
		changeInvitation(pending, "REVOKED", actor.id(), clock.instant());
	}

	private void ensureClaimsResolved(UUID id, UUID caregiver) {
		if (store.replay(id).occurrences.values().stream()
				.anyMatch(o -> o.handler != null && o.handler.actor().equals(caregiver))) {
			throw conflict("Сначала освободите активные задачи помощника");
		}
	}

	private Patient manager(UUID id, CurrentUser actor) {
		Patient patient = store.lock(id);
		requireManager(patient.role(actor.id()));
		return patient;
	}

	static void requireManager(Role role) {
		if (role != Role.MANAGER) throw forbidden("Действие доступно менеджеру");
	}

	static Instant max(Instant left, Instant right) {
		return left.isAfter(right) ? left : right;
	}

	private static Profile profile(Patient patient, UUID actor) {
		Role role = patient.role(actor);
		return new Profile(patient.id(), patient.name(), patient.zone(), role,
				role == Role.MANAGER ? patient.caregiver() : null, role == Role.MANAGER ? patient.patientLogin() : null);
	}

	private void checkProfile(ProfileInput input) {
		if (!text(input.name(), 100) || !text(input.zone(), 100) || !ZoneId.getAvailableZoneIds().contains(input.zone())) {
			throw badRequest("Укажите имя и часовой пояс подопечного");
		}
	}

	private Invitation invitation(String digest) {
		return invitations("WHERE digest = ?", digest).stream()
				.findFirst().orElseThrow(() -> notFound("Приглашение недоступно"));
	}

	private List<Invitation> invitations(String where, Object... arguments) {
		return jdbc.query("SELECT id, patient_id, role, email, expires, state FROM tracking_invitation " + where,
				(rs, row) -> new Invitation(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
						Role.valueOf(rs.getString(3)), rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getString(6)), arguments);
	}

	private void changeInvitation(Invitation invite, String state, UUID actor, Instant now) {
		jdbc.update("UPDATE tracking_invitation SET state = ? WHERE id = ?", state, invite.id());
		audit.record(actor, now, invite.id(), "INVITATION_CHANGED", invite, invite.withState(state));
	}

	private static String digest(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
