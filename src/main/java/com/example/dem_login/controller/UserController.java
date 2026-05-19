package com.example.dem_login.controller;

import org.springframework.web.bind.annotation.*;
import com.example.dem_login.dto.Dto;
import com.example.dem_login.service.GoogleAuthService;
import com.example.dem_login.service.UserService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private GoogleAuthService googleAuthService;

    // GOOGLE LOGIN
    @PostMapping("/auth/google")
    public ResponseEntity<Dto.LoginResponse> loginWithGoogle(@RequestBody Dto.GoogleLoginRequest req) {
        Dto.LoginResponse res = googleAuthService.loginWithGoogle(req.getToken());
        HttpStatus status = res.isSuccess() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(res);
    }

    // LOGIN
    @PostMapping("/login")
    public ResponseEntity<Dto.LoginResponse> login(@RequestBody Dto.LoginRequest req) {
        Dto.LoginResponse res = userService.Login(req.getUsername(), req.getPassword());
        HttpStatus status = res.isSuccess() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(res);
    }

    // REGISTER
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody Dto.RegisterRequest req) {
        Map<String, String> result = userService.register(req);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST).body(result);
    }

    // GET ALL USERS
    @GetMapping("/users")
    public ResponseEntity<List<Dto.UserResponse>> getUser() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // GET USER BY ID
    @GetMapping("/users/{id}")
    public ResponseEntity<Dto.UserResponse> getUser(@PathVariable Long id) {
        Dto.UserResponse user = userService.getUserById(id);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }

    // EDIT USER
    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> editUser(
            @PathVariable Long id, @RequestBody Dto.EditUserRequest req) {
        Map<String, String> result = userService.editUser(id, req);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(result);
    }

    // DELETE USER
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.deleteUser(id));
    }

    // UNLOCK USER
    @PatchMapping("/users/{id}/unlock")
    public ResponseEntity<Map<String, String>> unLockUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.unLockUser(id));
    }

    // RESET DATABASE
    @PostMapping("/admin/reset")
    public ResponseEntity<Map<String, String>> resetDatabase() {
        return ResponseEntity.ok(userService.resetDatabase());
    }

    // ADD USER (ADMIN)
    @PostMapping("/users")
    public ResponseEntity<Dto.CreateUserResponse> addUser(@RequestBody Dto.CreateUserRequest req) {
        Dto.CreateUserResponse res = userService.addUser(req);
        HttpStatus status = res.isSuccess() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(res);
    }

    // UPDATE PROFILE
    @PatchMapping("/users/profile")
    public ResponseEntity<Map<String, String>> updateProfile(@RequestBody Dto.UpdateProfileRequest req) {
        Map<String, String> result = userService.updateProfile(req);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(result);
    }

    // FORCE CHANGE PASSWORD
    @PostMapping("/users/force-change-password")
    public ResponseEntity<Map<String, String>> forceChangePassword(
            @RequestBody Dto.ForceChangePasswordRequest req) {
        Map<String, String> result = userService.forceChangePassword(req);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(result);
    }

    // GET CART
    @GetMapping("/users/{username}/cart")
    public ResponseEntity<Map<String, String>> getCart(@PathVariable String username) {
        String cartData = userService.getCartData(username);
        return ResponseEntity.ok(Map.of("cartData", cartData != null ? cartData : "[]"));
    }

    // SYNC CART
    @PostMapping("/users/cart/sync")
    public ResponseEntity<Map<String, String>> syncCart(@RequestBody Dto.SyncCartRequest req) {
        Map<String, String> result = userService.syncCartData(req);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(result);
    }
}