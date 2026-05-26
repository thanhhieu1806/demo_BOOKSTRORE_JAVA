package com.example.dem_login.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    // Tất cả request không phải /api và không phải trang chủ sẽ trả về index.html
    @GetMapping({
            "/login",
            "/dashboard",
            "/users",
            "/users/**",
            "/customers",
            "/customers/**",
            "/books",
            "/books/**",
            "/cart",
            "/cart/**",
            "/orders",
            "/orders/**",
            "/chat",
            "/chat/**",
            "/change-password",
            "/profile",
            "/register",
            "/register/**",
            "/settings",
            "/book-detail"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
