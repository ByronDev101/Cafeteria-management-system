package ke.ac.kca.cafeteria.menu;

import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Manager screens for the menu (FR-007). Managers only; see SecurityConfig. */
@Controller
@RequestMapping("/manager/menu")
public class ManagerMenuController {

    private static final String BASE = "/manager/menu";
    private static final String LIST = "redirect:/manager/menu";

    private final MenuService service;

    public ManagerMenuController(MenuService service) {
        this.service = service;
    }

    @GetMapping
    public String menu(Model model) {
        model.addAttribute("categories", service.managerMenu());
        return "manager/menu";
    }

    // ---- categories ----

    @GetMapping("/categories/new")
    public String newCategory(Model model) {
        return categoryForm(model, "New category", BASE + "/categories", "", "", "0", null);
    }

    @PostMapping("/categories")
    public String createCategory(@RequestParam String name,
                                 @RequestParam(defaultValue = "") String description,
                                 @RequestParam(defaultValue = "0") String displayOrder,
                                 Principal principal, Model model, RedirectAttributes redirect) {
        try {
            service.createCategory(principal.getName(), name, description, displayOrder);
            redirect.addFlashAttribute("message", "Category created.");
            return LIST;
        } catch (MenuException e) {
            return categoryForm(model, "New category", BASE + "/categories", name, description, displayOrder,
                    e.getMessages());
        }
    }

    @GetMapping("/categories/{id}/edit")
    public String editCategory(@PathVariable String id, Model model, RedirectAttributes redirect) {
        try {
            CategoryView category = service.category(id);
            return categoryForm(model, "Edit category", BASE + "/categories/" + id,
                    category.getName(), orEmpty(category.getDescription()),
                    String.valueOf(category.getDisplayOrder()), null);
        } catch (MenuException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
            return LIST;
        }
    }

    @PostMapping("/categories/{id}")
    public String updateCategory(@PathVariable String id,
                                 @RequestParam String name,
                                 @RequestParam(defaultValue = "") String description,
                                 @RequestParam(defaultValue = "0") String displayOrder,
                                 Principal principal, Model model, RedirectAttributes redirect) {
        try {
            service.updateCategory(principal.getName(), id, name, description, displayOrder);
            redirect.addFlashAttribute("message", "Category saved.");
            return LIST;
        } catch (MenuException e) {
            return categoryForm(model, "Edit category", BASE + "/categories/" + id, name, description,
                    displayOrder, e.getMessages());
        }
    }

    @PostMapping("/categories/{id}/activate")
    public String activateCategory(@PathVariable String id, Principal principal, RedirectAttributes redirect) {
        return run(redirect, "Category activated.", () -> service.setCategoryActive(principal.getName(), id, true));
    }

    @PostMapping("/categories/{id}/deactivate")
    public String deactivateCategory(@PathVariable String id, Principal principal, RedirectAttributes redirect) {
        return run(redirect, "Category deactivated. Students no longer see it.",
                () -> service.setCategoryActive(principal.getName(), id, false));
    }

    // ---- items ----

    @GetMapping("/items/new")
    public String newItem(@RequestParam(required = false) String category, Model model) {
        return itemForm(model, "New item", BASE + "/items", category == null ? "" : category,
                "", "", "", null);
    }

    @PostMapping("/items")
    public String createItem(@RequestParam String categoryId,
                             @RequestParam String name,
                             @RequestParam(defaultValue = "") String description,
                             @RequestParam String price,
                             Principal principal, Model model, RedirectAttributes redirect) {
        try {
            service.createItem(principal.getName(), categoryId, name, description, price);
            redirect.addFlashAttribute("message", "Item created.");
            return LIST;
        } catch (MenuException e) {
            return itemForm(model, "New item", BASE + "/items", categoryId, name, description, price,
                    e.getMessages());
        }
    }

    @GetMapping("/items/{id}/edit")
    public String editItem(@PathVariable String id, Model model, RedirectAttributes redirect) {
        try {
            ItemView item = service.item(id);
            return itemForm(model, "Edit item", BASE + "/items/" + id, item.getCategoryPublicId(),
                    item.getName(), orEmpty(item.getDescription()), item.getPriceInput(), null);
        } catch (MenuException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
            return LIST;
        }
    }

    @PostMapping("/items/{id}")
    public String updateItem(@PathVariable String id,
                             @RequestParam String categoryId,
                             @RequestParam String name,
                             @RequestParam(defaultValue = "") String description,
                             @RequestParam String price,
                             Principal principal, Model model, RedirectAttributes redirect) {
        try {
            service.updateItem(principal.getName(), id, categoryId, name, description, price);
            redirect.addFlashAttribute("message", "Item saved.");
            return LIST;
        } catch (MenuException e) {
            return itemForm(model, "Edit item", BASE + "/items/" + id, categoryId, name, description, price,
                    e.getMessages());
        }
    }

    @PostMapping("/items/{id}/available")
    public String markAvailable(@PathVariable String id, Principal principal, RedirectAttributes redirect) {
        return run(redirect, "Item marked available.", () -> service.setItemAvailable(principal.getName(), id, true));
    }

    @PostMapping("/items/{id}/unavailable")
    public String markUnavailable(@PathVariable String id, Principal principal, RedirectAttributes redirect) {
        return run(redirect, "Item marked unavailable.", () -> service.setItemAvailable(principal.getName(), id, false));
    }

    @PostMapping("/items/{id}/activate")
    public String activateItem(@PathVariable String id, Principal principal, RedirectAttributes redirect) {
        return run(redirect, "Item activated.", () -> service.setItemActive(principal.getName(), id, true));
    }

    @PostMapping("/items/{id}/deactivate")
    public String deactivateItem(@PathVariable String id, Principal principal, RedirectAttributes redirect) {
        return run(redirect, "Item deactivated. Students no longer see it.",
                () -> service.setItemActive(principal.getName(), id, false));
    }

    // ---- helpers ----

    private String categoryForm(Model model, String title, String action, String name, String description,
                                String displayOrder, List<String> errors) {
        model.addAttribute("title", title);
        model.addAttribute("formAction", action);
        model.addAttribute("name", name);
        model.addAttribute("description", description);
        model.addAttribute("displayOrder", displayOrder);
        if (errors != null) {
            model.addAttribute("errors", errors);
        }
        return "manager/category-form";
    }

    private String itemForm(Model model, String title, String action, String categoryId, String name,
                            String description, String price, List<String> errors) {
        model.addAttribute("title", title);
        model.addAttribute("formAction", action);
        model.addAttribute("categories", service.categoryOptions());
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("name", name);
        model.addAttribute("description", description);
        model.addAttribute("price", price);
        if (errors != null) {
            model.addAttribute("errors", errors);
        }
        return "manager/item-form";
    }

    private String run(RedirectAttributes redirect, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("message", success);
        } catch (MenuException e) {
            redirect.addFlashAttribute("errors", e.getMessages());
        }
        return LIST;
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }
}
