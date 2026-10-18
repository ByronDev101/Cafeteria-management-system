package ke.ac.kca.cafeteria.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ke.ac.kca.cafeteria.reporting.AuditService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;

class TableServiceTest {

    private CafeteriaTableRepository tables;
    private AuditService audit;
    private TableService service;

    @BeforeEach
    void setUp() {
        tables = mock(CafeteriaTableRepository.class);
        audit = mock(AuditService.class);
        service = new TableService(tables, audit, "Africa/Nairobi");
    }

    /** A stored table plus the plain token that its hash was made from. */
    private record Stored(CafeteriaTable table, String token) {
    }

    private Stored stored(String label) {
        String token = TableToken.generate();
        CafeteriaTable table = new CafeteriaTable(label, TableToken.hash(token));
        when(tables.findByPublicId(table.getPublicId())).thenReturn(Optional.of(table));
        when(tables.findByTokenHash(TableToken.hash(token))).thenReturn(Optional.of(table));
        return new Stored(table, token);
    }

    // ---- creating ----

    @Test
    void createStoresOnlyTheHashAndTheAuditEntryNeverContainsTheToken() {
        IssuedQr issued = service.create("admin", "Table 7");

        ArgumentCaptor<CafeteriaTable> saved = ArgumentCaptor.forClass(CafeteriaTable.class);
        verify(tables).save(saved.capture());
        assertEquals("Table 7", saved.getValue().getLabel());
        assertEquals(TableToken.hash(issued.token()), saved.getValue().getTokenHash());
        assertNotEquals(issued.token(), saved.getValue().getTokenHash());
        assertTrue(saved.getValue().isActive());
        verify(audit).record(eq("admin"), eq("TABLE_CREATED"), eq("TABLE"), eq(saved.getValue().getPublicId()),
                eq("SUCCESS"), argThat(details -> !details.contains(issued.token())));
    }

    @Test
    void labelsAreTidiedAndBadOnesRejected() {
        assertEquals("Table 7", service.create("admin", "  Table    7 ").label());

        for (String bad : new String[] {"", "   ", "Table@7", "-Table", "x".repeat(41)}) {
            assertThrows(TableException.class, () -> service.create("admin", bad), bad);
        }
        when(tables.existsByLabelIgnoreCase("Table 8")).thenReturn(true);
        assertThrows(TableException.class, () -> service.create("admin", "Table 8"));
    }

    // ---- changing ----

    @Test
    void renameIsAuditedOnlyWhenTheLabelChanges() {
        Stored t = stored("Table 1");
        service.rename("admin", t.table().getPublicId(), "Terrace 1");
        assertEquals("Terrace 1", t.table().getLabel());
        verify(audit).record(eq("admin"), eq("TABLE_RENAMED"), eq("TABLE"), eq(t.table().getPublicId()),
                eq("SUCCESS"), argThat(d -> d.contains("Table 1 -> Terrace 1")));

        service.rename("admin", t.table().getPublicId(), "Terrace 1");
        verify(audit, org.mockito.Mockito.times(1)).record(any(), eq("TABLE_RENAMED"), any(), any(), any(), any());
    }

    @Test
    void renameRejectsDuplicateLabel() {
        Stored t = stored("Table 1");
        when(tables.existsByLabelIgnoreCaseAndIdNot(eq("Table 2"), any())).thenReturn(true);
        assertThrows(TableException.class, () -> service.rename("admin", t.table().getPublicId(), "Table 2"));
        assertEquals("Table 1", t.table().getLabel());
    }

    @Test
    void deactivatingKillsTheOldTokenForGood() {
        Stored t = stored("Table 1");
        String oldHash = t.table().getTokenHash();

        service.deactivate("admin", t.table().getPublicId());

        assertFalse(t.table().isActive());
        assertNotEquals(oldHash, t.table().getTokenHash());
        verify(audit).record(eq("admin"), eq("TABLE_DEACTIVATED"), eq("TABLE"), eq(t.table().getPublicId()),
                eq("SUCCESS"), anyString());
    }

    @Test
    void reactivatingIssuesABrandNewToken() {
        Stored t = stored("Table 1");
        service.deactivate("admin", t.table().getPublicId());
        String hashWhileInactive = t.table().getTokenHash();

        IssuedQr issued = service.reactivate("admin", t.table().getPublicId());

        assertTrue(t.table().isActive());
        assertEquals(TableToken.hash(issued.token()), t.table().getTokenHash());
        assertNotEquals(hashWhileInactive, t.table().getTokenHash());
        assertNotEquals(t.token(), issued.token());
        assertThrows(TableException.class, () -> service.reactivate("admin", t.table().getPublicId()));
    }

