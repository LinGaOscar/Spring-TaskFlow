package com.taskflow.user;

import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    // email 是帳號唯一識別碼，重複建立直接報錯，不允許同 email 多帳號
    @Transactional
    public User createUser(UserDto.CreateRequest req) {
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new IllegalArgumentException("電子郵件已存在：" + req.getEmail());
        }
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setDisplayName(req.getDisplayName());
        user.setRole(req.getRole());
        if (req.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(req.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException("部門不存在"));
            user.setDepartment(dept);
        }
        return userRepository.save(user);
    }

    // 停用帳號不在列表中，避免停用使用者出現在成員選單
    public List<User> listUsers() {
        return userRepository.findByEnabledTrue();
    }

    // 不允許修改 email（帳號識別碼）與密碼，需另開獨立入口
    @Transactional
    public User updateUser(Long id, UserDto.UpdateRequest req) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        if (req.getDisplayName() != null) user.setDisplayName(req.getDisplayName());
        if (req.getRole() != null) user.setRole(req.getRole());
        if (req.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(req.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException("部門不存在"));
            user.setDepartment(dept);
        }
        return userRepository.save(user);
    }

    // 軟刪除：停用而非實體刪除，保留 FK 關聯避免歷史資料斷鏈（如 WBS owner、操作紀錄）
    @Transactional
    public void disableUser(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        user.setEnabled(false);
        userRepository.save(user);
    }
}
