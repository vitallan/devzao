package com.allanvital.devzao.web;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record LanguageShareView(
        String language,
        long repositoryCount,
        int percentage,
        String color
) {
}
