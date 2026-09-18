package cires.dft.remotescheduler.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        Model model) {

        // Deliberately one message for every failure: wrong password, unknown address and
        // deactivated account all look the same from outside.
        if (error != null) model.addAttribute("error", "That email and password do not match.");
        if (logout != null) model.addAttribute("message", "You are signed out.");

        return "login";
    }
}
