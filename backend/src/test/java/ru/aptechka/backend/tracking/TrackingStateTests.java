package ru.aptechka.backend.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.aptechka.backend.tracking.TrackingState.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TrackingStateTests {
	@Test
	void passingTheScheduledTimeDoesNotExpireClaimsOrCreateReports() {
		UUID patient = UUID.randomUUID();
		UUID plan = UUID.randomUUID();
		UUID author = UUID.randomUUID();
		Instant claimedAt = Instant.parse("2026-10-26T08:00:00Z");
		LocalDate day = LocalDate.parse("2026-10-26");
		LocalTime time = LocalTime.parse("09:00");
		Instruction instruction = new Instruction(Kind.PROCEDURE, "Уход", null, null,
				"Выполнить процедуру", day, null, List.of(), List.of(time));
		Version version = new Version(plan, 1, "UTC", claimedAt, instruction);
		TrackingState state = new TrackingState(patient);
		state.apply(1, new PlanChange(plan, version, claimedAt, false, List.of()));
		Slot slot = state.day(day).getFirst();
		Handler handler = new Handler(author, "Помощник", claimedAt, 2);
		state.apply(2, claimedAt, new OccurrenceChange(slot, Action.CLAIM, null, null,
				author, handler.name(), null, handler, null));

		Occurrence occurrence = state.occurrence(slot);
		assertThat(state.status(occurrence, claimedAt)).isEqualTo(Status.PENDING);
		assertThat(state.status(occurrence, Instant.parse("2026-11-26T12:00:00Z"))).isEqualTo(Status.OVERDUE);
		assertThat(occurrence.handler).isEqualTo(handler);
		assertThat(occurrence.reports).isEmpty();
		assertThat(state.inbox).isEmpty();
	}
}
