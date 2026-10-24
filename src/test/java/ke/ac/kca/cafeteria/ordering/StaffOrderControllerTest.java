package ke.ac.kca.cafeteria.ordering;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ke.ac.kca.cafeteria.config.SecurityConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StaffOrderController.class)
@Import(SecurityConfig.class)
class StaffOrderControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderProcessingService processing;

    @BeforeEach
    void stubBoard() {
        QueueOrder fresh = new QueueOrder("o1", "SC-ABC123", "SUBMITTED", "Submitted", "Table 7", "Amina Wanjiru", "12:05", "3 min ago",
                List.of("2 x Tea", "1 x Samosa"), "KSh 130.00", "Cash", 4L, "ACCEPTED", "Accept order", true, null);
        QueueOrder cancelled = new QueueOrder("o2", "SC-DEF456", "CANCELLED", "Cancelled", "Table 2", "Brian Otieno", "11:00",
                "1 h 5 min ago", List.of("1 x Tea"), "KSh 50.00", "M-Pesa till", 2L, null, null, false,
                "Cancelled by staff: Out of stock");
        when(processing.queue(any(QueueFilter.class))).thenReturn(List.of(fresh, cancelled));
        when(processing.counts()).thenReturn(Map.of(QueueFilter.ACTIVE, 3L, QueueFilter.NEW, 1L,
                QueueFilter.KITCHEN, 1L, QueueFilter.READY, 1L));
        when(processing.pollToken()).thenReturn("SUBMITTED:1:100");
    }

    @Test
    @WithMockUser(username = "sam", roles = "STAFF")
    void staffSeeTheBoardInEveryView() throws Exception {
        mvc.perform(get("/staff/orders")).andExpect(status().isOk());
        mvc.perform(get("/staff/orders").param("filter", "NEW")).andExpect(status().isOk());
        mvc.perform(get("/staff/orders").param("filter", "DONE_TODAY")).andExpect(status().isOk());
        mvc.perform(get("/staff/orders").param("filter", "bogus")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "sam", roles = "STAFF")
    void anEmptyBoardStillRenders() throws Exception {
        when(processing.queue(any(QueueFilter.class))).thenReturn(List.of());
        mvc.perform(get("/staff/orders")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managersCanUseTheBoardToo() throws Exception {
        mvc.perform(get("/staff/orders")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentsAndAdministratorsAreRefused() throws Exception {
        mvc.perform(get("/staff/orders")).andExpect(status().isForbidden());
        mvc.perform(get("/staff/orders/poll")).andExpect(status().isForbidden());
        mvc.perform(post("/staff/orders/o1/transition").with(csrf()).param("to", "ACCEPTED").param("version", "4"))
           .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorsCannotProcessOrders() throws Exception {
        mvc.perform(get("/staff/orders")).andExpect(status().isForbidden());
    }

    @Test
    void visitorsAreSentToSignIn() throws Exception {
        mvc.perform(get("/staff/orders")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "sam", roles = "STAFF")
    void aStatusChangeUsesTheSignedInStaffMemberAndKeepsTheView() throws Exception {
        when(processing.transition(eq("sam"), eq("o1"), eq("ACCEPTED"), eq(null), eq(4L)))
                .thenReturn(new TransitionResult("SC-ABC123", OrderStatus.ACCEPTED));

        mvc.perform(post("/staff/orders/o1/transition").with(csrf())
                .param("to", "ACCEPTED").param("version", "4").param("filter", "NEW").param("staff", "someone-else"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/staff/orders?filter=NEW"))
           .andExpect(flash().attributeExists("message"));

        verify(processing).transition("sam", "o1", "ACCEPTED", null, 4L);
    }

    @Test
    @WithMockUser(username = "sam", roles = "STAFF")
    void cancellingPassesTheReasonAlong() throws Exception {
        when(processing.transition(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new TransitionResult("SC-ABC123", OrderStatus.CANCELLED));
        mvc.perform(post("/staff/orders/o1/transition").with(csrf())
                .param("to", "CANCELLED").param("version", "4").param("reason", "Out of stock"))
           .andExpect(redirectedUrl("/staff/orders?filter=ACTIVE"));
        verify(processing).transition("sam", "o1", "CANCELLED", "Out of stock", 4L);
    }

    @Test
    @WithMockUser(username = "sam", roles = "STAFF")
    void aRefusedChangeExplainsWhy() throws Exception {
        when(processing.transition(any(), any(), any(), any(), any()))
                .thenThrow(new OrderException("Order SC-ABC123 is submitted and cannot be moved to ready."));
        mvc.perform(post("/staff/orders/o1/transition").with(csrf()).param("to", "READY").param("version", "4"))
           .andExpect(status().is3xxRedirection())
           .andExpect(flash().attributeExists("errors"));
    }

    @Test
    @WithMockUser(username = "sam", roles = "STAFF")
    void twoPeopleActingAtTheSameMomentIsExplained() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(CustomerOrder.class, 1L))
                .when(processing).transition(any(), any(), any(), any(), any());
        mvc.perform(post("/staff/orders/o1/transition").with(csrf()).param("to", "ACCEPTED").param("version", "4"))
           .andExpect(flash().attributeExists("errors"));
    }

    @Test
    @WithMockUser(username = "sam", roles = "STAFF")
    void aMissingOrBrokenVersionIsRefusedWithoutTouchingTheOrder() throws Exception {
        mvc.perform(post("/staff/orders/o1/transition").with(csrf()).param("to", "ACCEPTED"))
           .andExpect(flash().attributeExists("errors"));
        mvc.perform(post("/staff/orders/o1/transition").with(csrf()).param("to", "ACCEPTED").param("version", "abc"))
           .andExpect(flash().attributeExists("errors"));
        verify(processing, never()).transition(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void changesNeedACsrfToken() throws Exception {
        mvc.perform(post("/staff/orders/o1/transition").param("to", "ACCEPTED").param("version", "4"))
           .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void theQuietRefreshAnswerIsTiny() throws Exception {
        mvc.perform(get("/staff/orders/poll"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.token").value("SUBMITTED:1:100"));
    }
}
