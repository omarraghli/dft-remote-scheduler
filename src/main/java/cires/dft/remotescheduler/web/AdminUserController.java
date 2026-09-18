package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.security.SecurityContexts;
import cires.dft.remotescheduler.service.UserManagementException;
import cires.dft.remotescheduler.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Account management. The whole path is admin-only, enforced in SecurityConfig. */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final RemoteScheduleProperties scheduleProperties;

    public AdminUserController(UserService userService,
                               RemoteScheduleProperties scheduleProperties) {
        this.userService = userService;
        this.scheduleProperties = scheduleProperties;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("roles", Role.values());
        model.addAttribute("rosterNames", scheduleProperties.getPeople().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
        model.addAttribute("meId", SecurityContexts.currentUserId());
        return "admin/users";
    }

    @PostMapping
    public String create(@RequestParam String email,
                         @RequestParam Role role,
                         @RequestParam(required = false) String rosterName,
                         RedirectAttributes redirect) {

        try {
            String temporary = userService.create(email, role, rosterName);

            // Shown once, on the next page render, and never stored in readable form.
            redirect.addFlashAttribute("issuedFor", email.trim().toLowerCase());
            redirect.addFlashAttribute("issuedPassword", temporary);
            redirect.addFlashAttribute("message", "Account created.");

        } catch (UserManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            String temporary = userService.resetPassword(id);

            redirect.addFlashAttribute("issuedFor", userService.require(id).getEmail());
            redirect.addFlashAttribute("issuedPassword", temporary);
            redirect.addFlashAttribute("message", "A new temporary password was issued.");

        } catch (UserManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/active")
    public String setActive(@PathVariable Long id,
                            @RequestParam boolean active,
                            RedirectAttributes redirect) {

        try {
            userService.setActive(id, active, SecurityContexts.currentUserId());
            redirect.addFlashAttribute("message",
                    active ? "Account reactivated." : "Account deactivated.");

        } catch (UserManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam Role role,
                             RedirectAttributes redirect) {

        try {
            userService.changeRole(id, role, SecurityContexts.currentUserId());
            redirect.addFlashAttribute("message", "Role updated.");

        } catch (UserManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            userService.delete(id, SecurityContexts.currentUserId());
            redirect.addFlashAttribute("message", "Account deleted.");

        } catch (UserManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/users";
    }
}
