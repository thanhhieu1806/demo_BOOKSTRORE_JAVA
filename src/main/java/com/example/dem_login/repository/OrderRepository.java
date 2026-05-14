package com.example.dem_login.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.dem_login.model.Order;

import java.util.List;

//Phải sử dụng JpaRepository để kế thừa các phương thức CRUD
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUsername(String username);

    List<Order> findAllByOrderByCreateDateDesc();
}
