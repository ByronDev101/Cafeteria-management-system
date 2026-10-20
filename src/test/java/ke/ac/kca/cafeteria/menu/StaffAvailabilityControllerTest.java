package ke.ac.kca.cafeteria.menu;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ke.ac.kca.cafeteria.config.SecurityConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StaffAvailabilityController.class)
@Import(SecurityConfig.class)
class StaffAvailabilityControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MenuService service;

    private void menuWithTwoItems() {
        ItemView tea = new ItemView("i1", "Tea", null, new BigDecimal("50.00"), true, true, "c1", "Drinks");
        ItemView juice = new ItemView("i2", "Fresh Juice", null, new BigDecimal("120.00"), false, true, "c1", "Drinks");
        ItemView hidden = new ItemView("i3", "Old Soda", null, new BigDecimal("80.00"), true, false, "c1", "Drinks");
        when(service.managerMenu()).thenReturn(List.of(
                new CategoryView("c1", "Drinks", null, true, 1, List.of(tea, juice, hidden)),
                new CategoryView("c2", "Retired", null, false, 2, List.of())));
    }

    @Test
    @WithMockUser(username = "sam", roles = "STAFF")
    void staffSeeActiveItemsWithTheirAvailability() throws Exception {
        menuWithTwoItems();
        mvc.perform(get("/staff/availability")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void anEmptyMenuStillRenders() throws Exception {
        when(service.managerMenu()).thenReturn(List.of());
        mvc.perform(get("/staff/availability")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managersCanUseItToo() throws Exception {
        menuWithTwoItems();
        mvc.perform(get("/staff/availability")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentsAreRefused() throws Exception {
        mvc.perform(get("/staff/availability")).andExpect(status().isForbidden());
        mvc.perform(post("/staff/availability/i1/unavailable").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sam", roles = "STAFF")
    void staffCanMarkItemsSoldOutAndBackOnUsingTheirOwnName() throws Exception {
        mvc.perform(post("/staff/availability/i1/unavailable").with(csrf()))
           .andExpect(redirectedUrl("/staff/availability"))
           .andExpect(flash().attributeExists("message"));
        verify(service).setItemAvailable("sam", "i1", false);

        mvc.perform(post("/staff/availability/i1/available").with(csrf())).andExpect(redirectedUrl("/staff/availability"));
        verify(service).setItemAvailable("sam", "i1", true);
    }

    @Test
    @WithMockUser(username = "sam", roles = "STAFF")
    void anUnknownItemGivesASafeMessage() throws Exception {
        doThrow(new MenuException("That item was not found.")).when(service).setItemAvailable("sam", "ghost", false);
        mvc.perform(post("/staff/availability/ghost/unavailable").with(csrf()))
           .andExpect(flash().attributeExists("errors"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void changesNeedACsrfToken() throws Exception {
        mvc.perform(post("/staff/availability/i1/unavailable")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotReachThePriceAndPhotoScreens() throws Exception {
        mvc.perform(get("/manager/menu")).andExpect(status().isForbidden());
    }
}
