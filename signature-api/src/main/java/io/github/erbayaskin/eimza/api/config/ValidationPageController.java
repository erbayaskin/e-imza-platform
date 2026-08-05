package io.github.erbayaskin.eimza.api.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class ValidationPageController {

    @GetMapping({"/validation", "/validation/"})
    String validation() {
        return "redirect:/validation/index.html";
    }
}
