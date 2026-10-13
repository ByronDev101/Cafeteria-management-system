package ke.ac.kca.cafeteria.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ke.ac.kca.cafeteria.config.SecurityConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ManagerMenuController.class)
@Import(SecurityConfig.class)
class ManagerMenuControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MenuService service;

    private CategoryView sampleCategory() {
        ItemView tea = new ItemView("i1", "Tea", "Hot tea", new BigDecimal("50.00"), true, true, "c1", "Drinks",
                3, true, true, 7L);
        ItemView juice = new ItemView("i2", "Fresh Juice", null, new BigDecimal("120.00"), false, true, "c1", "Drinks");
        return new CategoryView("c1", "Drinks", "Hot and cold", true, 1, List.of(tea, juice));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotOpenManagerMenu() throws Exception {
        mvc.perform(get("/manager/menu")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCannotEditTheMenu() throws Exception {
        mvc.perform(get("/manager/menu")).andExpect(status().isForbidden());
        mvc.perform(post("/manager/menu/items/i1/unavailable").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void managerSeesCategoriesItemsAndThumbnails() throws Exception {
        when(service.managerMenu()).thenReturn(List.of(sampleCategory()));
        mvc.perform(get("/manager/menu")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void changeWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/manager/menu/items/i1/unavailable")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void markUnavailableUsesTheSignedInManagerAsActor() throws Exception {
        mvc.perform(post("/manager/menu/items/i1/unavailable").with(csrf()))
           .andExpect(status().is3xxRedirection());
        verify(service).setItemAvailable("boss", "i1", false);
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void categoryFormShowsErrorsWhenSaveFails() throws Exception {
        doThrow(new MenuException("A category with that name already exists."))
                .when(service).createCategory(eq("boss"), anyString(), anyString(), anyString());
        mvc.perform(post("/manager/menu/categories").with(csrf())
                .param("name", "Drinks").param("description", "").param("displayOrder", "1"))
           .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void newItemFormRenders() throws Exception {
        when(service.categoryOptions()).thenReturn(List.of(sampleCategory()));
        mvc.perform(get("/manager/menu/items/new").param("category", "c1")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void editItemFormShowsCurrentPhotoAndRemoveButton() throws Exception {
        ItemView withPhoto = new ItemView("i1", "Tea", "Hot tea", new BigDecimal("50.00"), true, true, "c1", "Drinks",
                3, true, false, 7L);
        when(service.item("i1")).thenReturn(withPhoto);
        when(service.categoryOptions()).thenReturn(List.of(sampleCategory()));
        mvc.perform(get("/manager/menu/items/i1/edit")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void creatingAnItemWithAPhotoPassesTheFieldsAndBytesToTheService() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "tea.jpg", "image/jpeg", new byte[] {1, 2, 3});
        mvc.perform(multipart("/manager/menu/items").file(photo).with(csrf())
                .param("categoryId", "c1").param("name", "Tea").param("description", "Hot tea")
                .param("price", "50").param("prepMinutes", "3")
                .param("vegetarian", "true").param("featured", "true"))
           .andExpect(status().is3xxRedirection());

        ArgumentCaptor<ItemInput> input = ArgumentCaptor.forClass(ItemInput.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(service).createItem(eq("boss"), input.capture(), bytes.capture());
        assertEquals("Tea", input.getValue().name());
        assertEquals("3", input.getValue().prepMinutes());
        assertTrue(input.getValue().vegetarian());
        assertTrue(input.getValue().featured());
        assertEquals(3, bytes.getValue().length);
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void creatingAnItemWithoutAPhotoPassesNoBytes() throws Exception {
        mvc.perform(post("/manager/menu/items").with(csrf())
                .param("categoryId", "c1").param("name", "Tea").param("price", "50"))
           .andExpect(status().is3xxRedirection());
        ArgumentCaptor<ItemInput> input = ArgumentCaptor.forClass(ItemInput.class);
        verify(service).createItem(eq("boss"), input.capture(), eq(null));
        assertEquals(false, input.getValue().vegetarian());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void itemFormShowsErrorsWhenSaveFails() throws Exception {
        doThrow(new MenuException("Enter the price in KSh as a number, for example 150 or 150.50."))
                .when(service).createItem(eq("boss"), any(ItemInput.class), any());
        when(service.categoryOptions()).thenReturn(List.of(sampleCategory()));
        mvc.perform(post("/manager/menu/items").with(csrf())
                .param("categoryId", "c1").param("name", "Tea").param("price", "abc"))
           .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "MANAGER")
    void removingAPhotoUsesTheSignedInManagerAsActor() throws Exception {
        mvc.perform(post("/manager/menu/items/i1/photo/remove").with(csrf()))
           .andExpect(status().is3xxRedirection());
        verify(service).removeItemImage("boss", "i1");
    }
}
