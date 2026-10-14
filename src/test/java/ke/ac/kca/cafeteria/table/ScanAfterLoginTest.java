package ke.ac.kca.cafeteria.table;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ke.ac.kca.cafeteria.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** A student who scans a table's QR code while signed out should land on that table right after signing in. */
@WebMvcTest(StudentTableController.class)
@Import({SecurityConfig.class, ScanAfterLoginTest.Accounts.class})
class ScanAfterLoginTest {

    @TestConfiguration
    static class Accounts {
        @Bean
        UserDetailsService users(PasswordEncoder encoder) {
            return new InMemoryUserDetailsManager(
                    User.withUsername("amina").password(encoder.encode("Sunny-Day-42")).roles("STUDENT").build());
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TableService service;

    @Test
    void signedOutScanContinuesToTheTableAfterSignIn() throws Exception {
        String code = TableToken.generate();

        MvcResult scan = mvc.perform(get("/t/" + code))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login*"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) scan.getRequest().getSession();

        mvc.perform(post("/login").with(csrf()).session(session)
                        .param("username", "amina").param("password", "Sunny-Day-42"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/t/" + code));
    }

    @Test
    void ordinarySignInStillGoesToTheStartPage() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "amina").param("password", "Sunny-Day-42"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
