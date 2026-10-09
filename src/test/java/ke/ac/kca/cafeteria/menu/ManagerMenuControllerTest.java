package ke.ac.kca.cafeteria.menu;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@WebMvcTest(ManagerMenuController.class)
@Import(SecurityConfig.class)
class ManagerMenuControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MenuService service;

    private CategoryView sampleCategory() {
        ItemView tea = new ItemView("i1", "Tea", "Hot tea", new BigDecimal("50.00"), true, true, "c1", "Drinks");
        ItemView juice = new ItemView("i2", "Fresh Juice", null, new BigDecimal("120.00"), false, true, "c1", "Drinks");
        return new CategoryView("c1", "Drinks", "Hot and cold", true, 1, List.of(tea, juice));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotOpenManagerMenu() throws Exception {
        mvc.perform(get("/manager/menu")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCannotEditTheMenu() throws Exception {
        mvc.perform(get("/manager/menu")).andExpect(status().isForbidden());
        mvc.perform(post("/manager/menu/items/i1/unavailable").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void managerSeesCategoriesAndItems() throws Exception {
        when(service.managerMenu()).thenReturn(List.of(sampleCategory()));
        mvc.perform(get("/manager/menu")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void changeWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/manager/menu/items/i1/unavailable")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void markUnavailableUsesTheSignedInManagerAsActor() throws Exception {
        mvc.perform(post("/manager/menu/items/i1/unavailable").with(csrf()))
           .andExpect(status().is3xxRedirection());
        verify(service).setItemAvailable("boss", "i1", false);
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void categoryFormShowsErrorsWhenSaveFails() throws Exception {
        doThrow(new MenuException("A category with that name already exists."))
                .when(service).createCategory(eq("boss"), anyString(), anyString(), anyString());
        mvc.perform(post("/manager/menu/categories").with(csrf())
                .param("name", "Drinks").param("description", "").param("displayOrder", "1"))
           .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void newItemFormRenders() throws Exception {
        when(service.categoryOptions()).thenReturn(List.of(sampleCategory()));
        mvc.perform(get("/manager/menu/items/new").param("category", "c1")).andExpect(status().isOk());
    }
}
