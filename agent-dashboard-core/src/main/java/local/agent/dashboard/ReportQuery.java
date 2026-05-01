package local.agent.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

record ReportQuery(String kind, LocalDate startDate, LocalDate endDate, ZoneId zone) {
    static ReportQuery from(Map<String, String> query, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        String month = query.get("month");
        if (month != null && !month.isBlank()) {
            YearMonth yearMonth = "current".equals(month) ? YearMonth.from(today) : YearMonth.parse(month);
            return new ReportQuery("month", yearMonth.atDay(1), yearMonth.atEndOfMonth(), zone);
        }

        String days = query.get("days");
        int dayCount = days == null || days.isBlank() ? 7 : Integer.parseInt(days);
        if (dayCount != 1 && dayCount != 7 && dayCount != 30) {
            throw new BadRequestException("days must be one of 1, 7, or 30");
        }
        return new ReportQuery("days", today.minusDays(dayCount - 1L), today, zone);
    }

    boolean contains(Instant instant) {
        LocalDate date = instant.atZone(zone).toLocalDate();
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
