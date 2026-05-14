package com.example.dem_login.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.dem_login.repository.BookRepository;
import com.example.dem_login.repository.CustomerProfileRepository;
import com.example.dem_login.repository.OrderRepository;
import jakarta.transaction.Transactional;
import java.util.Map;

import com.example.dem_login.model.Book;
import com.example.dem_login.model.CustomerProfile;
import com.example.dem_login.model.Order;
import com.example.dem_login.model.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final BookRepository bookRepository;

    public OrderService(OrderRepository orderRepository, BookRepository bookRepository) {
        this.orderRepository = orderRepository;
        this.bookRepository = bookRepository;
    }

    // lay tat ca don hang admin
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreateDateDesc();
    }

    @Autowired
    private CustomerProfileRepository profileRepo;

    // thong tin don hang theo username
    public List<Order> getOrdersByUser(String username) {
        return orderRepository.findByUsername(username);
    }

    @Transactional
    public Map<String, String> checkout(String username, String customerName, String phone, String address,
            List<Map<String, Object>> cartItems) {
        if (cartItems == null || cartItems.isEmpty())
            return Map.of("success", "false", "message", " giỏ hàng trỗng");

        Order order = new Order();
        order.setUsername(username);
        order.setCustomerName(customerName);
        order.setPhone(phone);
        order.setAddress(address);
        order.setCreateDate(LocalDateTime.now());
        order.setUpdateDate(LocalDateTime.now());
        order.setStatus(Order.OrderStatus.PENDING);

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map<String, Object> cartItem : cartItems) {
            Long bookId = Long.valueOf(cartItem.get("bookId").toString());
            int quantity = Integer.parseInt(cartItem.get("quantity").toString());

            Book book = bookRepository.findById(bookId).orElse(null);
            if (book == null)
                return Map.of("success", "false", "message", "sách id" + bookId + "không tìm thấy");
            if (book.getQuantity() < quantity)
                return Map.of("success", "false", "message", "Sách" + book.getTitle() + "Không đủ hàng");

            // tru ton kho
            book.setQuantity(book.getQuantity() - quantity);
            bookRepository.save(book);

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setBookId(bookId);
            item.setBookTitle(book.getTitle());
            item.setQuantity(quantity);
            item.setPrice(book.getPrice());

            BigDecimal itemSubtotal = book.getPrice().multiply(BigDecimal.valueOf(quantity));
            item.setSubtotal(itemSubtotal);

            items.add(item);
            total = total.add(item.getSubtotal());
        }
        order.setItems(items);
        order.setTotalAmount(total);
        orderRepository.save(order);

        CustomerProfile profile = profileRepo.findByUsername(username)
                .orElse(new CustomerProfile());
        profile.setUsername(username);
        profile.setCustomerName(customerName);
        profile.setPhone(phone);
        profile.setAddress(address);
        profile.setUpdateDate(LocalDateTime.now());
        if (profile.getCreateDate() == null)
            profile.setCreateDate(LocalDateTime.now());

        profileRepo.save(profile);

        return Map.of("success", "true", "message", "đặt hàng thành công" + order.getId());

    }

    // xac nhan huy don hang
    public Map<String, String> updateOrderStatus(Long id, String status) {
        return orderRepository.findById(id).map(order -> {
            try {
                order.setStatus(Order.OrderStatus.valueOf(status));
                order.setUpdateDate(LocalDateTime.now());
                orderRepository.save(order);
                return Map.of("Success", "true", "message", "Cập nhật trạng thái thành công");

            } catch (Exception e) {
                return Map.of("Success", "false", "message", "Trạng thái không hợp lệ");
            }
        }).orElse(Map.of("Success", "false", "message", "Không tìm thấy đơn hàng"));
    }

    @Transactional
    public Map<String, String> updateOrderForUser(Long id, String username, String customerName, String phone,
            String address) {
        return orderRepository.findById(id).map(order -> {
            if (!order.getUsername().equals(username)) {
                return Map.of("success", "false", "message", "Bạn không có quyền sửa đơn hàng này");
            }
            if (order.getStatus() != Order.OrderStatus.PENDING) {
                return Map.of("success", "false", "message", "Chỉ được sửa đơn hàng đang chờ xác nhận");
            }
            if (customerName == null || customerName.trim().isEmpty()) {
                return Map.of("success", "false", "message", "Tên người nhận không được trống");
            }
            if (phone == null || phone.trim().isEmpty()) {
                return Map.of("success", "false", "message", "Số điện thoại không được trống");
            }
            if (address == null || address.trim().isEmpty()) {
                return Map.of("success", "false", "message", "Địa chỉ không được trống");
            }

            order.setCustomerName(customerName.trim());
            order.setPhone(phone.trim());
            order.setAddress(address.trim());
            order.setUpdateDate(LocalDateTime.now());
            orderRepository.save(order);
            return Map.of("success", "true", "message", "Cập nhật đơn hàng thành công");
        }).orElse(Map.of("success", "false", "message", "Không tìm thấy đơn hàng"));
    }

    @Transactional
    public Map<String, String> deleteOrderForUser(Long id, String username) {
        return orderRepository.findById(id).map(order -> {
            if (!order.getUsername().equals(username)) {
                return Map.of("success", "false", "message", "Bạn không có quyền xóa đơn hàng này");
            }
            if (order.getStatus() != Order.OrderStatus.PENDING) {
                return Map.of("success", "false", "message", "Chỉ được xóa đơn hàng đang chờ xác nhận");
            }

            for (OrderItem item : order.getItems()) {
                bookRepository.findById(item.getBookId()).ifPresent(book -> {
                    book.setQuantity(book.getQuantity() + item.getQuantity());
                    bookRepository.save(book);
                });
            }
            orderRepository.delete(order);
            return Map.of("success", "true", "message", "Xóa đơn hàng thành công");
        }).orElse(Map.of("success", "false", "message", "Không tìm thấy đơn hàng"));
    }

}
