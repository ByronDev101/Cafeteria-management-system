package ke.ac.kca.cafeteria.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PageController.class)
@Import(SecurityConfig.class)
class SecurityHardeningTest {

    @Autowired
    MockMvc mvc;

    @Test
    void everyPageCarriesTheBrowserProtectionHeaders() throws Exception {
        mvc.perform(get("/login"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
           .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
           .andExpect(header().string("Referrer-Policy", "no-referrer"))
           .andExpect(header().string("X-Content-Type-Options", "nosniff"))
           .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
           .andExpect(header().string("Permissions-Policy", containsString("geolocation=()")));
    }

    @Test
    void secureConnectionsAreToldToStayOnHttpsForAYear() throws Exception {
        mvc.perform(get("/login").secure(true))
           .andExpect(status().isOk())
           .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
           .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));
    }

    @Test
    void plainHttpIsNeverToldAboutHttpsOnlyRules() throws Exception {
        mvc.perform(get("/login"))
           .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void theSignInPageExplainsAThrottledAttemptWithoutRevealingAnything() throws Exception {
        mvc.perform(get("/login").param("throttled", ""))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Too many failed sign-in attempts from this device")))
           .andExpect(content().string(not(containsString("Sign-in failed"))));
    }

    @Test
    void theNormalSignInPageShowsNoThrottleMessage() throws Exception {
        mvc.perform(get("/login"))
           .andExpect(content().string(not(containsString("Too many failed sign-in attempts"))));
    }
}
