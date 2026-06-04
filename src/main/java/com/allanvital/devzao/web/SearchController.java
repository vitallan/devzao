package com.allanvital.devzao.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Controller
public class SearchController {

    @PostMapping("/search")
    public String search(@RequestParam("login") String login) {
        String trimmed = login == null ? "" : login.trim();
        if (trimmed.isBlank()) {
            return "redirect:/";
        }
        if (!trimmed.matches("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?$")) {
            return "redirect:/";
        }
        return "redirect:/u/" + trimmed;
    }
}
