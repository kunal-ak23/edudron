package com.datagami.edudron.student.util;

import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.CalendarEvent;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Parses iCal RRULE strings and generates materialized CalendarEvent occurrences.
 *
 * Supported rules:
 * - FREQ=DAILY with optional INTERVAL
 * - FREQ=WEEKLY with optional INTERVAL and BYDAY=MO,WE,FR
 * - FREQ=MONTHLY with optional INTERVAL
 *
 * End conditions: COUNT=N, UNTIL=YYYYMMDD, or default 6-month horizon.
 * Safety limit: max 200 occurrences.
 */
public class RecurrenceGenerator {

    private static final int MAX_OCCURRENCES = 200;
    private static final int DEFAULT_HORIZON_MONTHS = 6;
    private static final DateTimeFormatter UNTIL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Map<String, DayOfWeek> DAY_MAP = Map.of(
            "MO", DayOfWeek.MONDAY,
            "TU", DayOfWeek.TUESDAY,
            "WE", DayOfWeek.WEDNESDAY,
            "TH", DayOfWeek.THURSDAY,
            "FR", DayOfWeek.FRIDAY,
            "SA", DayOfWeek.SATURDAY,
            "SU", DayOfWeek.SUNDAY
    );

    private RecurrenceGenerator() {
        // utility class
    }

    /**
     * Generate materialized occurrences from a recurring parent event.
     * The parent event itself is NOT included in the returned list.
     */
    public static List<CalendarEvent> generateOccurrences(CalendarEvent parent) {
        if (parent == null || !parent.isRecurring() || parent.getRecurrenceRule() == null) {
            return Collections.emptyList();
        }

        Map<String, String> ruleParts = parseRule(parent.getRecurrenceRule());

        String freq = ruleParts.get("FREQ");
        if (freq == null) {
            return Collections.emptyList();
        }

        int interval = Integer.parseInt(ruleParts.getOrDefault("INTERVAL", "1"));
        Integer count = ruleParts.containsKey("COUNT") ? Integer.parseInt(ruleParts.get("COUNT")) : null;
        LocalDate untilDate = ruleParts.containsKey("UNTIL")
                ? LocalDate.parse(ruleParts.get("UNTIL"), UNTIL_FORMAT)
                : null;

        // Default horizon if neither COUNT nor UNTIL
        OffsetDateTime horizon = (count == null && untilDate == null)
                ? parent.getStartDateTime().plusMonths(DEFAULT_HORIZON_MONTHS)
                : null;

        // Parse BYDAY for weekly
        Set<DayOfWeek> byDays = new LinkedHashSet<>();
        if (ruleParts.containsKey("BYDAY")) {
            for (String day : ruleParts.get("BYDAY").split(",")) {
                DayOfWeek dow = DAY_MAP.get(day.trim().toUpperCase());
                if (dow != null) {
                    byDays.add(dow);
                }
            }
        }

        // Calculate duration to preserve on each occurrence
        Duration duration = (parent.getEndDateTime() != null)
                ? Duration.between(parent.getStartDateTime(), parent.getEndDateTime())
                : null;

        List<CalendarEvent> occurrences = new ArrayList<>();

        switch (freq.toUpperCase()) {
            case "DAILY":
                generateDaily(parent, interval, count, untilDate, horizon, duration, occurrences);
                break;
            case "WEEKLY":
                generateWeekly(parent, interval, byDays, count, untilDate, horizon, duration, occurrences);
                break;
            case "MONTHLY":
                generateMonthly(parent, interval, count, untilDate, horizon, duration, occurrences);
                break;
            default:
                break;
        }

        return occurrences;
    }

    private static void generateDaily(CalendarEvent parent, int interval,
                                      Integer count, LocalDate untilDate, OffsetDateTime horizon,
                                      Duration duration, List<CalendarEvent> occurrences) {
        OffsetDateTime current = parent.getStartDateTime();
        int generated = 0;

        while (generated < MAX_OCCURRENCES) {
            current = current.plusDays(interval);
            if (shouldStop(current, count, generated, untilDate, horizon)) break;

            occurrences.add(createOccurrence(parent, current, duration));
            generated++;
        }
    }

