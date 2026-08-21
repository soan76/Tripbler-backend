package com.tripbler.backend.admin.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @GetMapping("/test")
    public Map<String, String> test() {
        return Map.of(
            "message",
            "관리자 권한 접근 성공"
        );
    }
}