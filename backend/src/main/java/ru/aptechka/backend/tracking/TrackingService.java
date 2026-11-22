package ru.aptechka.backend.tracking;

import static ru.aptechka.backend.common.Errors.*;
import static ru.aptechka.backend.tracking.TrackingAccess.*;
import static ru.aptechka.backend.tracking.TrackingState.*;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.aptechka.backend.account.CurrentUser;
import ru.aptechka.backend.tracking.TrackingStore.Patient;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
class TrackingService {
	record OccurrenceView(String id, Slot slot, String zone, Instruction instruction, Status status, Instant completedAt,
			long completionRevision, Handler handler, List<List<Revision>> reports) {}
	record DayView(LocalDate date, LocalDate today, List<OccurrenceView> occurrences) {}
	record Preview(long version, Instant effective, List<OccurrenceView> reported, List<OccurrenceView> claimed) {}
	record PlanInput(UUID requestId, UUID plan, Long version, Instant effective, Boolean stop, Instruction instruction) {
		boolean stopping() {
			return Boolean.TRUE.equals(stop);
		}
	}
	record OccurrenceInput(UUID requestId, LocalDate day, String occurrence, Action action, Instant actual,
			String reason, Long acknowledgment, UUID author, Long reportRevision, Handler expectedHandler) {}
	record ReadInput(UUID requestId, boolean read) {}
	record Result(long sequence) {}

	private final TrackingStore store;
	private final JsonMapper json;
	private final Clock clock;

	TrackingService(TrackingStore store, JsonMapper json, Clock clock) {
		this.store = store;
		this.json = json;
		this.clock = clock;
	}

	DayView day(CurrentUser actor, UUID patient, LocalDate day) {
		Patient profile = store.lock(patient);
		Role role = profile.role(actor.id());
		Instant now = clock.instant();
		LocalDate today = localDay(profile, now);
		LocalDate date = day == null ? today : day;
		TrackingState state = store.replay(patient);
		return new DayView(date, today, state.day(date).stream().map(slot -> view(state, slot, actor.id(), role, now)).toList());
	}

	List<Version> plans(CurrentUser actor, UUID patient) {
		requireManager(store.lock(patient).role(actor.id()));
		return List.copyOf(store.replay(patient).active.values());
	}

	List<OccurrenceView> claims(CurrentUser actor, UUID patient) {
		requireManager(store.lock(patient).role(actor.id()));
		TrackingState state = store.replay(patient);
		Instant now = clock.instant();
		return state.occurrences.values().stream().filter(o -> o.handler != null)
				.map(o -> view(state, o.slot, actor.id(), Role.MANAGER, now)).toList();
	}

	Preview preview(CurrentUser actor, UUID patient, UUID plan, Instant effective) {
		requireManager(store.lock(patient).role(actor.id()));
		TrackingState state = store.replay(patient);
		Version current = current(state, plan);
		Instant now = clock.instant();
		Instant cutoff = max(now, effective);
		List<OccurrenceView> reported = new ArrayList<>();
		List<OccurrenceView> claimed = new ArrayList<>();
		for (Occurrence occurrence : state.occurrences.values()) {
			if (!occurrence.slot.plan().equals(plan)) continue;
			OccurrenceView view = view(state, occurrence.slot, actor.id(), Role.MANAGER, now);
			if (occurrence.handler != null) claimed.add(view);
			if (!occurrence.slot.scheduled().isBefore(cutoff) && !occurrence.reports.isEmpty()) reported.add(view);
		}
		return new Preview(current.version(), cutoff, reported, claimed);
	}

