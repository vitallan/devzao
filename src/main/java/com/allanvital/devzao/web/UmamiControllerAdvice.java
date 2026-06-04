package com.allanvital.devzao.web;

import com.allanvital.devzao.config.UmamiConfig;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
* @author Allan Vital (https://allanvital.com)
  */
@ControllerAdvice
public class UmamiControllerAdvice {

    private final UmamiConfig umamiConfig;

    public UmamiControllerAdvice(UmamiConfig umamiConfig) {
        this.umamiConfig = umamiConfig;
    }

    @ModelAttribute("umamiScriptUrl")
    public String umamiScriptUrl() {
        return this.umamiConfig.getScriptUrl();
    }

    @ModelAttribute("umamiWebsiteId")
    public String umamiWebsiteId() {
        return this.umamiConfig.getWebsiteId();
    }
}
