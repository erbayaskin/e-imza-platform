package io.github.erbayaskin.eimza.api.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class DemoPageController {

    @GetMapping({"/demo", "/demo/"})
    String demo() {
        return "redirect:/demo/index.html";
    }

    @GetMapping({"/multi-signature", "/multi-signature/"})
    String multiSignature() {
        return "redirect:/multi-signature/index.html";
    }
}
