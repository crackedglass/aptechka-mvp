package ru.aptechka.backend.tracking;

import static ru.aptechka.backend.common.Errors.badRequest;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

final class TrackingState {

	enum Kind { MEDICATION, PROCEDURE }
	enum Role { MANAGER, CAREGIVER, PATIENT }
	enum Action { CLAIM, RELEASE, TAKEOVER, REPORT, CORRECT, RETRACT, CANCEL, REOPEN }
	enum Status { PENDING, OVERDUE, COMPLETED, CANCELLED }

	record Instruction(Kind kind, String title, BigDecimal dose, String unit, String instructions,
			LocalDate start, LocalDate end, List<DayOfWeek> weekdays, List<LocalTime> times) {

		void validate() {
			if (kind == null || !text(title, 200) || start == null || weekdays == null || times == null
					|| times.isEmpty() || times.size() > 1440 || weekdays.stream().anyMatch(Objects::isNull) || times.stream().anyMatch(Objects::isNull)
					|| Set.copyOf(times).size() != times.size() || Set.copyOf(weekdays).size() != weekdays.size()
					|| times.stream().anyMatch(time -> time.getSecond() != 0 || time.getNano() != 0)
					|| end != null && end.isBefore(start)) {
				throw badRequest("Проверьте название, даты и время расписания");
			}
			if (weekdays.isEmpty() && (times.size() != 1 || end != null && !end.equals(start))) {
				throw badRequest("У разового события одна дата и одно время");
			}
			if (kind == Kind.MEDICATION && (dose == null || dose.signum() <= 0 || !text(unit, 60))
					|| kind == Kind.PROCEDURE && !text(instructions, 4000)) {
				throw badRequest("Укажите полную дозу и единицу или инструкцию процедуры");
			}
		}

		boolean occursOn(LocalDate day) {
			return !day.isBefore(start) && (end == null || !day.isAfter(end))
					&& (weekdays.isEmpty() ? day.equals(start) : weekdays.contains(day.getDayOfWeek()));
		}
	}

	record Version(UUID plan, long version, String zone, Instant from, Instruction instruction) {}
	record PlanChange(UUID plan, Version replacement, Instant effective, boolean stop, List<Slot> exceptions) {}
	record Slot(UUID plan, long version, LocalDate day, LocalTime time, Instant scheduled) {
		String id() {
			return version + "/" + day + "/" + time;
		}
	}
	record Handler(UUID actor, String name, Instant since, long revision) {}
	record Revision(long seq, Action action, UUID author, String authorName, UUID patient, Instant actual, Instant recorded, String reason) {}
	record OccurrenceChange(Slot slot, Action action, Instant actual, String reason, UUID author, String authorName,
			Handler previous, Handler next, String notification) {}
	record ReadChange(long notification, boolean read) {}
	record Notice(long id, String kind, Slot slot, String title, Instant recorded, boolean read) {}
	record ZoneChange(long previous, Version replacement, Instant effective, List<Slot> exceptions) {}
	record Stop(Instant at, long seq) {}
	private record NominalSlot(UUID plan, LocalTime time) {}

	static final class Occurrence {
		final Slot slot;
		final Map<UUID, List<Revision>> reports = new LinkedHashMap<>();
		Handler handler;
		Boolean cancelled;
		long lifecycleRevision;

		Occurrence(Slot slot) {
			this.slot = slot;
		}

		List<Revision> validReports() {
			return reports.values().stream().map(List::getLast)
					.filter(report -> report.action() != Action.RETRACT).toList();
		}

		long completionRevision() {
			if (validReports().isEmpty()) return 0;
			return reports.values().stream().map(List::getLast).mapToLong(Revision::seq).max().orElseThrow();
		}

		Instant completionTime() {
			return validReports().stream().map(Revision::actual).min(Comparator.naturalOrder()).orElse(null);
		}
	}

	final UUID patient;
	final Map<UUID, Version> active = new LinkedHashMap<>();
	final Map<Long, Version> versions = new LinkedHashMap<>();
	final Map<Long, Instant> closures = new HashMap<>();
	final Map<UUID, Stop> stops = new HashMap<>();
	final Map<String, Occurrence> occurrences = new LinkedHashMap<>();
	final Map<String, Slot> exceptions = new LinkedHashMap<>();
	final Map<Long, Notice> inbox = new LinkedHashMap<>();
	long sequence;

	TrackingState(UUID patient) {
		this.patient = patient;
	}

	void apply(long seq, PlanChange change) {
		Version previous = active.remove(change.plan());
		if (change.stop()) {
			stops.put(change.plan(), new Stop(change.effective(), seq));
		} else if (previous != null) {
			for (Version version : versions.values()) {
				Instant closure = closures.get(version.version());
				if (version.plan().equals(change.plan()) && (closure == null || closure.isAfter(change.effective()))) {
					closures.put(version.version(), change.effective());
				}
			}
		}
		exceptions.values().removeIf(slot -> slot.plan().equals(change.plan()) && !slot.scheduled().isBefore(change.effective()));
		for (Slot slot : change.exceptions()) {
			exceptions.put(slot.id(), slot);
		}
		Version replacement = change.replacement();
		if (replacement != null) {
			active.put(change.plan(), replacement);
			versions.put(replacement.version(), replacement);
		}
	}

