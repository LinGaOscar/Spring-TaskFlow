package com.taskflow.user;

import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;

    @Test
    void findByEmail_returnsUser() {
        Department dept = new Department();
        dept.setName("資訊部");
        departmentRepository.save(dept);

        User user = new User();
        user.setEmail("admin@example.com");
        user.setPasswordHash("hashed");
        user.setDisplayName("Admin");
        user.setDepartment(dept);
        user.setRole(User.Role.SECTION_CHIEF);
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("admin@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Admin");
    }
}
