package com.example.dem_login.controller;

import com.example.dem_login.dto.Dto;
import com.example.dem_login.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    // GET /api/customers
    @GetMapping("/customers")
    public ResponseEntity<List<Dto.CustomerResponse>> getAll() {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    // GET /api/customers/{id}
    @GetMapping("/customers/{id}")
    public ResponseEntity<Dto.CustomerResponse> getById(@PathVariable Long id) {
        Dto.CustomerResponse c = customerService.getById(id);
        return c != null ? ResponseEntity.ok(c) : ResponseEntity.notFound().build();
    }

    // POST /api/customers
    @PostMapping("/customers")
    public ResponseEntity<Map<String, String>> add(@RequestBody Dto.CustomerRequest req) {
        Map<String, String> result = customerService.addCustomer(req);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST).body(result);
    }

    // PUT /api/customers/{id}
    @PutMapping("/customers/{id}")
    public ResponseEntity<Map<String, String>> update(
            @PathVariable Long id, @RequestBody Dto.CustomerRequest req) {
        Map<String, String> result = customerService.updateCustomer(id, req);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(result);
    }

    // DELETE /api/customers/{id}
    @DeleteMapping("/customers/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.deleteCustomer(id));
    }
}