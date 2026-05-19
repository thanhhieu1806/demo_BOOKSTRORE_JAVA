package com.example.dem_login.controller;

import com.example.dem_login.service.OrderService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.example.dem_login.repository.CustomerProfileRepository;
import com.example.dem_login.model.CustomerProfile;
import com.example.dem_login.model.Order;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin
public class OrderController {
    private final OrderService orderService;
    private final CustomerProfileRepository profileRepo;

    public OrderController(OrderService orderService, CustomerProfileRepository profileRepo) {
        this.orderService = orderService;
        this.profileRepo = profileRepo;
    }

    // Admin lay tat ca don
    @GetMapping
    public ResponseEntity<List<Order>> getAll() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    // User:laydon cua minh
    @GetMapping("/user/{username}")
    public ResponseEntity<List<Order>> getByUser(@PathVariable String username) {
        return ResponseEntity.ok(orderService.getOrdersByUser(username));
    }

    // thanh toan
    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> checkout(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String customerName = (String) body.get("customerName");
        String phone = (String) body.get("phone");
        String address = (String) body.get("address");
        List<Map<String, Object>> cartitems = (List<Map<String, Object>>) body.get("items");

        Map<String, String> result = orderService.checkout(username, customerName, phone, address, cartitems);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST).body(result);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, String>> UpdateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, body.get("status")));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> updateOrder(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Map<String, String> result = orderService.updateOrderForUser(
                id,
                body.get("username"),
                body.get("customerName"),
                body.get("phone"),
                body.get("address"));
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteOrder(
            @PathVariable Long id,
            @RequestParam String username) {
        Map<String, String> result = orderService.deleteOrderForUser(id, username);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(result);
    }

    @GetMapping("/profile/{username}")
    public ResponseEntity<CustomerProfile> getProfile(
            @PathVariable String username) {
        return profileRepo.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/profile/update")
    public ResponseEntity<Map<String, String>> updateProfile(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String customerName = body.get("customerName");
        String phone = body.get("phone");
        String address = body.get("address");
        String email = body.get("email");

        CustomerProfile profile = profileRepo.findByUsername(username).orElse(new CustomerProfile());
        profile.setUsername(username);
        profile.setCustomerName(customerName);
        profile.setPhone(phone);
        profile.setAddress(address);
        profile.setEmail(email);
        profile.setUpdateDate(java.time.LocalDateTime.now());
        if (profile.getCreateDate() == null) {
            profile.setCreateDate(java.time.LocalDateTime.now());
        }
        profileRepo.save(profile);

        return ResponseEntity.ok(Map.of("success", "true", "message", "Cập nhật thành công"));
    }

}
