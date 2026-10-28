package ke.ac.kca.cafeteria.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ReportRangeTest {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    /** 21:30 UTC on 27 Oct is already 00:30 on 28 Oct in Nairobi. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T21:30:00Z"), ZoneOffset.UTC);

    private static ReportRange parse(String from, String to) {
        return ReportRange.parse(from, to, NAIROBI, CLOCK);
    }

    @Test
    void withNothingChosenItIsTheLastSevenDaysEndingTodayInNairobi() {
        ReportRange range = parse(null, null);

        assertEquals(LocalDate.of(2026, 10, 22), range.getFrom());
        assertEquals(LocalDate.of(2026, 10, 28), range.getTo());
        assertEquals("2026-10-22", range.getFromText());
        assertEquals("2026-10-28", range.getToText());
        assertTrue(range.getWarnings().isEmpty());
    }

    @Test
    void blankTextCountsAsNothingChosen() {
        ReportRange range = parse("  ", "");
        assertEquals(LocalDate.of(2026, 10, 22), range.getFrom());
        assertTrue(range.getWarnings().isEmpty());
    }

    @Test
    void aChosenRangeIsUsedAndBothEndDaysAreIncluded() {
        ReportRange range = parse("2026-10-01", "2026-10-07");

        assertEquals(LocalDate.of(2026, 10, 1), range.getFrom());
        assertEquals(LocalDate.of(2026, 10, 7), range.getTo());
        assertTrue(range.getWarnings().isEmpty());
        // Nairobi is 3 hours ahead of UTC, so the first day begins at 21:00 UTC the evening before.
        assertEquals(Instant.parse("2026-09-30T21:00:00Z"), range.start());
        assertEquals(Instant.parse("2026-10-07T21:00:00Z"), range.endExclusive());
    }

    @Test
    void aSingleDayIsAllowed() {
        ReportRange range = parse("2026-10-05", "2026-10-05");
        assertTrue(range.getWarnings().isEmpty());
        assertEquals(Instant.parse("2026-10-04T21:00:00Z"), range.start());
        assertEquals(Instant.parse("2026-10-05T21:00:00Z"), range.endExclusive());
    }

    @Test
    void aStartAfterTheEndFallsBackWithAWarning() {
        ReportRange range = parse("2026-10-20", "2026-10-10");

        assertEquals(LocalDate.of(2026, 10, 22), range.getFrom());
        assertEquals(LocalDate.of(2026, 10, 28), range.getTo());
        assertEquals(1, range.getWarnings().size());
        assertTrue(range.getWarnings().get(0).contains("start date was after the end date"));
    }

    @Test
    void textThatIsNotADateFallsBackWithAWarning() {
        for (String bad : new String[] {"yesterday", "2026-13-45", "' OR 1=1 --", "2026/10/01", "<script>"}) {
            ReportRange range = parse(bad, "2026-10-07");
            assertEquals(LocalDate.of(2026, 10, 22), range.getFrom(), bad);
            assertEquals(1, range.getWarnings().size(), bad);
            assertTrue(range.getWarnings().get(0).contains("not valid"), bad);
        }
    }

    @Test
    void absurdYearsFallBackInsteadOfFailing() {
        for (String bad : new String[] {"0001-01-01", "9999-12-31", "+999999999-01-01"}) {
            ReportRange range = parse(bad, null);
            assertEquals(LocalDate.of(2026, 10, 28), range.getTo(), bad);
            assertEquals(1, range.getWarnings().size(), bad);
        }
    }

    @Test
    void theLongestAllowedRangeIs366Days() {
        ReportRange leapYear = parse("2024-01-01", "2024-12-31");
        assertEquals(LocalDate.of(2024, 1, 1), leapYear.getFrom());
        assertTrue(leapYear.getWarnings().isEmpty());

        ReportRange tooLong = parse("2024-01-01", "2025-01-01");
        assertEquals(LocalDate.of(2026, 10, 22), tooLong.getFrom());
        assertEquals(1, tooLong.getWarnings().size());
        assertTrue(tooLong.getWarnings().get(0).contains("366"));
    }

    @Test
    void aMissingEndMeansTodayAndAMissingStartMeansAWeekBeforeTheEnd() {
        ReportRange onlyStart = parse("2026-10-20", null);
        assertEquals(LocalDate.of(2026, 10, 20), onlyStart.getFrom());
        assertEquals(LocalDate.of(2026, 10, 28), onlyStart.getTo());
        assertTrue(onlyStart.getWarnings().isEmpty());

        ReportRange onlyEnd = parse(null, "2026-10-10");
        assertEquals(LocalDate.of(2026, 10, 4), onlyEnd.getFrom());
        assertEquals(LocalDate.of(2026, 10, 10), onlyEnd.getTo());
        assertTrue(onlyEnd.getWarnings().isEmpty());
    }
}
