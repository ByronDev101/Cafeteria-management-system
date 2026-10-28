package ke.ac.kca.cafeteria.credit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

@WebMvcTest(StudentCreditController.class)
@Import(SecurityConfig.class)
class StudentCreditControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CreditService credit;

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void aStudentSeesTheirLimitWhatIsHeldAndOwedAndTheirRecentActivity() throws Exception {
        when(credit.balanceOf("amina")).thenReturn(new CreditBalance(true, new BigDecimal("500.00"),
                new BigDecimal("100.00"), new BigDecimal("150.00")));
        when(credit.recentEntries("amina")).thenReturn(List.of(
                new CreditEntryView("Held for an order", "reserve", "KSh 130.00", "SC-ABC123", "27 Oct 2026, 12:05", null),
                new CreditEntryView("Payment recorded", "settlement", "KSh 50.00", null, "26 Oct 2026, 09:00",
                        "Paid at the till")));

        mvc.perform(get("/student/credit"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("KSh 250.00")))
           .andExpect(content().string(containsString("KSh 500.00")))
           .andExpect(content().string(containsString("Held for an order")))
           .andExpect(content().string(containsString("SC-ABC123")))
           .andExpect(content().string(containsString("Paid at the till")))
           .andExpect(content().string(containsString("No real money moves")));
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void aStudentWithNoCreditIsToldHowToGetSome() throws Exception {
        when(credit.balanceOf("amina")).thenReturn(
                new CreditBalance(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(credit.recentEntries("amina")).thenReturn(List.of());

        mvc.perform(get("/student/credit"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("do not have cafeteria credit yet")))
           .andExpect(content().string(containsString("Nothing yet.")))
           .andExpect(content().string(not(containsString("Available now"))));
    }

    @Test
    @WithMockUser(username = "amina", roles = "STUDENT")
    void theStudentIsAlwaysTheSignedInPerson() throws Exception {
        when(credit.balanceOf("amina")).thenReturn(
                new CreditBalance(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        mvc.perform(get("/student/credit").param("username", "brian").param("student", "brian"))
           .andExpect(status().isOk());

        verify(credit).balanceOf("amina");
        verify(credit, never()).balanceOf("brian");
        verify(credit).recentEntries("amina");
    }

    @Test
    void everyoneButStudentsIsRefused() throws Exception {
        for (String role : List.of("STAFF", "MANAGER", "ADMIN")) {
            mvc.perform(get("/student/credit").with(user("someone").roles(role))).andExpect(status().isForbidden());
        }
        verify(credit, never()).balanceOf(any());
    }

    @Test
    void visitorsAreSentToSignIn() throws Exception {
        mvc.perform(get("/student/credit")).andExpect(status().is3xxRedirection());
        verify(credit, never()).balanceOf(any());
    }
}
