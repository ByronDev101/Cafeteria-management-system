package ke.ac.kca.cafeteria.ordering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.servlet.http.HttpSession;
import ke.ac.kca.cafeteria.config.SecurityConfig;
import ke.ac.kca.cafeteria.table.SelectedTable;
import ke.ac.kca.cafeteria.table.TableService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudentOrderController.class)
@Import(SecurityConfig.class)
class StudentOrderControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orders;

    @MockitoBean
    TableService tables;

    private static OrderView sampleOrder(String status, boolean terminal, boolean canCancel) {
        return new OrderView("o1", "SC-ABC123", status, "Label", terminal, canCancel, "Table 7", "Cash", "KSh 130.00",
                "1 Jan 2026, 12:00",
                List.of(new OrderLineView("Tea", 2, "KSh 50.00", "KSh 100.00")),
                List.of(new OrderStepView("Submitted", "current", "1 Jan 2026, 12:00"),
                        new OrderStepView("Accepted", "upcoming", null)));
    }

    private static Cart cartWithTea() {
        Cart cart = new Cart();
        cart.add("tea", 2);
        return cart;
    }

    private void tableSelected() {
        when(tables.currentTable(any(HttpSession.class))).thenReturn(Optional.of(new SelectedTable("t1", "Table 7")));
    }

    // ---- placing ----

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void placingAnOrderOpensItAndEmptiesTheCart() throws Exception {
        tableSelected();
        when(orders.place(eq("amina"), anyMap(), eq("t1"), eq(PaymentMethod.CASH), anyString()))
                .thenReturn(new PlacedOrder("o1", "SC-ABC123", false));
        Cart cart = cartWithTea();

        mvc.perform(post("/student/orders").with(csrf()).param("paymentMethod", "CASH")
                .sessionAttr(CartSession.ATTRIBUTE, cart))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/student/orders/o1"))
           .andExpect(flash().attributeExists("message"));

        assertTrue(cart.isEmpty());
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void theStudentComesFromTheSessionNeverFromTheRequest() throws Exception {
        tableSelected();
        when(orders.place(eq("amina"), anyMap(), eq("t1"), eq(PaymentMethod.MPESA_TILL), anyString()))
                .thenReturn(new PlacedOrder("o1", "SC-ABC123", false));
        mvc.perform(post("/student/orders").with(csrf()).param("paymentMethod", "MPESA_TILL")
                .param("student", "someone-else").param("price", "1")
                .sessionAttr(CartSession.ATTRIBUTE, cartWithTea()))
           .andExpect(redirectedUrl("/student/orders/o1"));
        verify(orders).place(eq("amina"), anyMap(), eq("t1"), eq(PaymentMethod.MPESA_TILL), anyString());
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void aRefusedOrderReturnsToTheCartWithTheReasonAndKeepsTheItems() throws Exception {
        tableSelected();
        when(orders.place(any(), anyMap(), any(), any(), any()))
                .thenThrow(new OrderException("Fresh Juice is sold out. Remove it to continue."));
        Cart cart = cartWithTea();

        mvc.perform(post("/student/orders").with(csrf()).param("paymentMethod", "CASH")
                .sessionAttr(CartSession.ATTRIBUTE, cart))
           .andExpect(redirectedUrl("/student/cart"))
           .andExpect(flash().attributeExists("errors"));

        assertFalse(cart.isEmpty());
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void withoutATableNoTableIsPassedOn() throws Exception {
        when(orders.place(any(), anyMap(), eq(null), any(), any()))
                .thenThrow(new OrderException("Choose your table first."));
        mvc.perform(post("/student/orders").with(csrf()).param("paymentMethod", "CASH")
                .sessionAttr(CartSession.ATTRIBUTE, cartWithTea()))
           .andExpect(redirectedUrl("/student/cart"));
        verify(orders).place(eq("amina"), anyMap(), eq(null), eq(PaymentMethod.CASH), anyString());
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void aRepeatedSubmitShowsTheExistingOrderAndLeavesANewCartAlone() throws Exception {
        tableSelected();
        when(orders.place(any(), anyMap(), any(), any(), any())).thenReturn(new PlacedOrder("o1", "SC-ABC123", true));
        Cart cart = cartWithTea();

        mvc.perform(post("/student/orders").with(csrf()).param("paymentMethod", "CASH")
                .sessionAttr(CartSession.ATTRIBUTE, cart))
           .andExpect(redirectedUrl("/student/orders/o1"));

        assertFalse(cart.isEmpty());
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void twoTapsAtTheSameMomentEndUpOnTheSameOrder() throws Exception {
        tableSelected();
        Cart cart = cartWithTea();
        String key = cart.idempotencyKey();
        when(orders.place(any(), anyMap(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(orders.findByKey("amina", key)).thenReturn(Optional.of(new PlacedOrder("o9", "SC-AAA111", true)));

        mvc.perform(post("/student/orders").with(csrf()).param("paymentMethod", "CASH")
                .sessionAttr(CartSession.ATTRIBUTE, cart))
           .andExpect(redirectedUrl("/student/orders/o9"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void onlyStudentsCanPlaceOrders() throws Exception {
        mvc.perform(post("/student/orders").with(csrf()).param("paymentMethod", "CASH")).andExpect(status().isForbidden());
        mvc.perform(get("/student/orders")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void placingNeedsACsrfToken() throws Exception {
        mvc.perform(post("/student/orders").param("paymentMethod", "CASH")).andExpect(status().isForbidden());
    }

    // ---- reading ----

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void historyAndDetailPagesRender() throws Exception {
        OrderSummary summary = new OrderSummary("o1", "SC-ABC123", "SUBMITTED", "Submitted", true, "KSh 130.00",
                "1 Jan 2026, 12:00", "Table 7", "2 x Tea");
        when(orders.history(eq("amina"), org.mockito.ArgumentMatchers.anyInt())).thenReturn(new PageImpl<>(List.of(summary)));
        when(orders.detail("amina", "o1")).thenReturn(sampleOrder("SUBMITTED", false, true));

        mvc.perform(get("/student/orders")).andExpect(status().isOk());
        mvc.perform(get("/student/orders/o1")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void emptyHistoryAndFinishedOrdersRender() throws Exception {
        when(orders.history(eq("amina"), org.mockito.ArgumentMatchers.anyInt())).thenReturn(new PageImpl<>(List.of()));
        when(orders.detail("amina", "o2")).thenReturn(sampleOrder("COMPLETED", true, false));
        mvc.perform(get("/student/orders")).andExpect(status().isOk());
        mvc.perform(get("/student/orders/o2")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void someoneElsesOrderLooksExactlyLikeOneThatDoesNotExist() throws Exception {
        when(orders.detail("amina", "theirs")).thenThrow(new OrderException("That order was not found."));
        mvc.perform(get("/student/orders/theirs"))
           .andExpect(status().isNotFound())
           .andExpect(view().name("student/order-not-found"));
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void theStatusAnswerIsTinyAndOwnerOnly() throws Exception {
        when(orders.statusOf("amina", "o1")).thenReturn(OrderStatus.PREPARING);
        when(orders.statusOf("amina", "theirs")).thenThrow(new OrderException("That order was not found."));

        mvc.perform(get("/student/orders/o1/status"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("PREPARING"))
           .andExpect(jsonPath("$.label").value("Preparing"))
           .andExpect(jsonPath("$.terminal").value(false));
        mvc.perform(get("/student/orders/theirs/status")).andExpect(status().isNotFound());
    }

    // ---- cancelling ----

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void cancellingUsesTheSignedInStudent() throws Exception {
        mvc.perform(post("/student/orders/o1/cancel").with(csrf()))
           .andExpect(redirectedUrl("/student/orders/o1"))
           .andExpect(flash().attributeExists("message"));
        verify(orders).cancelOwn("amina", "o1");
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void aRefusedCancellationExplainsWhy() throws Exception {
        doThrow(new OrderException("This order can no longer be cancelled here because it has been accepted."))
                .when(orders).cancelOwn("amina", "o1");
        mvc.perform(post("/student/orders/o1/cancel").with(csrf()))
           .andExpect(redirectedUrl("/student/orders/o1"))
           .andExpect(flash().attributeExists("errors"));
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void aClashWithStaffAcceptingAtTheSameMomentIsExplained() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(CustomerOrder.class, 1L))
                .when(orders).cancelOwn("amina", "o1");
        mvc.perform(post("/student/orders/o1/cancel").with(csrf()))
           .andExpect(redirectedUrl("/student/orders/o1"))
           .andExpect(flash().attributeExists("errors"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void cancellingNeedsACsrfToken() throws Exception {
        mvc.perform(post("/student/orders/o1/cancel")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void aCancelledOrderShowsWhoCancelledItAndWhy() throws Exception {
        OrderView cancelled = new OrderView("o3", "SC-ZZZ999", "CANCELLED", "Cancelled", true, false, "Table 7", "Cash",
                "KSh 50.00", "1 Jan 2026, 12:00",
                List.of(new OrderLineView("Tea", 1, "KSh 50.00", "KSh 50.00")),
                List.of(new OrderStepView("Submitted", "done", "1 Jan 2026, 12:00"),
                        new OrderStepView("Cancelled", "cancelled", "1 Jan 2026, 12:10")),
                "Cancelled by staff: Out of stock");
        when(orders.detail("amina", "o3")).thenReturn(cancelled);
        mvc.perform(get("/student/orders/o3")).andExpect(status().isOk());
    }
}
