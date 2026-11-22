package ru.aptechka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
class TrackingFlowTests {
	private static final String API = "/api/tracking";
	private static final String PASSWORD = "tracking-password";
	private static final String TODAY = "2026-10-25";
	private static final String YESTERDAY = "2026-10-24";
	private static final String TOMORROW = "2026-10-26";
	private static final String EARLY = "2026-10-24T07:00:00Z";

	@Autowired MockMvc mvc;
	@Autowired JsonMapper json;
	@Autowired JdbcTemplate jdbc;
	private User manager, caregiver, patientLogin, replacement;
	private String patient, root;

	record User(String id, String email) {}

	@BeforeEach
	void setup() throws Exception {
		manager = register("Менеджер");
		caregiver = register("Помощник");
		patientLogin = register("Подопечный");
		replacement = register("Новый помощник");
		patient = response(call(manager, post(API + "/patients"), profile("Europe/Moscow"))
				.andExpect(status().isOk())).path("id").asString();
		root = API + "/patients/" + patient;
	}

	@ParameterizedTest
	@ValueSource(strings = {"MEDICATION", "PROCEDURE"})
	void independentReportsPrivacyAndDurableInbox(String kind) throws Exception {
		assertThat(patient).isNotEqualTo(manager.id());
		call(manager, post(API + "/patients"), profile("Europe/Moscow")).andExpect(status().isConflict());
		create(kind, YESTERDAY, null, "09:00", "18:00");
		JsonNode yesterday = day(manager, YESTERDAY).path("occurrences");
		assertThat(yesterday).hasSize(2);
		assertThat(yesterday.get(0).path("id").asString()).isNotEqualTo(yesterday.get(1).path("id").asString());
		JsonNode beforeInvite = day(manager, null);
		assertThat(beforeInvite.path("date").asString()).isEqualTo(TODAY);
		JsonNode solo = beforeInvite.path("occurrences").get(1);
		mutate(manager, action(solo, "REPORT").put("actual", EARLY)).andExpect(status().isOk());
		assertThat(find(manager, solo).path("status").asString()).isEqualTo("COMPLETED");
		assertThat(inbox()).isEmpty();
		call(caregiver, get(root + "/day"), null).andExpect(status().isNotFound());

		String firstToken = invite(caregiver, "CAREGIVER");
		String token = invite(caregiver, "CAREGIVER");
		accept(caregiver, firstToken).andExpect(status().isNotFound());
		accept(patientLogin, token).andExpect(status().isNotFound());
		call(caregiver, get(root + "/day"), null).andExpect(status().isNotFound());
		accept(caregiver, token).andExpect(status().isOk());
		accept(caregiver, token).andExpect(status().isNotFound());
		JsonNode patientProfile = response(accept(patientLogin, invite(patientLogin, "PATIENT")).andExpect(status().isOk()));
		assertThat(patientProfile.path("id").asString()).isEqualTo(patient);
		call(patientLogin, post(root + "/plans"), request()).andExpect(status().isForbidden());
		call(patientLogin, post(root + "/invitations"), json.createObjectNode()).andExpect(status().isForbidden());
		call(caregiver, get(root + "/inbox"), null).andExpect(status().isForbidden());
		call(replacement, get(root + "/day"), null).andExpect(status().isNotFound());

		JsonNode item = yesterday.get(0);
		ObjectNode stale = action(item, "REPORT").put("actual", EARLY);
		ObjectNode report = action(item, "REPORT").put("actual", "2026-10-24T07:30:00Z");
		JsonNode first = response(mutate(caregiver, report).andExpect(status().isOk()));
		JsonNode retry = response(mutate(caregiver, report).andExpect(status().isOk()));
		assertThat(retry).isEqualTo(first);
		mutate(caregiver, report.deepCopy().put("actual", EARLY)).andExpect(status().isConflict());
		mutate(caregiver, action(item, "REPORT").put("actual", EARLY)).andExpect(status().isConflict());
		mutate(patientLogin, stale).andExpect(status().isConflict());
		JsonNode patientItem = find(patientLogin, item);
		long revision = patientItem.path("completionRevision").asLong();
		assertThat(patientItem.path("completedAt").asString()).isEqualTo("2026-10-24T07:30:00Z");
		mutate(patientLogin, stale.deepCopy().put("acknowledgment", revision)).andExpect(status().isOk());
		JsonNode both = find(manager, item);
		assertThat(both.path("reports")).hasSize(2);
		assertThat(both.path("completedAt").asString()).isEqualTo(EARLY);
		assertThat(both.path("reports").get(0).get(0).path("patient").asString()).isEqualTo(patient);
		assertThat(both.path("reports").get(0).get(0).path("recorded").asString()).isEqualTo("2026-10-25T09:00:00Z");
		assertThat(find(caregiver, item).path("reports")).hasSize(1);

		mutate(manager, action(item, "CORRECT").put("author", caregiver.id()).put("reason", "Чужая запись")
				.put("reportRevision", revision).put("actual", EARLY)).andExpect(status().isForbidden());
		mutate(caregiver, action(item, "CORRECT").put("author", caregiver.id()).put("reportRevision", revision)
				.put("actual", EARLY)).andExpect(status().isBadRequest());
		mutate(caregiver, action(item, "CORRECT").put("author", caregiver.id()).put("reportRevision", revision)
				.put("actual", EARLY).put("reason", "Уточнено время")).andExpect(status().isOk());
		JsonNode corrected = find(caregiver, item).path("reports").get(0);
		assertThat(corrected).hasSize(2);
		assertThat(corrected.get(0).path("actual").asString()).isEqualTo("2026-10-24T07:30:00Z");
		retract(caregiver, item, corrected.get(1).path("seq").asLong());
		assertThat(find(manager, item).path("status").asString()).isEqualTo("COMPLETED");
		assertThat(inbox()).hasSize(2);
		retract(patientLogin, item, both.path("reports").get(1).get(0).path("seq").asLong());
		assertThat(find(manager, item).path("status").asString()).isEqualTo("OVERDUE");
		mutate(caregiver, action(item, "REPORT").put("actual", EARLY)).andExpect(status().isConflict());

		JsonNode notices = inbox();
		assertThat(notices).hasSize(3);
		assertThat(notices.get(0).path("kind").asString()).isEqualTo("LAST_RETRACTED");
		long notification = notices.get(2).path("id").asLong();
		ObjectNode read = request().put("read", true);
		call(manager, put(root + "/inbox/" + notification), read).andExpect(status().isOk());
		call(manager, put(root + "/inbox/" + notification), read).andExpect(status().isOk());
		mutate(caregiver, report).andExpect(status().isOk());
		assertThat(inbox().get(2).path("read").asBoolean()).isTrue();
		call(manager, put(root + "/inbox/" + notification), request().put("read", false)).andExpect(status().isOk());
		assertThat(inbox().get(2).path("read").asBoolean()).isFalse();

		JsonNode today = day(manager, TODAY).path("occurrences").get(0);
		mutate(manager, action(today, "REPORT").put("actual", "2026-10-25T09:00:01Z")).andExpect(status().isBadRequest());
		mutate(manager, action(today, "REPORT").put("actual", "2026-10-25T07:00:00Z")).andExpect(status().isOk());
		assertThat(inbox()).hasSize(3);
		assertThat(jdbc.queryForList("SELECT next_state FROM account_access_audit WHERE kind = 'INVITATION_CREATED'", String.class))
				.allMatch(state -> !state.contains("digest") && !state.contains(token));
	}

