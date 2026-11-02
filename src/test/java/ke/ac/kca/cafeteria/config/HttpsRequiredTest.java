package ke.ac.kca.cafeteria.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** What the prod and behind-proxy profiles switch on with app.require-https=true. */
@WebMvcTest(value = PageController.class, properties = "app.require-https=true")
@Import(SecurityConfig.class)
class HttpsRequiredTest {

    @Autowired
    MockMvc mvc;

    @Test
    void aPlainHttpPageRequestIsSentToHttps() throws Exception {
        mvc.perform(get("/login"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("https://**"));
    }

    @Test
    void aPlainHttpSignInIsNeverAcceptedSoPasswordsDoNotTravelUnprotected() throws Exception {
        mvc.perform(post("/login").param("username", "amina").param("password", "x"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("https://**"));
    }

    @Test
    void aSecureRequestIsServedNormally() throws Exception {
        mvc.perform(get("/login").secure(true)).andExpect(status().isOk());
    }
}