	void apply(ZoneChange change) {
		Instant previousEnd = closures.put(change.previous(), change.effective());
		Version replacement = change.replacement();
		versions.put(replacement.version(), replacement);
		if (previousEnd != null) {
			closures.put(replacement.version(), previousEnd);
		}
		if (active.get(replacement.plan()).version() == change.previous()) {
			active.put(replacement.plan(), replacement);
		}
		for (Slot slot : change.exceptions()) {
			exceptions.put(slot.id(), slot);
		}
	}

	void apply(long seq, Instant recorded, OccurrenceChange change) {
		Occurrence occurrence = occurrences.computeIfAbsent(change.slot().id(), key -> new Occurrence(change.slot()));
		switch (change.action()) {
			case CLAIM, RELEASE, TAKEOVER -> occurrence.handler = change.next();
			case CANCEL, REOPEN -> {
				occurrence.cancelled = change.action() == Action.CANCEL;
				occurrence.lifecycleRevision = seq;
			}
			case REPORT, CORRECT, RETRACT -> {
				occurrence.reports.computeIfAbsent(change.author(), key -> new ArrayList<>()).add(
						new Revision(seq, change.action(), change.author(), change.authorName(), patient, change.actual(), recorded, change.reason()));
				if (change.action() == Action.REPORT) {
					occurrence.handler = null;
				}
			}
		}
		if (change.notification() != null) {
			inbox.put(seq, new Notice(seq, change.notification(), change.slot(),
					versions.get(change.slot().version()).instruction().title(), recorded, false));
		}
	}

	void apply(ReadChange change) {
		Notice notice = inbox.get(change.notification());
		inbox.put(notice.id(), new Notice(notice.id(), notice.kind(), notice.slot(), notice.title(), notice.recorded(), change.read()));
	}

	List<Slot> day(LocalDate date) {
		Map<String, Slot> slots = new LinkedHashMap<>();
		for (Version version : versions.values()) {
			if (!version.instruction().occursOn(date)) {
				continue;
			}
			for (LocalTime time : version.instruction().times()) {
				Instant scheduled = date.atTime(time).atZone(ZoneId.of(version.zone())).toInstant();
				Slot slot = new Slot(version.plan(), version.version(), date, time, scheduled);
				if (visible(slot, version)) {
					slots.put(slot.id(), slot);
				}
			}
		}
		// Reuse the original scheduled instant for slots that were referenced by a command.
		for (Occurrence occurrence : occurrences.values()) {
			if (slots.containsKey(occurrence.slot.id())) {
				slots.put(occurrence.slot.id(), occurrence.slot);
			}
		}
		for (Slot slot : exceptions.values()) {
			if (slot.day().equals(date)) {
				slots.put(slot.id(), slot);
			}
		}
		// Changing zones must not add a second slot for care already scheduled on the same local day.
		Map<NominalSlot, Slot> unique = new LinkedHashMap<>();
		for (Slot slot : slots.values()) {
			unique.merge(new NominalSlot(slot.plan(), slot.time()), slot,
					(first, second) -> first.version() < second.version() ? first : second);
		}
		return unique.values().stream().sorted(Comparator.comparing(Slot::scheduled).thenComparingLong(Slot::version)).toList();
	}

	private boolean visible(Slot slot, Version version) {
		Instant closure = closures.get(version.version());
		if (slot.scheduled().isBefore(version.from())
				|| closure != null && !slot.scheduled().isBefore(closure)) {
			return false;
		}
		return exceptions.values().stream().noneMatch(exception -> exception.plan().equals(slot.plan())
				&& exception.day().equals(slot.day()) && exception.time().equals(slot.time())
				&& exception.version() < slot.version());
	}

	Occurrence occurrence(Slot slot) {
		return occurrences.getOrDefault(slot.id(), new Occurrence(slot));
	}

	Status status(Occurrence occurrence, Instant now) {
		Stop stop = stops.get(occurrence.slot.plan());
		boolean stopped = stop != null && !occurrence.slot.scheduled().isBefore(stop.at())
				&& !exceptions.containsKey(occurrence.slot.id()) && occurrence.lifecycleRevision < stop.seq();
		if (Boolean.TRUE.equals(occurrence.cancelled) || stopped) {
			return Status.CANCELLED;
		}
		if (occurrence.completionRevision() != 0) {
			return Status.COMPLETED;
		}
		return now.isAfter(occurrence.slot.scheduled()) ? Status.OVERDUE : Status.PENDING;
	}

	static boolean text(String value, int max) {
		return value != null && !value.isBlank() && value.length() <= max;
	}
}
