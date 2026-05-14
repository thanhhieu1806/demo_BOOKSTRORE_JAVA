package com.example.dem_login.repository;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.ArrayList;
import java.util.List;
import com.example.dem_login.model.Customer;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import java.util.Optional;

@Repository
public class JsonCustomerRepository {

    @Value("${data.customer.file.path:data/customer.json}")
    private String filePath;

    private final ObjectMapper mapper;
    private List<Customer> customers;
    private Long nextId = 1L;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonCustomerRepository() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());// xu ly
    }

    @PostConstruct
    public void init() throws IOException {
        // kiem tra file co ton tai khong
        File file = new File(filePath);
        if (!file.exists()) {
            // Neu file khong ton tai thi tao moi
            file.getParentFile().mkdirs();
            // ghi vao file danh sach customer rong
            mapper.writeValue(file, new ArrayList<>());
        }
        // doc danh sach customer tu file json
        loadFromFile();
        // tinh toan id lon nhat
        nextId = customers.stream()
                .mapToLong(Customer::getId)
                .max().orElse(0L) + 1;
    }

    // doc danh sach customer tu file json
    private void loadFromFile() {
        // lock.readLock().lock()//lock read: nhieu process doc cung luc duoc
        //
        lock.readLock().lock();
        try {
            File file = new File(filePath);
            if (file.exists() && file.length() > 0) {
                // readValue doc file json vaf tra ve danh sach customer
                customers = mapper.readValue(file, new TypeReference<List<Customer>>() {
                });
            } else {
                // Neu file khong ton tai hoac file rong thi tao moi danh sach rong
                customers = new ArrayList<>();
            }
        } catch (IOException e) {
            throw new RuntimeException("Không thể đọc file customer.json", e);
        } finally {
            lock.readLock().unlock();
        }
    }

    // luu danh sach customer vao file json
    private void saveToFile() {
        lock.writeLock().lock();
        try {
            // writeWithDefaultPrettyPrinter() để định dạng file json cho dễ đọc
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), customers);

        } catch (IOException e) {
            throw new RuntimeException("Không thể ghi file customer.json", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // lay danh sach tat ca customer
    public List<Customer> findAll() {
        return new ArrayList<>(customers);
    }

    // Hàm tìm customer theo id
    public Optional<Customer> findById(Long id) {

        // customers.stream()
        // Chuyển List<Customer> thành Stream để xử lý dữ liệu
        return customers.stream()

                // filter(...)
                // Lọc ra customer có id trùng với id truyền vào
                .filter(c -> c.getId().equals(id))

                // findFirst()
                // Lấy customer đầu tiên tìm được
                .findFirst();
    }

    // kiem tra số điện thoại đã tồn tại chưa
    // anyMatch(c->c.getPhone().equals(phone)) :kiem tra xem co customer nao co so
    // dien thoai trung khong
    public boolean existsByPhone(String phone) {

        for (Customer customer : customers) {

            // lấy số điện thoại của customer hiện tại
            String customerPhone = customer.getPhone();

            // kiểm tra có trùng không
            if (customerPhone.equals(phone)) {
                return true;
            }
        }

        // duyệt hết mà không thấy
        return false;
    }

    public Customer save(Customer customer) {
        // lock write: chi co 1 process ghi duoc cung luc
        lock.writeLock().lock();
        try {
            if (customer.getId() == null) {// neu customer chua co id thi la them moi
                customer.setId(nextId++);// neu customer da co id thi la cap nhat
                customers.add(customer);// customers.add(customer): them customer moi
            } else {
                // removeIf(c->c.getId().equals(customer.getId())) :xoa customer cu
                // c->c:lambda expression
                customers.removeIf(c -> c.getId().equals(customer.getId()));
                customers.add(customer);
            }
            // luu danh sach customer vao file json
            saveToFile();
            return customer;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean deleteById(Long id) {
        lock.writeLock().lock();
        try {
            boolean removed = customers.removeIf(c -> c.getId().equals(id));
            if (removed)
                saveToFile();
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long count() {
        return customers.size();
    }
}
