package ke.ac.kca.cafeteria.table;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ke.ac.kca.cafeteria.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminTableController.class)
@Import({SecurityConfig.class, PublicUrl.class})
class AdminTableControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TableService service;

    private static final List<TableView> SAMPLE = List.of(
            new TableView("t1", "Table 1", true, "9 Oct 2026, 09:00"),
            new TableView("t2", "Table 2", false, "9 Oct 2026, 09:05"));

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotOpenTableAdministration() throws Exception {
        mvc.perform(get("/admin/tables")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managerCannotOpenTableAdministration() throws Exception {
        mvc.perform(get("/admin/tables")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/tables/t1/rotate").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void adminSeesTheTableList() throws Exception {
        when(service.list()).thenReturn(SAMPLE);
        mvc.perform(get("/admin/tables")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void adminSeesThePrintableQrCardRightAfterIssuing() throws Exception {
        when(service.list()).thenReturn(SAMPLE);
        QrSheet sheet = new QrSheet("Table 7", "ABCD-EFGH-JKMN-PQRS-TVWX-YZ01-23",
                "http://localhost/t/ABCDEFGHJKMNPQRSTVWXYZ0123", QrCodeImage.pngDataUri("http://localhost/t/x", 200));
        mvc.perform(get("/admin/tables").flashAttr("issued", sheet).flashAttr("message", "Table created."))
           .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void changeWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/admin/tables/t1/deactivate")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/tables").param("label", "Table 9")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void creatingATableShowsItsQrOnceAndUsesTheSignedInAdminAsActor() throws Exception {
        when(service.create(eq("boss"), eq("Table 7"))).thenReturn(new IssuedQr("t7", "Table 7", TableToken.generate()));
        mvc.perform(post("/admin/tables").with(csrf()).param("label", "Table 7"))
           .andExpect(status().is3xxRedirection())
           .andExpect(flash().attributeExists("issued"));
        verify(service).create("boss", "Table 7");
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void formShowsErrorsWhenSaveFails() throws Exception {
        doThrow(new TableException("A table with that label already exists."))
                .when(service).create(eq("boss"), anyString());
        mvc.perform(post("/admin/tables").with(csrf()).param("label", "Table 1")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void newAndEditFormsRender() throws Exception {
        when(service.get("t1")).thenReturn(SAMPLE.get(0));
        mvc.perform(get("/admin/tables/new")).andExpect(status().isOk());
        mvc.perform(get("/admin/tables/t1/edit")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void deactivateReactivateAndRotateUseTheSignedInAdminAsActor() throws Exception {
        when(service.reactivate("boss", "t2")).thenReturn(new IssuedQr("t2", "Table 2", TableToken.generate()));
        when(service.rotate("boss", "t1")).thenReturn(new IssuedQr("t1", "Table 1", TableToken.generate()));

        mvc.perform(post("/admin/tables/t1/deactivate").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/tables/t2/reactivate").with(csrf()))
           .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("issued"));
        mvc.perform(post("/admin/tables/t1/rotate").with(csrf()))
           .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("issued"));

        verify(service).deactivate("boss", "t1");
        verify(service).reactivate("boss", "t2");
        verify(service).rotate("boss", "t1");
    }
}
