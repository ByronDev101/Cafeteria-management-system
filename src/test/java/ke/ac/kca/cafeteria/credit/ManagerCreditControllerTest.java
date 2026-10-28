package ke.ac.kca.cafeteria.credit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ke.ac.kca.cafeteria.config.SecurityConfig;
import ke.ac.kca.cafeteria.ordering.OrderException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ManagerCreditController.class)
@Import(SecurityConfig.class)
class ManagerCreditControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CreditService credit;

    private static CreditRow row(boolean hasAccount, String limit, String held, String owed) {
        return new CreditRow("s1", "Amina Wanjiru", "amina",
                new CreditBalance(hasAccount, new BigDecimal(limit), new BigDecimal(held), new BigDecimal(owed)));
    }

    @BeforeEach
    void stubOverview() {
        when(credit.overview(any())).thenReturn(List.of(row(true, "500.00", "100.00", "150.00")));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void aManagerSeesEachStudentsFiguresAndTheForms() throws Exception {
        mvc.perform(get("/manager/credit"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Amina Wanjiru")))
           .andExpect(content().string(containsString("KSh 500.00")))
           .andExpect(content().string(containsString("KSh 250.00")))
           .andExpect(content().string(containsString("Set limit")))
           .andExpect(content().string(containsString("Record payment")))
           .andExpect(content().string(containsString("prototype")));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void theRecordPaymentFormIsOfferedOnlyToStudentsWhoOweSomething() throws Exception {
        when(credit.overview(any())).thenReturn(List.of(row(true, "500.00", "0.00", "0.00")));

        mvc.perform(get("/manager/credit"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Set limit")))
           .andExpect(content().string(not(containsString("Record payment"))));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void theSearchTextIsHandedToTheServiceAndKeptInTheBox() throws Exception {
        mvc.perform(get("/manager/credit").param("q", "amina"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("value=\"amina\"")));

        verify(credit).overview("amina");
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void whenNobodyIsListedThePageSaysWhatToDo() throws Exception {
        when(credit.overview(any())).thenReturn(List.of());

        mvc.perform(get("/manager/credit"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("No student has credit yet")));
        mvc.perform(get("/manager/credit").param("q", "zzz"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("No active student matches that search")));
    }

    @Test
    @WithMockUser(username = "pat", roles = "MANAGER")
    void theLimitIsSetAsTheSignedInManagerAndTheSearchIsKept() throws Exception {
        when(credit.setLimit("pat", "s1", "500")).thenReturn(row(true, "500.00", "0.00", "0.00"));

        mvc.perform(post("/manager/credit/s1/limit").with(csrf()).param("limit", "500").param("q", "ami")
                .param("actor", "someone-else"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/manager/credit?q=ami"))
           .andExpect(flash().attributeExists("message"));

        verify(credit).setLimit("pat", "s1", "500");
    }

    @Test
    @WithMockUser(username = "pat", roles = "MANAGER")
    void withNoSearchTheRedirectHasNoQuery() throws Exception {
        when(credit.setLimit("pat", "s1", "500")).thenReturn(row(true, "500.00", "0.00", "0.00"));

        mvc.perform(post("/manager/credit/s1/limit").with(csrf()).param("limit", "500"))
           .andExpect(redirectedUrl("/manager/credit"));
    }

    @Test
    @WithMockUser(username = "pat", roles = "MANAGER")
    void aRefusedLimitExplainsWhy() throws Exception {
        when(credit.setLimit(any(), any(), any()))
                .thenThrow(new OrderException("The credit limit can be at most KSh 50,000.00."));

        mvc.perform(post("/manager/credit/s1/limit").with(csrf()).param("limit", "99999"))
           .andExpect(status().is3xxRedirection())
           .andExpect(flash().attributeExists("errors"));
    }

    @Test
    @WithMockUser(username = "pat", roles = "MANAGER")
    void aPaymentIsRecordedAsTheSignedInManager() throws Exception {
        when(credit.settle("pat", "s1", "100", "Paid at the till")).thenReturn(row(true, "500.00", "0.00", "50.00"));

        mvc.perform(post("/manager/credit/s1/settle").with(csrf()).param("amount", "100")
                .param("note", "Paid at the till").param("q", "ami"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/manager/credit?q=ami"))
           .andExpect(flash().attributeExists("message"));

        verify(credit).settle("pat", "s1", "100", "Paid at the till");
    }

    @Test
    @WithMockUser(username = "pat", roles = "MANAGER")
    void aRefusedPaymentExplainsWhy() throws Exception {
        when(credit.settle(any(), any(), any(), any()))
                .thenThrow(new OrderException("Amina Wanjiru owes KSh 0.00, so a payment of KSh 10.00 is too much."));

        mvc.perform(post("/manager/credit/s1/settle").with(csrf()).param("amount", "10"))
           .andExpect(flash().attributeExists("errors"));
    }

    @Test
    @WithMockUser(username = "pat", roles = "MANAGER")
    void twoManagersChangingTheSameAccountAtOnceIsExplained() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(CreditAccount.class, 1L))
                .when(credit).setLimit(any(), any(), any());
        doThrow(new ObjectOptimisticLockingFailureException(CreditAccount.class, 1L))
                .when(credit).settle(any(), any(), any(), any());

        mvc.perform(post("/manager/credit/s1/limit").with(csrf()).param("limit", "500"))
           .andExpect(flash().attributeExists("errors"));
        mvc.perform(post("/manager/credit/s1/settle").with(csrf()).param("amount", "10"))
           .andExpect(flash().attributeExists("errors"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void changesNeedACsrfToken() throws Exception {
        mvc.perform(post("/manager/credit/s1/limit").param("limit", "500")).andExpect(status().isForbidden());
        mvc.perform(post("/manager/credit/s1/settle").param("amount", "10")).andExpect(status().isForbidden());
        verify(credit, never()).setLimit(any(), any(), any());
        verify(credit, never()).settle(any(), any(), any(), any());
    }

    @Test
    void everyoneButManagersIsRefused() throws Exception {
        for (String role : List.of("STAFF", "STUDENT", "ADMIN")) {
            mvc.perform(get("/manager/credit").with(user("someone").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/manager/credit/s1/limit").with(user("someone").roles(role)).with(csrf())
                    .param("limit", "500")).andExpect(status().isForbidden());
            mvc.perform(post("/manager/credit/s1/settle").with(user("someone").roles(role)).with(csrf())
                    .param("amount", "10")).andExpect(status().isForbidden());
        }
        verify(credit, never()).overview(any());
        verify(credit, never()).setLimit(any(), any(), any());
        verify(credit, never()).settle(any(), any(), any(), any());
    }

    @Test
    void visitorsAreSentToSignIn() throws Exception {
        mvc.perform(get("/manager/credit")).andExpect(status().is3xxRedirection());
        verify(credit, never()).overview(any());
    }
}
