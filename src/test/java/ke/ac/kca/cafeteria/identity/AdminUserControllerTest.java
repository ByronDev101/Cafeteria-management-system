package ke.ac.kca.cafeteria.identity;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import ke.ac.kca.cafeteria.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserAdminService service;

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotOpenAccountList() throws Exception {
        mvc.perform(get("/admin/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotDeactivateAccounts() throws Exception {
        mvc.perform(post("/admin/users/abc/deactivate").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void adminSeesAccountList() throws Exception {
        when(service.list(anyInt(), anyString())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/admin/users")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void changeWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/admin/users/abc/deactivate")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void deactivateUsesTheSignedInAdminAsActor() throws Exception {
        mvc.perform(post("/admin/users/abc/deactivate").with(csrf()))
           .andExpect(status().is3xxRedirection());
        verify(service).setActive("boss", "abc", false);
    }
}
