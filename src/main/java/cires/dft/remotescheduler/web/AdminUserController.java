package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.security.SecurityContexts;
import cires.dft.remotescheduler.service.RosterService;
import cires.dft.remotescheduler.service.TeamInviteService;
import cires.dft.remotescheduler.service.UserManagementException;
import cires.dft.remotescheduler.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.Principal;
import java.util.List;

/**
 * The team, and everybody who can sign in — one list, because an account is a person. The whole
 * path is admin-only, enforced in SecurityConfig.
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final String BACK = "redirect:/admin/users";

    private final UserService userService;
    private final RosterService roster;
    private final TeamInviteService invites;
    private final RemoteScheduleProperties properties;

    public AdminUserController(UserService userService,
                               RosterService roster,
                               TeamInviteService invites,
                               RemoteScheduleProperties properties) {
        this.userService = userService;
        this.roster = roster;
        this.invites = invites;
        this.properties = properties;
    }

    @GetMapping
    public String list(Model model) {
        List<AppUser> users = userService.findAllByName();
        long planned = roster.activeNames().size();
        long joined = users.stream()
                .filter(u -> u.isActive() && u.isOnSchedule() && u.hasJoined())
                .count();
        int quota = properties.getRemotesPerPerson();

        model.addAttribute("users", users);
        model.addAttribute("roles", Role.values());
        model.addAttribute("meId", SecurityContexts.currentUserId());
        model.addAttribute("plannedCount", planned);
        model.addAttribute("joinedCount", joined);
        model.addAttribute("quota", quota);
        model.addAttribute("needed", planned * quota);
        model.addAttribute("slots", properties.getSlotsPerDay().stream()
                .mapToInt(Integer::intValue).sum());
        model.addAttribute("invite", invites.live().orElse(null));
        model.addAttribute("domain", invites.allowedDomain());
        return "admin/users";
    }

    @PostMapping
    public String add(@RequestParam(required = false) String name,
                      @RequestParam(required = false) String email,
                      @RequestParam(defaultValue = "USER") Role role,
                      @RequestParam(defaultValue = "false") boolean onSchedule,
                      RedirectAttributes redirect) {

        try {
            String temporary = userService.add(name, email, role, onSchedule);

            if (temporary != null) {
                // Shown once, on the next page render, and never stored in readable form.
                redirect.addFlashAttribute("issuedFor", email.trim().toLowerCase());
                redirect.addFlashAttribute("issuedPassword", temporary);
            }
            redirect.addFlashAttribute("message", temporary != null
                    ? "Added, with a temporary password."
                    : name.trim() + " is on the team. They sign up with the join link.");

        } catch (UserManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return BACK;
    }

    @PostMapping("/{id}/rename")
    public String rename(@PathVariable Long id, @RequestParam String name,
                         RedirectAttributes redirect) {
        return attempt(redirect, "Renamed — past weeks and leave follow.",
                () -> roster.rename(id, name));
    }

    @PostMapping("/{id}/schedule")
    public String setOnSchedule(@PathVariable Long id, @RequestParam boolean onSchedule,
                                RedirectAttributes redirect) {
        return attempt(redirect, onSchedule
                        ? "On the schedule from the next week generated."
                        : "Off the schedule from the next week generated.",
                () -> userService.setOnSchedule(id, onSchedule));
    }

    @PostMapping("/{id}/unjoin")
    public String unjoin(@PathVariable Long id, RedirectAttributes redirect) {
        return attempt(redirect, "Sign-up undone. The name can be claimed again with the join link.",
                () -> userService.unjoin(id, SecurityContexts.currentUserId()));
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

        return BACK;
    }

    /** Deactivating is how somebody leaves: off the schedule and unable to sign in, but kept. */
    @PostMapping("/{id}/active")
    public String setActive(@PathVariable Long id,
                            @RequestParam boolean active,
                            RedirectAttributes redirect) {
        return attempt(redirect, active
                        ? "Reactivated."
                        : "Deactivated. Weeks already planned keep them; the next one will not.",
                () -> userService.setActive(id, active, SecurityContexts.currentUserId()));
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam Role role,
                             RedirectAttributes redirect) {
        return attempt(redirect, "Role updated.",
                () -> userService.changeRole(id, role, SecurityContexts.currentUserId()));
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        return attempt(redirect, "Deleted.",
                () -> userService.delete(id, SecurityContexts.currentUserId()));
    }

    @PostMapping("/invite")
    public String issueInvite(Principal principal, HttpServletRequest request,
                              RedirectAttributes redirect) {

        String token = invites.issue(principal == null ? null : principal.getName());
        String link = ServletUriComponentsBuilder.fromContextPath(request)
                .path("/join/{token}")
                .buildAndExpand(token)
                .toUriString();

        // Shown once, on the next render. Only its hash is kept.
        redirect.addFlashAttribute("inviteLink", link);
        redirect.addFlashAttribute("message", "New join link issued. Any older one stops working.");
        return BACK;
    }

    @PostMapping("/invite/revoke")
    public String revokeInvite(RedirectAttributes redirect) {
        invites.revoke();
        redirect.addFlashAttribute("message",
                "Join link revoked. Nobody new can sign up until you issue another.");
        return BACK;
    }

    private String attempt(RedirectAttributes redirect, String done, Runnable change) {
        try {
            change.run();
            redirect.addFlashAttribute("message", done);

        } catch (UserManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return BACK;
    }
}
