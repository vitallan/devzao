package com.allanvital.devzao.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Component
public class UmamiConfig {

    private final String scriptUrl;
    private final String websiteId;

    public UmamiConfig(
            @Value("${devzao.umami.script-url:}") String scriptUrl,
            @Value("${devzao.umami.website-id:}") String websiteId
    ) {
        this.scriptUrl = scriptUrl;
        this.websiteId = websiteId;
    }

    public String getScriptUrl() {
        return this.scriptUrl;
    }

    public String getWebsiteId() {
        return this.websiteId;
    }
}
