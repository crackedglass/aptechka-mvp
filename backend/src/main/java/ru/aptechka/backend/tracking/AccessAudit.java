package ru.aptechka.backend.tracking;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class AccessAudit {
	private final JdbcTemplate jdbc;
	private final JsonMapper json;

	public AccessAudit(JdbcTemplate jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public void record(UUID actor, Instant at, UUID subject, String kind, Object before, Object after) {
		jdbc.update("""
				INSERT INTO account_access_audit (id, actor, recorded_at, subject, kind, prior_state, next_state)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), actor, Timestamp.from(at), subject, kind,
				before == null ? null : json.writeValueAsString(before),
				after == null ? null : json.writeValueAsString(after));
	}
}
