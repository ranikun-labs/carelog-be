
package carelog.carelog.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {

    @GetMapping("/")
    public String redirectSwagger() {
        return "redirect:/swagger-ui.html";
    }
}
