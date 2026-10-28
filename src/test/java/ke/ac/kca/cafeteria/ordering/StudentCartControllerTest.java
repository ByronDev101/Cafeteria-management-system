package ke.ac.kca.cafeteria.ordering;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;
import ke.ac.kca.cafeteria.config.SecurityConfig;
import ke.ac.kca.cafeteria.credit.CreditBalance;
import ke.ac.kca.cafeteria.credit.CreditService;
import ke.ac.kca.cafeteria.menu.OrderableItem;
import ke.ac.kca.cafeteria.table.SelectedTable;
import ke.ac.kca.cafeteria.table.TableService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudentCartController.class)
@Import(SecurityConfig.class)
class StudentCartControllerTest {

    private static final String ITEM = "3f2b1c9e-8a4d-4e0b-9c1d-5a6b7c8d9e0f";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CartService carts;

    @MockitoBean
    TableService tables;

    @MockitoBean
    CreditService credit;

    private static CartView fullCart() {
        CartLine tea = new CartLine("i1", "Tea", "KSh 50.00", 2, "KSh 100.00", true, null, 7L);
        CartLine juice = new CartLine("i2", "Fresh Juice", "KSh 120.00", 1, "KSh 120.00", false, "sold out", null);
        return new CartView(List.of(tea, juice), new BigDecimal("100.00"), "KSh 100.00", 3, false);
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void cartPageShowsLinesProblemsPaymentChoicesAndTheTable() throws Exception {
        when(carts.view(any(Cart.class))).thenReturn(fullCart());
        when(tables.currentTable(any(HttpSession.class))).thenReturn(Optional.of(new SelectedTable("t1", "Table 7")));
        mvc.perform(get("/student/cart")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void creditIsOfferedWithTheAmountLeftWhenTheStudentHasSome() throws Exception {
        when(carts.view(any(Cart.class))).thenReturn(fullCart());
        CreditBalance balance = new CreditBalance(true, new BigDecimal("500.00"), new BigDecimal("100.00"),
                new BigDecimal("150.00"));
        when(credit.balanceOf("amina")).thenReturn(balance);

        mvc.perform(get("/student/cart"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Cafeteria credit")))
           .andExpect(content().string(containsString("Available now: KSh 250.00")))
           .andExpect(content().string(containsString("value=\"CREDIT\"")));
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void creditIsNotOfferedWithoutAnAccountOrWithNothingLeft() throws Exception {
        when(carts.view(any(Cart.class))).thenReturn(fullCart());

        when(credit.balanceOf("amina")).thenReturn(new CreditBalance(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        mvc.perform(get("/student/cart"))
           .andExpect(status().isOk())
           .andExpect(content().string(not(containsString("value=\"CREDIT\""))));

        when(credit.balanceOf("amina")).thenReturn(new CreditBalance(true, new BigDecimal("200.00"),
                new BigDecimal("50.00"), new BigDecimal("150.00")));
        mvc.perform(get("/student/cart"))
           .andExpect(status().isOk())
           .andExpect(content().string(not(containsString("value=\"CREDIT\""))))
           .andExpect(content().string(not(containsString("Available now"))));
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void emptyCartPageStillRenders() throws Exception {
        when(carts.view(any(Cart.class))).thenReturn(new CartView(List.of(), BigDecimal.ZERO, "KSh 0.00", 0, false));
        mvc.perform(get("/student/cart")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void onlyStudentsHaveACart() throws Exception {
        mvc.perform(get("/student/cart")).andExpect(status().isForbidden());
        mvc.perform(post("/student/cart/add").with(csrf()).param("itemId", ITEM)).andExpect(status().isForbidden());
    }

    @Test
    void visitorsAreSentToSignIn() throws Exception {
        mvc.perform(get("/student/cart")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void changesNeedACsrfToken() throws Exception {
        mvc.perform(post("/student/cart/add").param("itemId", ITEM)).andExpect(status().isForbidden());
        mvc.perform(post("/student/cart/update").param("itemId", "i1").param("quantity", "2")).andExpect(status().isForbidden());
        mvc.perform(post("/student/cart/remove").param("itemId", "i1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void addingAnItemReturnsToThatItemOnTheMenu() throws Exception {
        when(carts.add(any(Cart.class), eq(ITEM), eq(1)))
                .thenReturn(new OrderableItem(1L, ITEM, "Tea", new BigDecimal("50.00"), true, null, null));
        mvc.perform(post("/student/cart/add").with(csrf()).param("itemId", ITEM))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/student/menu#item-" + ITEM))
           .andExpect(flash().attributeExists("message"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aRefusedAddShowsTheReasonAndNothingElse() throws Exception {
        when(carts.add(any(Cart.class), eq(ITEM), eq(1))).thenThrow(new OrderException("Fresh Juice is sold out."));
        mvc.perform(post("/student/cart/add").with(csrf()).param("itemId", ITEM))
           .andExpect(status().is3xxRedirection())
           .andExpect(flash().attributeExists("errors"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void oddItemIdsNeverEndUpInTheRedirectAddress() throws Exception {
        when(carts.add(any(Cart.class), eq("x\"><script>"), eq(1))).thenThrow(new OrderException("That item was not found."));
        mvc.perform(post("/student/cart/add").with(csrf()).param("itemId", "x\"><script>"))
           .andExpect(redirectedUrl("/student/menu"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void updateRemoveAndEmptyChangeTheSessionCart() throws Exception {
        mvc.perform(post("/student/cart/update").with(csrf()).param("itemId", "i1").param("quantity", "3"))
           .andExpect(redirectedUrl("/student/cart"));
        verify(carts).update(any(Cart.class), eq("i1"), eq(3));

        mvc.perform(post("/student/cart/remove").with(csrf()).param("itemId", "i1"))
           .andExpect(redirectedUrl("/student/cart"));
        verify(carts).remove(any(Cart.class), eq("i1"));

        Cart cart = new Cart();
        cart.add("tea", 2);
        mvc.perform(post("/student/cart/clear").with(csrf()).sessionAttr(CartSession.ATTRIBUTE, cart))
           .andExpect(redirectedUrl("/student/cart"));
        org.junit.jupiter.api.Assertions.assertTrue(cart.isEmpty());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aQuantityThatIsNotANumberIsRefusedWithoutTouchingTheCart() throws Exception {
        mvc.perform(post("/student/cart/update").with(csrf()).param("itemId", "i1").param("quantity", "lots"))
           .andExpect(status().is3xxRedirection())
           .andExpect(flash().attributeExists("errors"));
        verify(carts, never()).update(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
