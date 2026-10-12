package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public Map<String, Object> sayHello(@RequestParam(defaultValue = "World") String name) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Hello, " + name + "!");
        return response;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Spring Boot Gradle CI Cache Demo");
        return response;
    }
}
