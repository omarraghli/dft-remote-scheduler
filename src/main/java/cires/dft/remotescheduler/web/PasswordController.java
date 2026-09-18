package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.repository.AppUserRepository;
import cires.dft.remotescheduler.security.AppUserPrincipal;
import cires.dft.remotescheduler.security.SecurityContexts;
import cires.dft.remotescheduler.service.UserManagementException;
import cires.dft.remotescheduler.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordController {

    private final UserService userService;
    private final AppUserRepository users;

    public PasswordController(UserService userService, AppUserRepository users) {
        this.userService = userService;
        this.users = users;
    }

    @GetMapping("/password")
    public String form(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("forced", principal != null && principal.isMustChangePassword());
        model.addAttribute("email", principal == null ? "" : principal.getUsername());
        return "password";
    }

    @PostMapping("/password")
    public String change(@AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam String currentPassword,
                         @RequestParam String newPassword,
                         @RequestParam String confirmPassword,
                         RedirectAttributes redirect,
                         Model model) {

        try {
            userService.changeOwnPassword(principal.getUsername(), currentPassword,
                    newPassword, confirmPassword);

        } catch (UserManagementException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("forced", principal.isMustChangePassword());
            model.addAttribute("email", principal.getUsername());
            return "password";
        }

        // The must-change flag is carried on the principal, so refresh it or the filter will
        // send them straight back here.
        users.findByEmail(AppUser.normaliseEmail(principal.getUsername()))
                .ifPresent(SecurityContexts::refresh);

        redirect.addFlashAttribute("message", "Your password has been changed.");
        return "redirect:/";
    }
}