	Result plan(CurrentUser actor, UUID patient, PlanInput input) {
		Patient profile = store.lock(patient);
		requireManager(profile.role(actor.id()));
		return once(actor, patient, input.requestId(), input, () -> {
			TrackingState state = store.replay(patient);
			Instant now = clock.instant();
			if (input.plan() == null) {
				if (input.stopping() || input.version() != null || input.effective() != null) {
					throw badRequest("Недопустимые параметры нового плана");
				}
			} else if (input.effective() == null || !Objects.equals(input.version(), current(state, input.plan()).version())) {
				throw conflict("План изменился, обновите предварительный просмотр");
			}
			UUID plan = input.plan() == null ? UUID.randomUUID() : input.plan();
			Instant effective = input.plan() == null ? Instant.MIN : max(now, input.effective());
			Version replacement = null;
			if (!input.stopping()) {
				if (input.instruction() == null) throw badRequest("Укажите расписание");
				input.instruction().validate();
				if (input.plan() == null) {
					effective = input.instruction().start().atStartOfDay(ZoneId.of(profile.zone())).toInstant();
				}
				replacement = new Version(plan, state.sequence + 1, profile.zone(), effective, input.instruction());
			}
			store.append(state, actor.id(), now, "PLAN",
					new PlanChange(plan, replacement, effective, input.stopping(), protectedSlots(state, plan, effective)));
			return new Result(state.sequence);
		});
	}

	static List<Slot> protectedSlots(TrackingState state, UUID plan, Instant effective) {
		return state.occurrences.values().stream()
				.filter(o -> o.slot.plan().equals(plan) && !o.slot.scheduled().isBefore(effective))
				.filter(o -> o.handler != null || !o.reports.isEmpty())
				.map(o -> o.slot).toList();
	}

	Result occurrence(CurrentUser actor, UUID patient, OccurrenceInput input) {
		Patient profile = store.lock(patient);
		Role role = profile.role(actor.id());
		if (input.action() == Action.CANCEL || input.action() == Action.REOPEN) requireManager(role);
		return once(actor, patient, input.requestId(), input, () -> {
			if (input.day() == null || !text(input.occurrence(), 100) || input.action() == null) {
				throw badRequest("Укажите событие и действие");
			}
			TrackingState state = store.replay(patient);
			Slot slot = state.day(input.day()).stream().filter(s -> s.id().equals(input.occurrence())).findFirst()
					.orElseThrow(() -> notFound("Событие не найдено"));
			Occurrence occurrence = state.occurrence(slot);
			Instant now = clock.instant();
			Status status = state.status(occurrence, now);
			Handler handler = occurrence.handler;
			Handler next = null;
			Instant actual = null;
			String notification = null;
			switch (input.action()) {
				case CLAIM -> {
					requireOpenIncomplete(status);
					if (handler != null) throw conflict("У события уже есть исполнитель");
					next = new Handler(actor.id(), actor.displayName(), now, state.sequence + 1);
				}
				case RELEASE -> {
					if (handler == null || !handler.actor().equals(actor.id())) {
						throw forbidden("Освободить задачу может её исполнитель");
					}
					checkHandler(input, handler);
				}
				case TAKEOVER -> {
					requireOpenIncomplete(status);
					requireReason(input);
					if (handler == null || handler.actor().equals(actor.id())) throw conflict("Исполнитель изменился");
					checkHandler(input, handler);
					next = new Handler(actor.id(), actor.displayName(), now, state.sequence + 1);
				}
				case REPORT -> {
					if (status == Status.CANCELLED) throw conflict("Сначала менеджер должен открыть событие");
					if (occurrence.reports.containsKey(actor.id())) throw conflict("У вас уже есть запись об этом событии");
					if (status == Status.COMPLETED && !Objects.equals(input.acknowledgment(), occurrence.completionRevision())
							|| status != Status.COMPLETED && input.acknowledgment() != null) {
						throw conflict("Статус выполнения изменился. Проверьте событие.");
					}
					actual = actualTime(input, now);
					if (slot.day().isBefore(localDay(profile, now))) notification = "PREVIOUS_DAY";
				}
				case CORRECT, RETRACT -> {
					if (!actor.id().equals(input.author())) throw forbidden("Можно изменить только свою запись");
					requireReason(input);
					List<Revision> lineage = occurrence.reports.get(actor.id());
					if (lineage == null || lineage.getLast().action() == Action.RETRACT
							|| !Objects.equals(input.reportRevision(), lineage.getLast().seq())) {
						throw conflict("Запись изменилась");
					}
					if (input.action() == Action.CORRECT) actual = actualTime(input, now);
					else if (occurrence.validReports().size() == 1) notification = "LAST_RETRACTED";
				}
				case CANCEL -> {
					if (handler != null || !occurrence.reports.isEmpty() || status == Status.CANCELLED) {
						throw conflict("Нельзя отменить защищённое или уже отменённое событие");
					}
				}
				case REOPEN -> {
					if (status != Status.CANCELLED) throw conflict("Событие не отменено");
				}
			}
			store.append(state, actor.id(), now, "OCCURRENCE", new OccurrenceChange(slot, input.action(), actual,
					text(input.reason(), 1000) ? input.reason().strip() : null, actor.id(), actor.displayName(), handler, next, notification));
			return new Result(state.sequence);
		});
	}

