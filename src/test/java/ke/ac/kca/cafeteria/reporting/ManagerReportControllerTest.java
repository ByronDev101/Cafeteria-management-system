package ke.ac.kca.cafeteria.reporting;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ke.ac.kca.cafeteria.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ManagerReportController.class)
@Import(SecurityConfig.class)
class ManagerReportControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReportService reports;

    private static ManagerReport report(List<String> warnings, List<ReportRow> rows) {
        return new ManagerReport("2026-10-22", "2026-10-28", warnings, 7L,
                List.of(new ReportLine("Submitted", "status-submitted", 2L, "KSh 260.00"),
                        new ReportLine("Completed", "status-completed", 5L, "KSh 900.00")),
                List.of(new ReportLine("Awaiting payment", "pay-pending", 1L, "KSh 50.00"),
                        new ReportLine("Payment received", "pay-verified", 5L, "KSh 500.00")),
                List.of(new ReportLine("Cash", "", 3L, "KSh 300.00")),
                "", "", "", rows, false, 100);
    }

    private static ReportRow row() {
        return new ReportRow("SC-ABC123", "27 Oct 2026, 12:05", "Table 7", "Submitted", "status-submitted",
                "KSh 130.00", "Cash", "Awaiting payment", "pay-pending");
    }

    @BeforeEach
    void stubReport() {
        when(reports.build(any(), any(), any(), any(), any())).thenReturn(report(List.of(), List.of(row())));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void aManagerSeesTheReportAndTheOrdersFound() throws Exception {
        mvc.perform(get("/manager/reports"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Orders by status")))
           .andExpect(content().string(containsString("KSh 500.00")))
           .andExpect(content().string(containsString("SC-ABC123")))
           .andExpect(content().string(containsString("Awaiting payment")))
           .andExpect(content().string(containsString("prototype records")));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void theFiltersAreHandedToTheService() throws Exception {
        mvc.perform(get("/manager/reports").param("from", "2026-10-01").param("to", "2026-10-07")
                .param("status", "CANCELLED").param("number", "SC-A").param("table", "Table 7"))
           .andExpect(status().isOk());

        verify(reports).build("2026-10-01", "2026-10-07", "CANCELLED", "SC-A", "Table 7");
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void withNoFiltersTheServiceChoosesTheDefaultRange() throws Exception {
        mvc.perform(get("/manager/reports")).andExpect(status().isOk());
        verify(reports).build(null, null, null, null, null);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void aWarningAboutTheRangeIsShown() throws Exception {
        when(reports.build(any(), any(), any(), any(), any())).thenReturn(report(
                List.of("The start date was after the end date, so the last 7 days are shown."), List.of(row())));

        mvc.perform(get("/manager/reports").param("from", "2026-10-20").param("to", "2026-10-10"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("The start date was after the end date")));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void anEmptySearchSaysSo() throws Exception {
        when(reports.build(any(), any(), any(), any(), any())).thenReturn(report(List.of(), List.of()));

        mvc.perform(get("/manager/reports"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("No orders match these dates and filters.")));
    }

    @Test
    void everyoneButManagersIsRefused() throws Exception {
        for (String role : List.of("STAFF", "STUDENT", "ADMIN")) {
            mvc.perform(get("/manager/reports").with(user("someone").roles(role))).andExpect(status().isForbidden());
        }
        verify(reports, never()).build(any(), any(), any(), any(), any());
    }

    @Test
    void visitorsAreSentToSignIn() throws Exception {
        mvc.perform(get("/manager/reports")).andExpect(status().is3xxRedirection());
        verify(reports, never()).build(any(), any(), any(), any(), any());
    }
}
