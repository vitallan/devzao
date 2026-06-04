package com.allanvital.devzao.web;

import com.allanvital.devzao.Constants;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
* @author Allan Vital (https://allanvital.com)
  */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public String handleUnhandled(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), e);
        return "redirect:/?" + Constants.MESSAGE_PARAM + "="
                + UriUtils.encodeQueryParam("An unexpected error occurred", StandardCharsets.UTF_8);
    }

}
