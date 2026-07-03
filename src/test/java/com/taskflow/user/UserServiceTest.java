package com.taskflow.user;

import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository deptRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
        deptRepository.deleteAll();
        Department dept = new Department();
        dept.setName("資訊部");
        deptRepository.save(dept);
    }

    @Test
    void createUser_savesWithHashedPassword() {
        UserDto.CreateRequest req = new UserDto.CreateRequest();
        req.setEmail("user@test.com");
        req.setPassword("secret123");
        req.setDisplayName("測試使用者");
        req.setRole(User.Role.PROJECT_MEMBER);

        User created = userService.createUser(req);

        assertThat(created.getId()).isNotNull();
        assertThat(passwordEncoder.matches("secret123", created.getPasswordHash())).isTrue();
    }

    @Test
    void sectionChief_canCreateBoard() {
        UserDto.CreateRequest req = new UserDto.CreateRequest();
        req.setEmail("chief@test.com");
        req.setPassword("pass");
        req.setDisplayName("科長");
        req.setRole(User.Role.SECTION_CHIEF);
        User user = userService.createUser(req);

        assertThat(user.canCreateBoard()).isTrue();
    }

    @Test
    void projectMember_cannotCreateBoard() {
        UserDto.CreateRequest req = new UserDto.CreateRequest();
        req.setEmail("mem@test.com");
        req.setPassword("pass");
        req.setDisplayName("成員");
        req.setRole(User.Role.PROJECT_MEMBER);
        User user = userService.createUser(req);

        assertThat(user.canCreateBoard()).isFalse();
    }

    @Test
    void disableUser_setsEnabledFalse() {
        UserDto.CreateRequest req = new UserDto.CreateRequest();
        req.setEmail("old@test.com");
        req.setPassword("pass");
        req.setDisplayName("舊員工");
        req.setRole(User.Role.PROJECT_MEMBER);
        User user = userService.createUser(req);

        userService.disableUser(user.getId());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.isEnabled()).isFalse();
    }
}