    private static void generateWeekly(CalendarEvent parent, int interval, Set<DayOfWeek> byDays,
                                       Integer count, LocalDate untilDate, OffsetDateTime horizon,
                                       Duration duration, List<CalendarEvent> occurrences) {
        if (byDays.isEmpty()) {
            // No BYDAY -- treat like daily but in week-interval steps
            OffsetDateTime current = parent.getStartDateTime();
            int generated = 0;

            while (generated < MAX_OCCURRENCES) {
                current = current.plusWeeks(interval);
                if (shouldStop(current, count, generated, untilDate, horizon)) break;

                occurrences.add(createOccurrence(parent, current, duration));
                generated++;
            }
        } else {
            // BYDAY specified: iterate week by week, emit for each matching day
            OffsetDateTime baseStart = parent.getStartDateTime();
            LocalDate weekStart = baseStart.toLocalDate().with(DayOfWeek.MONDAY);
            int generated = 0;
            boolean firstWeek = true;

            while (generated < MAX_OCCURRENCES) {
                for (DayOfWeek day : sortedDays(byDays)) {
                    LocalDate candidateDate = weekStart.with(day);
                    OffsetDateTime candidateTime = candidateDate.atTime(baseStart.toLocalTime())
                            .atOffset(baseStart.getOffset());

                    // Skip dates on or before the parent's start
                    if (!candidateTime.isAfter(baseStart)) {
                        continue;
                    }
                    if (shouldStop(candidateTime, count, generated, untilDate, horizon)) {
                        return;
                    }

                    occurrences.add(createOccurrence(parent, candidateTime, duration));
                    generated++;
                    if (generated >= MAX_OCCURRENCES) return;
                }

                weekStart = weekStart.plusWeeks(firstWeek ? interval : interval);
                firstWeek = false;
            }
        }
    }

    private static void generateMonthly(CalendarEvent parent, int interval,
                                        Integer count, LocalDate untilDate, OffsetDateTime horizon,
                                        Duration duration, List<CalendarEvent> occurrences) {
        OffsetDateTime current = parent.getStartDateTime();
        int generated = 0;

        while (generated < MAX_OCCURRENCES) {
            current = current.plusMonths(interval);
            if (shouldStop(current, count, generated, untilDate, horizon)) break;

            occurrences.add(createOccurrence(parent, current, duration));
            generated++;
        }
    }

    private static boolean shouldStop(OffsetDateTime candidate, Integer count, int generated,
                                      LocalDate untilDate, OffsetDateTime horizon) {
        if (count != null && generated >= count) return true;
        if (untilDate != null && candidate.toLocalDate().isAfter(untilDate)) return true;
        if (horizon != null && candidate.isAfter(horizon)) return true;
        return false;
    }

    private static CalendarEvent createOccurrence(CalendarEvent parent, OffsetDateTime start, Duration duration) {
        CalendarEvent occurrence = new CalendarEvent();
        occurrence.setId(UlidGenerator.nextUlid());
        occurrence.setClientId(parent.getClientId());
        occurrence.setTitle(parent.getTitle());
        occurrence.setDescription(parent.getDescription());
        occurrence.setEventType(parent.getEventType());
        occurrence.setCustomTypeLabel(parent.getCustomTypeLabel());
        occurrence.setStartDateTime(start);
        occurrence.setEndDateTime(duration != null ? start.plus(duration) : null);
        occurrence.setAllDay(parent.isAllDay());
        occurrence.setAudience(parent.getAudience());
        occurrence.setClassIds(parent.getClassIds());
        occurrence.setSectionIds(parent.getSectionIds());
        occurrence.setTargetUserIds(parent.getTargetUserIds());
        occurrence.setCreatedByUserId(parent.getCreatedByUserId());
        occurrence.setRecurring(false);
        occurrence.setRecurrenceRule(null);
        occurrence.setRecurrenceParentId(parent.getId());
        occurrence.setMeetingLink(parent.getMeetingLink());
        occurrence.setLocation(parent.getLocation());
        occurrence.setColor(parent.getColor());
        occurrence.setMetadata(parent.getMetadata());
        occurrence.setActive(true);

        OffsetDateTime now = OffsetDateTime.now();
        occurrence.setCreatedAt(now);
        occurrence.setUpdatedAt(now);

        return occurrence;
    }

    private static Map<String, String> parseRule(String rrule) {
        Map<String, String> parts = new HashMap<>();
        // Strip optional "RRULE:" prefix
        String rule = rrule.startsWith("RRULE:") ? rrule.substring(6) : rrule;
        for (String part : rule.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                parts.put(kv[0].trim().toUpperCase(), kv[1].trim());
            }
        }
        return parts;
    }

    private static List<DayOfWeek> sortedDays(Set<DayOfWeek> days) {
        List<DayOfWeek> sorted = new ArrayList<>(days);
        Collections.sort(sorted);
        return sorted;
    }
}