    @Test
    void rotatingNeedsAnActiveTableAndReplacesTheToken() {
        Stored t = stored("Table 1");
        IssuedQr issued = service.rotate("admin", t.table().getPublicId());
        assertEquals(TableToken.hash(issued.token()), t.table().getTokenHash());
        assertNotEquals(t.token(), issued.token());

        service.deactivate("admin", t.table().getPublicId());
        assertThrows(TableException.class, () -> service.rotate("admin", t.table().getPublicId()));
    }

    @Test
    void unknownTableGivesASafeMessage() {
        when(tables.findByPublicId("nope")).thenReturn(Optional.empty());
        TableException e = assertThrows(TableException.class, () -> service.deactivate("admin", "nope"));
        assertEquals("That table was not found.", e.getMessage());
    }

    // ---- resolving tokens ----

    @Test
    void validTokenResolvesToItsTableInEveryReasonableWritingStyle() {
        Stored t = stored("Table 7");
        for (String input : new String[] {
                t.token(), TableToken.display(t.token()), TableToken.display(t.token()).toLowerCase(),
                "https://192.168.1.20:8080/t/" + t.token()}) {
            TableResolution result = service.resolve(input);
            assertTrue(result.isValid(), input);
            assertEquals("Table 7", result.table().getLabel());
            assertEquals(t.table().getPublicId(), result.table().getPublicId());
        }
    }

    @Test
    void malformedTokenIsRejectedWithoutTouchingTheDatabase() {
        assertEquals(TableResolution.Status.MALFORMED, service.resolve("abc").status());
        assertEquals(TableResolution.Status.MALFORMED, service.resolve("' OR 1=1 --").status());
        assertEquals(TableResolution.Status.MALFORMED, service.resolve(null).status());
        verify(tables, never()).findByTokenHash(any());
    }

    @Test
    void unknownTokenIsRejected() {
        when(tables.findByTokenHash(any())).thenReturn(Optional.empty());
        TableResolution result = service.resolve(TableToken.generate());
        assertEquals(TableResolution.Status.UNKNOWN, result.status());
        assertEquals(null, result.table());
    }

    @Test
    void inactiveTableTokenIsRejected() {
        Stored t = stored("Table 7");
        t.table().deactivate("0".repeat(64));
        when(tables.findByTokenHash(TableToken.hash(t.token()))).thenReturn(Optional.of(t.table()));
        assertEquals(TableResolution.Status.INACTIVE, service.resolve(t.token()).status());
    }

    // ---- the student's current table ----

    @Test
    void currentTableIsCheckedAgainstTheDatabaseEveryTime() {
        Stored t = stored("Table 7");
        MockHttpSession session = new MockHttpSession();

        assertTrue(service.currentTable(session).isEmpty());

        TableSession.store(session, new SelectedTable(t.table().getPublicId(), "Table 7"));
        assertEquals("Table 7", service.currentTable(session).orElseThrow().getLabel());

        service.rename("admin", t.table().getPublicId(), "Terrace 7");
        assertEquals("Terrace 7", service.currentTable(session).orElseThrow().getLabel());

        service.deactivate("admin", t.table().getPublicId());
        assertTrue(service.currentTable(session).isEmpty());
        assertTrue(TableSession.read(session).isEmpty(), "a switched-off table is dropped from the session");
    }

    // ---- listing ----

    @Test
    void tablesAreListedInNaturalOrder() {
        CafeteriaTable ten = new CafeteriaTable("Table 10", "a".repeat(64));
        CafeteriaTable two = new CafeteriaTable("Table 2", "b".repeat(64));
        CafeteriaTable one = new CafeteriaTable("Table 1", "c".repeat(64));
        CafeteriaTable terrace = new CafeteriaTable("Terrace 1", "d".repeat(64));
        when(tables.findAll()).thenReturn(List.of(terrace, ten, two, one));

        List<String> labels = service.list().stream().map(TableView::getLabel).toList();

        assertEquals(List.of("Table 1", "Table 2", "Table 10", "Terrace 1"), labels);
    }

    // ---- used by ordering ----

    @Test
    void activeTableIsFoundOnlyWhileSwitchedOn() {
        Stored t = stored("Table 7");
        TableRef found = service.activeTable(t.table().getPublicId()).orElseThrow();
        assertEquals("Table 7", found.label());
        assertEquals(t.table().getPublicId(), found.publicId());

        service.deactivate("admin", t.table().getPublicId());
        assertTrue(service.activeTable(t.table().getPublicId()).isEmpty());
        assertTrue(service.activeTable("nope").isEmpty());
    }
}
