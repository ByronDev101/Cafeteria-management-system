package ke.ac.kca.cafeteria.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PageController.class)
@Import(SecurityConfig.class)
class SecurityBaselineTest {

    @Autowired
    MockMvc mvc;

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void homeRequiresLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("**/login"));
    }
}
