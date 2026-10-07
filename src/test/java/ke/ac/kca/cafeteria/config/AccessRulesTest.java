package ke.ac.kca.cafeteria.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({PageController.class, AreaController.class})
@Import(SecurityConfig.class)
class AccessRulesTest {

    @Autowired
    MockMvc mvc;

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentOpensStudentAreaButNotOthers() throws Exception {
        mvc.perform(get("/student/home")).andExpect(status().isOk());
        mvc.perform(get("/staff/home")).andExpect(status().isForbidden());
        mvc.perform(get("/manager/home")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/home")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffOpensStaffAreaButNotManagerOrAdmin() throws Exception {
        mvc.perform(get("/staff/home")).andExpect(status().isOk());
        mvc.perform(get("/student/home")).andExpect(status().isForbidden());
        mvc.perform(get("/manager/home")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/home")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managerOpensStaffAndManagerAreas() throws Exception {
        mvc.perform(get("/staff/home")).andExpect(status().isOk());
        mvc.perform(get("/manager/home")).andExpect(status().isOk());
        mvc.perform(get("/admin/home")).andExpect(status().isForbidden());
        mvc.perform(get("/student/home")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminOpensAdminAreaOnly() throws Exception {
        mvc.perform(get("/admin/home")).andExpect(status().isOk());
        mvc.perform(get("/student/home")).andExpect(status().isForbidden());
        mvc.perform(get("/staff/home")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void homeRedirectsStudentToStudentArea() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/student/home"));
    }

    @Test
    @WithMockUser(roles = {"STAFF", "MANAGER"})
    void homeRedirectsToHighestRole() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/manager/home"));
    }

    @Test
    void anonymousIsSentToLogin() throws Exception {
        mvc.perform(get("/student/home")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/home")).andExpect(status().is3xxRedirection());
    }
}
