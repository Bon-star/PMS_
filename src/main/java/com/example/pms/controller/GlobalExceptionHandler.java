package com.example.pms.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public String handleMethodNotSupported(HttpServletRequest req, HttpRequestMethodNotSupportedException ex, Model model) {
        log.info("Method not allowed: {} {} -> {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        model.addAttribute("error", "Yêu cầu không hợp lệ (phương thức không được hỗ trợ). Vui lòng thử lại.");
        return "error";
    }
}