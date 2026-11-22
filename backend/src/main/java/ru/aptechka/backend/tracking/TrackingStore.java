package ru.aptechka.backend.tracking;

import static ru.aptechka.backend.common.Errors.notFound;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
final class TrackingStore {
	record Patient(UUID id, UUID manager, UUID caregiver, UUID patientLogin, String name, String zone) {
		TrackingState.Role role(UUID actor) {
			if (actor.equals(manager)) return TrackingState.Role.MANAGER;
			if (actor.equals(caregiver)) return TrackingState.Role.CAREGIVER;
			if (actor.equals(patientLogin)) return TrackingState.Role.PATIENT;
			throw notFound("Профиль не найден");
		}
	}

	record Receipt(String fingerprint, String response) {}

	private final JdbcTemplate jdbc;
	private final JsonMapper json;

	TrackingStore(JdbcTemplate jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	List<Patient> patients(UUID actor) {
		return patients("""
				SELECT * FROM tracking_patient WHERE manager = ? OR caregiver = ? OR patient_login = ?
				ORDER BY name, id
				""", actor, actor, actor);
	}

	Patient lock(UUID id) {
		return patients("SELECT * FROM tracking_patient WHERE id = ? FOR UPDATE", id).stream()
				.findFirst().orElseThrow(() -> notFound("Профиль не найден"));
	}

	private List<Patient> patients(String sql, Object... arguments) {
		return jdbc.query(sql, (rs, row) -> new Patient(rs.getObject("id", UUID.class),
				rs.getObject("manager", UUID.class), rs.getObject("caregiver", UUID.class),
				rs.getObject("patient_login", UUID.class), rs.getString("name"), rs.getString("zone")), arguments);
	}

	TrackingState replay(UUID patient) {
		TrackingState state = new TrackingState(patient);
		jdbc.query("""
				SELECT seq, schema_version, recorded_at, kind, payload FROM tracking_event WHERE patient_id = ? ORDER BY seq
				""", rs -> {
			if (rs.getInt("schema_version") != 1 || rs.getLong("seq") != state.sequence + 1) {
				throw new IllegalStateException("Unsupported tracking stream");
			}
			long sequence = rs.getLong("seq");
			Instant recorded = rs.getTimestamp("recorded_at").toInstant();
			String payload = rs.getString("payload");
			switch (rs.getString("kind")) {
				case "PLAN" -> state.apply(sequence, json.readValue(payload, TrackingState.PlanChange.class));
				case "ZONE" -> state.apply(json.readValue(payload, TrackingState.ZoneChange.class));
				case "OCCURRENCE" -> state.apply(sequence, recorded, json.readValue(payload, TrackingState.OccurrenceChange.class));
				case "INBOX" -> state.apply(json.readValue(payload, TrackingState.ReadChange.class));
				default -> throw new IllegalStateException("Unknown tracking event");
			}
			state.sequence = sequence;
		}, patient);
		return state;
	}

	void append(TrackingState state, UUID actor, Instant at, String kind, Object payload) {
		jdbc.update("""
				INSERT INTO tracking_event (patient_id, seq, schema_version, actor, recorded_at, kind, payload)
				VALUES (?, ?, 1, ?, ?, ?, ?)
				""", state.patient, ++state.sequence, actor, Timestamp.from(at), kind, json.writeValueAsString(payload));
	}

	Receipt receipt(UUID patient, UUID actor, UUID key) {
		return jdbc.query("""
				SELECT fingerprint, response FROM tracking_receipt WHERE patient_id = ? AND actor = ? AND request_id = ?
				""", (rs, row) -> new Receipt(rs.getString(1), rs.getString(2)), patient, actor, key)
				.stream().findFirst().orElse(null);
	}

	void receipt(UUID patient, UUID actor, UUID key, String fingerprint, Object result) {
		jdbc.update("""
				INSERT INTO tracking_receipt (patient_id, actor, request_id, fingerprint, response)
				VALUES (?, ?, ?, ?, ?)
				""", patient, actor, key, fingerprint, json.writeValueAsString(result));
	}
}