	@ParameterizedTest
	@ValueSource(strings = {"MEDICATION", "PROCEDURE"})
	void claimsEditsAndReplacement(String kind) throws Exception {
		create(kind, YESTERDAY, null, "10:00", "15:00", "18:00");
		accept(caregiver, invite(caregiver, "CAREGIVER")).andExpect(status().isOk());
		accept(patientLogin, invite(patientLogin, "PATIENT")).andExpect(status().isOk());
		JsonNode tomorrow = day(manager, TOMORROW).path("occurrences");
		JsonNode first = tomorrow.get(0);
		JsonNode second = tomorrow.get(1);
		JsonNode third = tomorrow.get(2);
		CountDownLatch ready = new CountDownLatch(2);
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var one = executor.submit(() -> concurrentClaim(caregiver, first, ready));
			var two = executor.submit(() -> concurrentClaim(patientLogin, first, ready));
			assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(200, 409);
		}
		JsonNode claimed = find(manager, first);
		assertThat(claimed.path("status").asString()).isEqualTo("PENDING");
		mutate(manager, action(first, "TAKEOVER").set("expectedHandler", claimed.path("handler")))
				.andExpect(status().isBadRequest());
		mutate(manager, action(first, "CANCEL")).andExpect(status().isConflict());

		mutate(caregiver, action(second, "REPORT").put("actual", EARLY)).andExpect(status().isOk());
		JsonNode plan = response(call(manager, get(root + "/plans"), null).andExpect(status().isOk())).get(0);
		String planId = plan.path("plan").asString();
		JsonNode preview = response(call(manager, get(root + "/plans/" + planId + "/preview")
				.param("effective", "2026-10-25T10:00:00Z"), null).andExpect(status().isOk()));
		assertThat(preview.path("reported")).hasSize(1);
		assertThat(preview.path("claimed")).hasSize(1);
		mutate(caregiver, action(third, "CLAIM")).andExpect(status().isOk());
		ObjectNode change = request().put("plan", planId).put("version", preview.path("version").asLong())
				.put("effective", "2026-10-25T10:00:00Z");
		change.set("instruction", instruction(kind, YESTERDAY, null, "10:00", "15:00", "18:00", "21:00").put("title", "Новая версия"));
		call(manager, post(root + "/plans"), change).andExpect(status().isOk());
		call(manager, post(root + "/plans"), change.deepCopy().put("requestId", UUID.randomUUID().toString())).andExpect(status().isConflict());
		assertThat(day(manager, TOMORROW).path("occurrences")).hasSize(4);
		assertThat(find(manager, first).path("instruction").path("title").asString()).isEqualTo("Уход");
		assertThat(find(manager, second).path("status").asString()).isEqualTo("COMPLETED");
		assertThat(find(manager, third).path("handler").path("actor").asString()).isEqualTo(caregiver.id());
		assertThat(day(manager, YESTERDAY).path("occurrences").get(0).path("instruction").path("title").asString()).isEqualTo("Уход");

