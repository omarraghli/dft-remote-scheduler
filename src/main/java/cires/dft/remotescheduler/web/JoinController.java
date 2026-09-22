package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.security.SecurityContexts;
import cires.dft.remotescheduler.service.TeamInviteService;
import cires.dft.remotescheduler.service.UserManagementException;
import cires.dft.remotescheduler.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Where the team link lands. Open to anybody holding it — the link is the invitation — and the
 * account it creates is an ordinary one, tied to the name its owner picked.
 */
@Controller
public class JoinController {

    /** The picker's "I am not on the list" choice, which switches to typing a name. */
    private static final String NEW = "__new__";

    private final UserService userService;
    private final TeamInviteService invites;

    public JoinController(UserService userService, TeamInviteService invites) {
        this.userService = userService;
        this.invites = invites;
    }

    @GetMapping("/join/{token}")
    public String form(@PathVariable String token, Model model) {
        return render(token, null, null, null, null, model);
    }

    @PostMapping("/join/{token}")
    public String join(@PathVariable String token,
                       @RequestParam(required = false) String rosterName,
                       @RequestParam(required = false) String newName,
                       @RequestParam String email,
                       @RequestParam String password,
                       @RequestParam String confirmPassword,
                       HttpServletRequest request,
                       HttpServletResponse response,
                       RedirectAttributes redirect,
                       Model model) {

        AppUser user;
        try {
            user = userService.join(token, NEW.equals(rosterName) ? null : rosterName, newName,
                    email, password, confirmPassword);

        } catch (UserManagementException e) {
            return render(token, e.getMessage(), rosterName, newName, email, model);
        }

        SecurityContexts.signIn(user, request, response);

        redirect.addFlashAttribute("message", "Welcome, " + user.getRosterName()
                + ". If you have leave coming up, add it here so your weeks are planned around it.");
        return "redirect:/leave";
    }

    private String render(String token, String error, String rosterName, String newName,
                          String email, Model model) {
        try {
            invites.requireValid(token);
        } catch (UserManagementException e) {
            model.addAttribute("invalid", e.getMessage());
            return "join";
        }

        model.addAttribute("token", token);
        model.addAttribute("names", userService.unclaimedNames());
        model.addAttribute("domain", invites.allowedDomain());
        model.addAttribute("error", error);
        model.addAttribute("rosterName", rosterName);
        model.addAttribute("newName", newName);
        model.addAttribute("newOption", NEW);
        model.addAttribute("email", email);
        return "join";
    }
}
