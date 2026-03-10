package carelog.carelog.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping


@Controller
public class RootController {

    @GetMapping("/")
    fun redirectSwagger(): String = "redirect:/swagger-ui.html"
}
