package ke.ac.kca.cafeteria.table;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import ke.ac.kca.cafeteria.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudentTableController.class)
@Import(SecurityConfig.class)
class StudentTableControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TableService service;

    private final String code = TableToken.generate();

    @Test
    @WithMockUser(roles = "STUDENT")
    void scanningAValidCodeRemembersTheTableAndOpensTheMenu() throws Exception {
        when(service.resolve(code)).thenReturn(TableResolution.valid(new SelectedTable("t7", "Table 7")));
        mvc.perform(get("/t/" + code))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/student/menu"))
           .andExpect(request().sessionAttribute("selectedTable", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void malformedUnknownAndInactiveCodesAllGetTheSamePlainAnswer() throws Exception {
        for (TableResolution.Status reason : new TableResolution.Status[] {
                TableResolution.Status.MALFORMED, TableResolution.Status.UNKNOWN, TableResolution.Status.INACTIVE}) {
            when(service.resolve("bad-" + reason)).thenReturn(TableResolution.failed(reason));
            mvc.perform(get("/t/bad-" + reason))
               .andExpect(status().isNotFound())
               .andExpect(view().name("student/table-invalid"))
               .andExpect(request().sessionAttributeDoesNotExist("selectedTable"));
        }
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void onlyStudentsCanUseTableCodes() throws Exception {
        mvc.perform(get("/t/" + code)).andExpect(status().isForbidden());
        mvc.perform(get("/student/table")).andExpect(status().isForbidden());
    }

    @Test
    void signedOutVisitorsAreSentToTheLoginPage() throws Exception {
        mvc.perform(get("/t/" + code)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/student/table")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void typingAValidCodeWorksLikeScanning() throws Exception {
        String typed = TableToken.display(code);
        when(service.resolve(typed)).thenReturn(TableResolution.valid(new SelectedTable("t7", "Table 7")));
        mvc.perform(post("/student/table").with(csrf()).param("code", typed))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/student/menu"))
           .andExpect(request().sessionAttribute("selectedTable", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void typingABadCodeShowsOneGenericMessage() throws Exception {
        when(service.resolve("nonsense")).thenReturn(TableResolution.failed(TableResolution.Status.MALFORMED));
        mvc.perform(post("/student/table").with(csrf()).param("code", "nonsense"))
           .andExpect(status().isOk())
           .andExpect(view().name("student/table-entry"))
           .andExpect(model().attributeExists("errors"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void entryFormRendersAndChangeTableClearsTheChoice() throws Exception {
        mvc.perform(get("/student/table")).andExpect(status().isOk());
        mvc.perform(post("/student/table/clear").with(csrf())
                .sessionAttr("selectedTable", new SelectedTable("t7", "Table 7")))
           .andExpect(status().is3xxRedirection())
           .andExpect(request().sessionAttributeDoesNotExist("selectedTable"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void typedCodeWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/student/table").param("code", code)).andExpect(status().isForbidden());
        verify(service, org.mockito.Mockito.never()).resolve(code);
    }
}
