package com.taskflow.user;

import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository deptRepository;

    Department deptA, deptB;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
        deptRepository.deleteAll();
        deptA = new Department(); deptA.setName("資訊科"); deptRepository.save(deptA);
        deptB = new Department(); deptB.setName("業務科"); deptRepository.save(deptB);
    }

    private User saveUser(String email, Department dept, boolean enabled) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("x");
        u.setDisplayName(email);
        u.setRole(User.Role.PROJECT_MEMBER);
        u.setDepartment(dept);
        u.setEnabled(enabled);
        return userRepository.save(u);
    }

    // 成員管理限科內帳號：跨科帳號不應出現在清單中，避免被誤加入本科看板成員（部門隔離旁路）
    @Test
    void listUsers_onlyReturnsSameDepartmentUsers() {
        saveUser("a@t.com", deptA, true);
        saveUser("b@t.com", deptB, true);

        var result = userService.listUsers(deptA.getId());

        assertThat(result).extracting(User::getEmail).containsExactly("a@t.com");
    }

    // 停用帳號不應出現在成員選單，避免離職人員仍可被加入看板
    @Test
    void listUsers_excludesDisabledUsers() {
        saveUser("active@t.com", deptA, true);
        saveUser("disabled@t.com", deptA, false);

        var result = userService.listUsers(deptA.getId());

        assertThat(result).extracting(User::getEmail).containsExactly("active@t.com");
    }
}