		mutate(patientLogin, action(third, "REPORT").put("actual", EARLY)).andExpect(status().isOk());
		assertThat(find(manager, third).path("handler").isNull()).isTrue();
		retract(patientLogin, third, find(manager, third).path("completionRevision").asLong());
		assertThat(find(manager, third).path("status").asString()).isEqualTo("PENDING");
		assertThat(find(manager, third).path("handler").isNull()).isTrue();

		JsonNode extra = day(manager, TOMORROW).path("occurrences").get(3);
		mutate(caregiver, action(extra, "CLAIM")).andExpect(status().isOk());
		String token = invite(replacement, "CAREGIVER");
		accept(replacement, token).andExpect(status().isConflict());
		call(replacement, get(root + "/day"), null).andExpect(status().isNotFound());
		ObjectNode takeover = action(extra, "TAKEOVER").put("reason", "Передача доступа");
		takeover.set("expectedHandler", find(manager, extra).path("handler"));
		mutate(manager, takeover).andExpect(status().isOk());
		mutate(manager, action(extra, "CANCEL")).andExpect(status().isConflict());
		ObjectNode release = action(extra, "RELEASE");
		release.set("expectedHandler", find(manager, extra).path("handler"));
		mutate(manager, release).andExpect(status().isOk());
		// The first concurrent claim may also belong to the caregiver.
		if (claimed.path("handler").path("actor").asString().equals(caregiver.id())) {
			ObjectNode ownRelease = action(first, "RELEASE");
			ownRelease.set("expectedHandler", claimed.path("handler"));
			mutate(caregiver, ownRelease).andExpect(status().isOk());
		}
		accept(replacement, token).andExpect(status().isOk());
		call(caregiver, get(root + "/day"), null).andExpect(status().isNotFound());
		mutate(caregiver, action(extra, "REPORT").put("actual", EARLY)).andExpect(status().isNotFound());
		assertThat(find(manager, second).path("reports").get(0).get(0).path("author").asString()).isEqualTo(caregiver.id());
		assertThat(find(replacement, second).path("reports")).isEmpty();

		mutate(manager, action(extra, "CANCEL")).andExpect(status().isOk());
		mutate(replacement, action(extra, "REPORT").put("actual", EARLY)).andExpect(status().isConflict());
		mutate(replacement, action(extra, "REOPEN")).andExpect(status().isForbidden());
		mutate(manager, action(extra, "REOPEN")).andExpect(status().isOk());
		mutate(replacement, action(extra, "REPORT").put("actual", EARLY)).andExpect(status().isOk());

