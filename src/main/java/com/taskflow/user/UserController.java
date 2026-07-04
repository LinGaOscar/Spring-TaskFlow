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
    // 誤加入本科看板成員（部門隔離旁路）。部長掛在部層級、看板成員來自下屬科，
    // 故 DIRECTOR 回傳本部＋所有子科帳號（與 addMember 的部門樹父子驗證一致）
    @GetMapping("/api/users")
    @ResponseBody
    public ApiResponse<List<UserDto.Response>> listUsers(
            @AuthenticationPrincipal UserDetails userDetails) {
        User caller = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        if (caller.getDepartment() == null) {
            return ApiResponse.ok(List.of());
        }
        List<User> users;
        if (caller.getRole() == User.Role.DIRECTOR) {
            List<Long> sectionIds = departmentRepository
                .findByParentId(caller.getDepartment().getId())
                .stream().map(Department::getId).toList();
            users = userService.listUsersForDivision(caller.getDepartment().getId(), sectionIds);
        } else {
            users = userService.listUsers(caller.getDepartment().getId());
        }
        return ApiResponse.ok(users.stream().map(UserDto.Response::from).toList());
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
