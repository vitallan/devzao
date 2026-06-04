package com.allanvital.devzao.web;

import com.allanvital.devzao.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Controller
public class HomeController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @GetMapping("/")
    public String home(
            @RequestParam(name = Constants.MESSAGE_PARAM, required = false) String message,
            @RequestParam(name = Constants.LOGIN_PARAM, required = false) String login,
            Model model
    ) {
        model.addAttribute("message", message);
        model.addAttribute("login", login);
        return "home";
    }
}
