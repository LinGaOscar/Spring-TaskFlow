package com.taskflow.user;

import com.taskflow.common.ApiResponse;
import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @GetMapping("/api/users/me")
    @ResponseBody
    public ApiResponse<UserDto.Response> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        return ApiResponse.ok(UserDto.Response.from(user));
    }

    // 成員管理限科內帳號：只回傳呼叫者本科（同 department）的啟用帳號，避免跨科帳號被
    // 誤加入本科看板成員（部門隔離旁路）。DIRECTOR 掛在部層級，這裡僅回傳同 department（部本身，
    // 不含子科），與看板成員的同科驗證邏輯一致，也是最簡單的實作方式
    @GetMapping("/api/users")
    @ResponseBody
    public ApiResponse<List<UserDto.Response>> listUsers(
            @AuthenticationPrincipal UserDetails userDetails) {
        User caller = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        if (caller.getDepartment() == null) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(userService.listUsers(caller.getDepartment().getId()).stream()
            .map(UserDto.Response::from).toList());
    }

    @GetMapping("/api/departments")
    @ResponseBody
    public ApiResponse<List<Map<String, Object>>> listDepartments() {
        List<Department> depts = departmentRepository.findAll();
        return ApiResponse.ok(depts.stream()
            .map(d -> Map.<String, Object>of("id", d.getId(), "name", d.getName()))
            .toList());
    }
}
