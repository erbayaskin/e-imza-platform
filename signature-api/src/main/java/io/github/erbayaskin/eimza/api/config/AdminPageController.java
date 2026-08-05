package io.github.erbayaskin.eimza.api.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {
    @GetMapping({"/admin", "/admin/"})
    String admin() {
        return "redirect:/admin/index.html";
    }
}
