package ke.ac.kca.cafeteria.menu;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ke.ac.kca.cafeteria.config.SecurityConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MenuImageController.class)
@Import(SecurityConfig.class)
class MenuImageControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MenuService service;

    private static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};

    @Test
    @WithMockUser(roles = "STUDENT")
    void signedInUserGetsThePhotoWithCaching() throws Exception {
        when(service.image("i1")).thenReturn(Optional.of(new ImagePayload("image/jpeg", PHOTO, 42L)));
        mvc.perform(get("/media/menu/i1"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Type", "image/jpeg"))
           .andExpect(header().string("ETag", "\"42\""))
           .andExpect(content().bytes(PHOTO));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void unknownPhotoIsNotFound() throws Exception {
        when(service.image("nope")).thenReturn(Optional.empty());
        mvc.perform(get("/media/menu/nope")).andExpect(status().isNotFound());
    }

    @Test
    void anonymousVisitorIsSentToLogin() throws Exception {
        mvc.perform(get("/media/menu/i1")).andExpect(status().is3xxRedirection());
    }
}
