package vn.fs.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.fs.service.PasswordResetService;

@Controller
@RequestMapping("/forgot-password")
public class ResetPasswordController {
    private final PasswordResetService reset;
    public ResetPasswordController(PasswordResetService reset){this.reset=reset;}
    @GetMapping("/{token}")
    public String form(Model model,@PathVariable String token) {
        var user=reset.validUser(token);
        if(user.isEmpty())return "redirect:/forgot-password/error";
        model.addAttribute("name",user.get().getName());model.addAttribute("email",user.get().getEmail());
        model.addAttribute("token",token);return "reset-password";
    }
    @PostMapping
    public String consume(@RequestParam String password,@RequestParam String confirm,@RequestParam String token,Model model) {
        if(password.length()<6 || password.length()>128 || !password.equals(confirm)) {
            String view=form(model,token);
            model.addAttribute(password.equals(confirm)?"errorPassword":"errorConfirm","error");return view;
        }
        return reset.consume(token,password)?"redirect:/forgot-password/done":"redirect:/forgot-password/error";
    }
    @GetMapping("/done") public String done(){return "done";}
    @GetMapping("/error") public String error(){return "error";}
}
