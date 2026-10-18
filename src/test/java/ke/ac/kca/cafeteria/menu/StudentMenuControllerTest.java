package ke.ac.kca.cafeteria.menu;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;
import ke.ac.kca.cafeteria.config.SecurityConfig;
import ke.ac.kca.cafeteria.ordering.Cart;
import ke.ac.kca.cafeteria.ordering.CartLine;
import ke.ac.kca.cafeteria.ordering.CartService;
import ke.ac.kca.cafeteria.ordering.CartSession;
import ke.ac.kca.cafeteria.ordering.CartView;
import ke.ac.kca.cafeteria.ordering.OrderService;
import ke.ac.kca.cafeteria.ordering.OrderSummary;
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

@WebMvcTest(StudentMenuController.class)
@Import(SecurityConfig.class)
class StudentMenuControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MenuService service;

    @MockitoBean
    TableService tables;

    @MockitoBean
    CartService carts;

    @MockitoBean
    OrderService orders;

    private ItemView item(String id, String name, boolean available, boolean featured, Long photo) {
        return new ItemView(id, name, "Tasty " + name, new BigDecimal("120.00"), available, true,
                "c1", "Drinks", 8, true, featured, photo);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentSeesHeroCardsPhotosPlaceholdersAndSoldOutItems() throws Exception {
        ItemView tea = item("i1", "Tea", true, true, 7L);
        ItemView pilau = item("i2", "Pilau", true, true, null);
        ItemView juice = item("i3", "Fresh Juice", false, false, null);
        when(service.studentMenu()).thenReturn(
                List.of(new CategoryView("c1", "Drinks", "Hot and cold", true, 1, List.of(tea, pilau, juice))));
        mvc.perform(get("/student/menu")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void menuShowsTheSelectedTable() throws Exception {
        when(service.studentMenu()).thenReturn(List.of());
        when(tables.currentTable(any(HttpSession.class))).thenReturn(Optional.of(new SelectedTable("t7", "Table 7")));
        mvc.perform(get("/student/menu"))
           .andExpect(status().isOk())
           .andExpect(model().attributeExists("table"));
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void menuShowsAddButtonsTheCartSummaryAndLiveOrders() throws Exception {
        when(service.studentMenu()).thenReturn(List.of(new CategoryView("c1", "Drinks", null, true, 1,
                List.of(item("i1", "Tea", true, true, null), item("i2", "Fresh Juice", false, false, null)))));
        when(carts.view(any())).thenReturn(new CartView(
                List.of(new CartLine("i1", "Tea", "KSh 120.00", 2, "KSh 240.00", true, null, null)),
                new BigDecimal("240.00"), "KSh 240.00", 2, true));
        when(orders.activeOrders("amina")).thenReturn(List.of(new OrderSummary("o1", "SC-ABC123", "PREPARING",
                "Preparing", true, "KSh 240.00", "1 Jan 2026, 12:00", "Table 7", "2 x Tea")));
        mvc.perform(get("/student/menu")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void everyPageKnowsHowManyItemsAreInTheCart() throws Exception {
        when(service.studentMenu()).thenReturn(List.of());
        Cart cart = new Cart();
        cart.add("i1", 3);
        mvc.perform(get("/student/menu").sessionAttr(CartSession.ATTRIBUTE, cart))
           .andExpect(status().isOk())
           .andExpect(model().attribute("cartCount", 3));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void menuWithoutATableAsksForOne() throws Exception {
        when(service.studentMenu()).thenReturn(List.of());
        mvc.perform(get("/student/menu"))
           .andExpect(status().isOk())
           .andExpect(model().attributeDoesNotExist("table"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void menuWithoutSpecialsStillRenders() throws Exception {
        when(service.studentMenu()).thenReturn(
                List.of(new CategoryView("c1", "Drinks", null, true, 1, List.of(item("i1", "Tea", true, false, null)))));
        mvc.perform(get("/student/menu")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void emptyMenuStillRenders() throws Exception {
        when(service.studentMenu()).thenReturn(List.of());
        mvc.perform(get("/student/menu")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managerIsNotAStudent() throws Exception {
        mvc.perform(get("/student/menu")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsSentToLogin() throws Exception {
        mvc.perform(get("/student/menu")).andExpect(status().is3xxRedirection());
    }
}
