package com.example.dem_login.service;

import com.example.dem_login.repository.JsonCustomerRepository;
import org.springframework.stereotype.Service;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import com.example.dem_login.dto.Dto;
import com.example.dem_login.model.Customer;
import java.time.LocalDateTime;

@Service
public class CustomerService {
    // tu injection repository
    private final JsonCustomerRepository repository;

    // format ngay thang nam sinh va ngay thang nam tao tai khoan
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public CustomerService(JsonCustomerRepository repository) {
        this.repository = repository;
    }

    // lay danh sachtatca khach hang
    public List<Dto.CustomerResponse> getAllCustomers() {
        return repository.findAll().stream() // lay tat ca khach hang
                .map(this::toResponse) // convert sang response
                .collect(Collectors.toList()); // collect thanh list
    }

    // lay thong tin khach hang theo id
    public Dto.CustomerResponse getById(Long id) {
        return repository.findById(id).map(this::toResponse).orElse(null);
        // neu co id thi tra ve response
        // neu id khong ton tai tra ve null
    }

    // them khach hanh
    public Map<String, String> addCustomer(Dto.CustomerRequest req) {
        // kiem tra thong tin nhap vao co du khong
        // kiem tra so dien thoai da ton tai hay chua
        if (req.getFullName() == null || req.getFullName().isBlank())
            return Map.of("success", "false", "message", "Họ tên không được để trống!");
        if (req.getPhone() == null || req.getPhone().isBlank())
            return Map.of("success", "false", "message", "Số điện thoại không được để trống!");
        if (repository.existsByPhone(req.getPhone()))
            return Map.of("success", "false", "message", "Số điện thoại đã tồn tại!");

        // tao customer moi
        Customer customer = new Customer();
        customer.setFullName(req.getFullName());// set ho ten
        customer.setPhone(req.getPhone());// set so dien thoai
        customer.setEmail(req.getEmail());// set email
        customer.setAddress(req.getAddress());// set dia chi
        customer.setDecription(req.getDecription());// set mo ta
        customer.setStatus(Customer.CustomerStatus.ACTIVE);// set trang thai mac dinh la active
        customer.setCreateDate(LocalDateTime.now());// set ngay tao la ngay hien tai
        customer.setUpdateDate(LocalDateTime.now());// set ngay cap nhat la ngay hien tai
        repository.save(customer);// luu customer vao database
        return Map.of("success", "true", "message", "Thêm khách hàng thành công!");
    }

    // cap nhatthongtin khach hang
    public Map<String, String> updateCustomer(Long id, Dto.CustomerRequest req) {
        // tim id khach hang
        return repository.findById(id).map(customer -> {
            // cap nhat thong tin neu khong null
            if (req.getFullName() != null)
                customer.setFullName(req.getFullName());
            // req = tờ form người dùng nhập
            // customer = dữ liệu trong database
            // getFullName() = lấy tên từ form
            // setFullName() = cập nhật tên vào database object
            if (req.getPhone() != null)
                customer.setPhone(req.getPhone());
            if (req.getEmail() != null)
                customer.setEmail(req.getEmail());
            if (req.getAddress() != null)
                customer.setAddress(req.getAddress());
            if (req.getDecription() != null)
                customer.setDecription(req.getDecription());
            if (req.getStatus() != null) {
                try {
                    customer.setStatus(Customer.CustomerStatus.valueOf(req.getStatus()));// gan trang thai moi
                } catch (Exception ignored) {
                }
            }
            customer.setUpdateDate(LocalDateTime.now());// cap nhat ngay cap nhat
            repository.save(customer);// luu customer vao database
            return Map.of("success", "true", "message", "Cập nhật thành công!");
        }).orElseGet(() -> Map.of("success", "false", "message", "Không tìm thấy khách hàng!"));
    }

    // xoa khach hang
    public Map<String, String> deleteCustomer(Long id) {
        boolean ok = repository.deleteById(id); // xoa id khach hang
        if (ok)
            return Map.of("success", "true", "message", "Xóa thành công!");
        else
            return Map.of("success", "false", "message", "Không tìm thấy khách hàng!");
    }

    // convert customer sang respone
    // Hàm chuyển Customer -> CustomerResponse
    private Dto.CustomerResponse toResponse(Customer customer) {

        // Tạo object response rỗng để trả về frontend
        Dto.CustomerResponse response = new Dto.CustomerResponse();

        // Lấy id từ customer gán sang response
        response.setId(customer.getId());

        // Lấy họ tên từ customer gán sang response
        response.setFullName(customer.getFullName());

        // Lấy số điện thoại từ customer gán sang response
        response.setPhone(customer.getPhone());

        // Nếu email khác null thì lấy email
        // nếu null thì gán chuỗi rỗng ""
        response.setEmail(
                customer.getEmail() != null
                        ? customer.getEmail()
                        : "");

        // Nếu address khác null thì lấy address
        // nếu null thì gán ""
        response.setAddress(
                customer.getAddress() != null
                        ? customer.getAddress()
                        : "");

        // Nếu description khác null thì lấy description
        // nếu null thì gán ""
        response.setDecription(
                customer.getDecription() != null
                        ? customer.getDecription()
                        : "");

        // Nếu status khác null
        // -> convert enum sang String bằng .name()
        // nếu null thì mặc định ACTIVE
        response.setStatus(
                customer.getStatus() != null
                        ? customer.getStatus().name()
                        : "ACTIVE");

        // Nếu createDate khác null
        // -> format ngày thành String theo FMT
        // nếu null thì trả ""
        response.setCreateDate(
                customer.getCreateDate() != null
                        ? customer.getCreateDate().format(FMT)
                        : "");

        // Nếu updateDate khác null
        // -> format ngày thành String
        // nếu null thì trả ""
        response.setUpdateDate(
                customer.getUpdateDate() != null
                        ? customer.getUpdateDate().format(FMT)
                        : "");

        // Trả dữ liệu response ra ngoài
        return response;
    }
}
