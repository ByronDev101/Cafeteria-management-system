package ke.ac.kca.cafeteria.menu;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@WebMvcTest(StudentMenuController.class)
@Import(SecurityConfig.class)
class StudentMenuControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MenuService service;

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentSeesMenuIncludingSoldOutItems() throws Exception {
        ItemView tea = new ItemView("i1", "Tea", "Hot tea", new BigDecimal("50.00"), true, true, "c1", "Drinks");
        ItemView juice = new ItemView("i2", "Fresh Juice", null, new BigDecimal("120.00"), false, true, "c1", "Drinks");
        when(service.studentMenu()).thenReturn(
                List.of(new CategoryView("c1", "Drinks", null, true, 1, List.of(tea, juice))));
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