		plan = response(call(manager, get(root + "/plans"), null).andExpect(status().isOk())).get(0);
		call(manager, post(root + "/plans"), request().put("plan", planId).put("version", plan.path("version").asLong())
				.put("effective", "2026-10-25T09:00:00Z").put("stop", true)).andExpect(status().isOk());
		assertThat(find(manager, second).path("status").asString()).isEqualTo("COMPLETED");
		assertThat(day(manager, "2026-10-27").path("occurrences").get(0).path("status").asString()).isEqualTo("CANCELLED");
		assertThat(day(manager, YESTERDAY).path("occurrences").get(0).path("status").asString()).isEqualTo("OVERDUE");
	}

	@Test
	void localDaysBoundsAndZoneChanges() throws Exception {
		call(manager, put(root), profile("America/New_York")).andExpect(status().isOk());
		create("PROCEDURE", "2026-03-08", null, "02:30");
		create("PROCEDURE", "2026-11-01", null, "01:30");
		JsonNode summer = day(manager, "2026-03-08").path("occurrences").get(0);
		JsonNode autumn = day(manager, "2026-11-01").path("occurrences").get(0);
		assertThat(summer.path("slot").path("scheduled").asString()).isEqualTo("2026-03-08T07:30:00Z");
		assertThat(autumn.path("slot").path("scheduled").asString()).isEqualTo("2026-11-01T05:30:00Z");

		create("MEDICATION", "2026-10-24", "2026-10-26", "21:30", "23:00");
		JsonNode past = day(manager, YESTERDAY).path("occurrences").get(0);
		JsonNode kept = day(manager, TOMORROW).path("occurrences").get(0);
		mutate(manager, action(kept, "REPORT").put("actual", EARLY)).andExpect(status().isOk());
		call(manager, put(root), profile("Pacific/Honolulu")).andExpect(status().isOk());
		assertThat(day(manager, null).path("date").asString()).isEqualTo(YESTERDAY);
		assertThat(find(manager, past)).isEqualTo(past);
		assertThat(day(manager, YESTERDAY).path("occurrences")).hasSize(2);
		assertThat(find(manager, kept).path("slot")).isEqualTo(kept.path("slot"));
		JsonNode shifted = day(manager, TOMORROW).path("occurrences").get(1);
		assertThat(shifted.path("slot").path("scheduled").asString()).isEqualTo("2026-10-27T09:00:00Z");
		assertThat(day(manager, "2026-10-27").path("occurrences")).isEmpty();
		assertThat(day(manager, "2026-11-01").path("occurrences").get(0).path("slot").path("scheduled").asString())
				.isEqualTo("2026-11-01T11:30:00Z");
	}

	@Test
	void expiryWeekdayValidationAndReplayableClaimClosure() throws Exception {
		String expiring = invite(caregiver, "CAREGIVER");
		jdbc.update("UPDATE tracking_invitation SET expires = ? WHERE patient_id = ?",
				java.sql.Timestamp.from(java.time.Instant.parse("2026-10-25T09:00:00Z")), UUID.fromString(patient));
		accept(caregiver, expiring).andExpect(status().isNotFound());
		accept(caregiver, invite(caregiver, "CAREGIVER")).andExpect(status().isOk());
		accept(patientLogin, invite(patientLogin, "PATIENT")).andExpect(status().isOk());
		call(manager, post(root + "/invitations"), json.createObjectNode().put("role", "PATIENT").put("email", replacement.email()))
				.andExpect(status().isConflict());

		ObjectNode create = request();
		ObjectNode weekdaysOnly = instruction("MEDICATION", YESTERDAY, "2026-10-27", "12:00", "16:00");
		weekdaysOnly.putArray("weekdays").add("MONDAY").add("TUESDAY");
		create.set("instruction", weekdaysOnly);
		call(manager, post(root + "/plans"), create).andExpect(status().isOk());
		assertThat(day(manager, YESTERDAY).path("occurrences")).isEmpty();
		assertThat(day(manager, TOMORROW).path("occurrences")).hasSize(2);
		assertThat(day(manager, "2026-10-27").path("occurrences")).hasSize(2);
		assertThat(day(manager, "2026-11-02").path("occurrences")).isEmpty();
		ObjectNode invalid = request();
		invalid.set("instruction", instruction("MEDICATION", TODAY, null, "10:00").put("dose", 0));
		call(manager, post(root + "/plans"), invalid).andExpect(status().isBadRequest());
		invalid.set("instruction", instruction("PROCEDURE", TODAY, null, "10:00").put("instructions", " "));
		call(manager, post(root + "/plans"), invalid).andExpect(status().isBadRequest());
		invalid.set("instruction", instruction("PROCEDURE", TODAY, null, "10:00", "10:00"));
		call(manager, post(root + "/plans"), invalid).andExpect(status().isBadRequest());

		JsonNode occurrence = day(manager, TOMORROW).path("occurrences").get(0);
		mutate(caregiver, action(occurrence, "CLAIM")).andExpect(status().isOk());
		ObjectNode staleRelease = action(occurrence, "RELEASE");
		staleRelease.set("expectedHandler", find(manager, occurrence).path("handler"));
		mutate(caregiver, staleRelease).andExpect(status().isOk());
		mutate(caregiver, action(occurrence, "CLAIM")).andExpect(status().isOk());
		mutate(caregiver, staleRelease.deepCopy().put("requestId", UUID.randomUUID().toString())).andExpect(status().isConflict());
		assertThat(find(manager, occurrence).path("handler").isObject()).isTrue();
		mutate(patientLogin, action(occurrence, "REPORT").put("actual", EARLY)).andExpect(status().isOk());
		assertThat(find(manager, occurrence).path("handler").isNull()).isTrue();
		var facts = jdbc.queryForList("SELECT payload FROM tracking_event WHERE patient_id = ? AND kind = 'OCCURRENCE' ORDER BY seq",
				String.class, UUID.fromString(patient)).stream().map(json::readTree).toList();
		assertThat(facts).hasSize(4);
		assertThat(facts.getLast().path("previous").path("actor").asString()).isEqualTo(caregiver.id());
		assertThat(facts.getLast().path("author").asString()).isEqualTo(patientLogin.id());
		assertThat(facts.getLast().path("next").isNull()).isTrue();
		assertThat(inbox()).isEmpty();
	}

	private int concurrentClaim(User user, JsonNode item, CountDownLatch ready) throws Exception {
		ready.countDown();
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		return mutate(user, action(item, "CLAIM")).andReturn().getResponse().getStatus();
	}

	private JsonNode find(User user, JsonNode item) throws Exception {
		for (JsonNode candidate : day(user, item.path("slot").path("day").asString()).path("occurrences")) {
			if (candidate.path("id").equals(item.path("id"))) return candidate;
		}
		throw new AssertionError("Missing occurrence " + item);
	}

	private void retract(User author, JsonNode item, long revision) throws Exception {
		mutate(author, action(item, "RETRACT").put("author", author.id()).put("reportRevision", revision)
				.put("reason", "Ошибочная запись")).andExpect(status().isOk());
	}

	private JsonNode inbox() throws Exception {
		return response(call(manager, get(root + "/inbox"), null).andExpect(status().isOk()));
	}

	private JsonNode day(User actor, String date) throws Exception {
		var request = get(root + "/day");
		if (date != null) request.param("date", date);
		return response(call(actor, request, null).andExpect(status().isOk()));
	}

	private ObjectNode profile(String zone) {
		return json.createObjectNode().put("name", "Подопечный").put("zone", zone);
	}

	private void create(String kind, String start, String end, String... times) throws Exception {
		ObjectNode plan = request();
		plan.set("instruction", instruction(kind, start, end, times));
		call(manager, post(root + "/plans"), plan).andExpect(status().isOk());
	}

	private ObjectNode instruction(String kind, String start, String end, String... times) {
		ObjectNode value = json.createObjectNode().put("title", "Уход").put("kind", kind)
				.put("dose", 2).put("unit", "таблетки").put("instructions", "Выполнить процедуру").put("start", start).put("end", end);
		var days = value.putArray("weekdays");
		if (times.length > 1) List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY").forEach(days::add);
		var scheduled = value.putArray("times");
		List.of(times).forEach(scheduled::add);
		return value;
	}

	private ObjectNode action(JsonNode item, String action) {
		return request().put("action", action).put("day", item.path("slot").path("day").asString())
				.put("occurrence", item.path("id").asString());
	}

	private ObjectNode request() {
		return json.createObjectNode().put("requestId", UUID.randomUUID().toString());
	}

	private User register(String name) throws Exception {
		String email = UUID.randomUUID() + "@test.ru";
		JsonNode user = response(call(null, post("/api/auth/register"),
				json.createObjectNode().put("email", email).put("password", PASSWORD).put("displayName", name))
				.andExpect(status().isCreated()));
		return new User(user.path("id").asString(), email);
	}

	private String invite(User invitee, String role) throws Exception {
		return response(call(manager, post(root + "/invitations"), json.createObjectNode().put("role", role).put("email", invitee.email()))
				.andExpect(status().isOk())).path("token").asString();
	}

	private ResultActions accept(User invitee, String token) throws Exception {
		return call(invitee, post(API + "/invitations/accept"), json.createObjectNode().put("token", token));
	}

	private ResultActions mutate(User user, ObjectNode body) throws Exception {
		return call(user, post(root + "/occurrences"), body);
	}

	private ResultActions call(User user, MockHttpServletRequestBuilder request, JsonNode body) throws Exception {
		if (user != null) request.with(httpBasic(user.email(), PASSWORD));
		if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		return mvc.perform(request);
	}

	private JsonNode response(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
	}
}