	List<Notice> inbox(CurrentUser actor, UUID patient) {
		requireManager(store.lock(patient).role(actor.id()));
		return store.replay(patient).inbox.values().stream().sorted(Comparator.comparingLong(Notice::id).reversed()).toList();
	}

	Result read(CurrentUser actor, UUID patient, long notification, ReadInput input) {
		requireManager(store.lock(patient).role(actor.id()));
		return once(actor, patient, input.requestId(), List.of(notification, input), () -> {
			TrackingState state = store.replay(patient);
			Notice notice = state.inbox.get(notification);
			if (notice == null) throw notFound("Уведомление не найдено");
			if (notice.read() != input.read()) {
				store.append(state, actor.id(), clock.instant(), "INBOX", new ReadChange(notification, input.read()));
			}
			return new Result(state.sequence);
		});
	}

	private Result once(CurrentUser actor, UUID patient, UUID key, Object input, Supplier<Result> operation) {
		if (key == null) throw badRequest("Отсутствует идентификатор запроса");
		String fingerprint = json.writeValueAsString(input);
		TrackingStore.Receipt receipt = store.receipt(patient, actor.id(), key);
		if (receipt != null) {
			if (!receipt.fingerprint().equals(fingerprint)) throw conflict("Идентификатор запроса уже использован");
			return json.readValue(receipt.response(), Result.class);
		}
		Result result = operation.get();
		store.receipt(patient, actor.id(), key, fingerprint, result);
		return result;
	}

	private static Version current(TrackingState state, UUID plan) {
		Version version = state.active.get(plan);
		if (version == null) throw notFound("Активный план не найден");
		return version;
	}

	private static OccurrenceView view(TrackingState state, Slot slot, UUID actor, Role role, Instant now) {
		Occurrence occurrence = state.occurrence(slot);
		List<List<Revision>> reports = occurrence.reports.entrySet().stream()
				.filter(entry -> role != Role.CAREGIVER || entry.getKey().equals(actor))
				.map(Map.Entry::getValue).toList();
		Version version = state.versions.get(slot.version());
		return new OccurrenceView(slot.id(), slot, version.zone(), version.instruction(),
				state.status(occurrence, now), occurrence.completionTime(), occurrence.completionRevision(), occurrence.handler, reports);
	}

	private static LocalDate localDay(Patient patient, Instant at) {
		return at.atZone(ZoneId.of(patient.zone())).toLocalDate();
	}

	private static void requireOpenIncomplete(Status status) {
		if (status == Status.CANCELLED || status == Status.COMPLETED) throw conflict("Событие закрыто");
	}

	private static Instant actualTime(OccurrenceInput input, Instant now) {
		if (input.actual() == null || input.actual().isAfter(now)) throw badRequest("Фактическое время должно быть не позже текущего");
		return input.actual();
	}

	private static void requireReason(OccurrenceInput input) {
		if (!text(input.reason(), 1000)) throw badRequest("Укажите причину");
	}

	private static void checkHandler(OccurrenceInput input, Handler handler) {
		if (!Objects.equals(input.expectedHandler(), handler)) throw conflict("Исполнитель изменился");
	}
}
