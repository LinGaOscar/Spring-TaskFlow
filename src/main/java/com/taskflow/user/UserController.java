package com.taskflow.user;

import com.taskflow.common.ApiResponse;
import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
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

    @GetMapping("/admin/users")
    public String usersPage(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        // 只有科長以上可管理人員
        if (user.getRole() != User.Role.SECTION_CHIEF && user.getRole() != User.Role.DIRECTOR) {
            return "redirect:/boards";
        }
        return "admin/users";
    }

    @GetMapping("/api/users/me")
    @ResponseBody
    public ApiResponse<UserDto.Response> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        return ApiResponse.ok(UserDto.Response.from(user));
    }

    @GetMapping("/api/users")
    @ResponseBody
    public ApiResponse<List<UserDto.Response>> listUsers() {
        return ApiResponse.ok(userService.listUsers().stream()
            .map(UserDto.Response::from).toList());
    }

    @PostMapping("/api/users")
    @ResponseBody
    public ApiResponse<UserDto.Response> createUser(
            @Valid @RequestBody UserDto.CreateRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        checkManagePermission(userDetails);
        return ApiResponse.ok(UserDto.Response.from(userService.createUser(req)));
    }

    @PutMapping("/api/users/{id}")
    @ResponseBody
    public ApiResponse<UserDto.Response> updateUser(
            @PathVariable Long id,
            @RequestBody UserDto.UpdateRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        checkManagePermission(userDetails);
        return ApiResponse.ok(UserDto.Response.from(userService.updateUser(id, req)));
    }

    @DeleteMapping("/api/users/{id}")
    @ResponseBody
    public ApiResponse<Void> disableUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        checkManagePermission(userDetails);
        userService.disableUser(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/departments")
    @ResponseBody
    public ApiResponse<List<Map<String, Object>>> listDepartments() {
        List<Department> depts = departmentRepository.findAll();
        return ApiResponse.ok(depts.stream()
            .map(d -> Map.<String, Object>of("id", d.getId(), "name", d.getName()))
            .toList());
    }

    private void checkManagePermission(UserDetails userDetails) {
        User caller = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        if (caller.getRole() != User.Role.SECTION_CHIEF && caller.getRole() != User.Role.DIRECTOR) {
            throw new SecurityException("無權管理人員");
        }
    }
}
