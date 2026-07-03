package com.taskflow.task;

import com.taskflow.board.Board;
import com.taskflow.board.BoardService;
import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskServiceTest {

    @Autowired TaskService taskService;
    @Autowired BoardService boardService;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;

    User chief;
    User member;
    User outsider;   // 他科成員，無看板權限
    Board board;

    @BeforeEach
    void setUp() {
        Department div = new Department();
        div.setName("資訊部");
        departmentRepository.save(div);
        Department sec = new Department();
        sec.setName("資訊科");
        sec.setParent(div);
        departmentRepository.save(sec);
        Department sec2 = new Department();
        sec2.setName("資訊科2");
        sec2.setParent(div);
        departmentRepository.save(sec2);

        chief = newUser("chief@t.com", User.Role.SECTION_CHIEF, sec);
        member = newUser("member@t.com", User.Role.PROJECT_MEMBER, sec);
        outsider = newUser("out@t.com", User.Role.PROJECT_MEMBER, sec2);

        var req = new com.taskflow.board.BoardDto.CreateRequest();
        req.setName("測試看板");
        board = boardService.createBoard(req, chief.getId());
        boardService.addMember(board.getId(), member.getId(), chief.getId());
    }

    User newUser(String email, User.Role role, Department dept) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("x");
        u.setDisplayName(email);
        u.setRole(role);
        u.setDepartment(dept);
        return userRepository.save(u);
    }

    TaskDto.SaveRequest save(String title) {
        TaskDto.SaveRequest r = new TaskDto.SaveRequest();
        r.setTitle(title);
        return r;
    }

    @Test
    void 建立任務_預設進入TODO欄尾端() {
        Task t1 = taskService.createTask(board.getId(), save("任務一"), member);
        Task t2 = taskService.createTask(board.getId(), save("任務二"), member);
        assertThat(t1.getStatus()).isEqualTo(Task.Status.TODO);
        assertThat(t2.getSortOrder()).isGreaterThan(t1.getSortOrder());
    }

    @Test
    void 無權限者建立任務_被拒() {
        assertThatThrownBy(() -> taskService.createTask(board.getId(), save("偷渡"), outsider))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void 跨欄移動_改變狀態並插入指定位置() {
        Task a = taskService.createTask(board.getId(), save("A"), member);
        Task b = taskService.createTask(board.getId(), save("B"), member);
        taskService.createTask(board.getId(), save("C"), member);

        TaskDto.MoveRequest mv = new TaskDto.MoveRequest();
        mv.setStatus(Task.Status.IN_PROGRESS);
        mv.setTargetIndex(0);
        taskService.moveTask(board.getId(), a.getId(), mv, member);

        List<Task> inProgress = taskService.listByBoard(board.getId()).stream()
            .filter(t -> t.getStatus() == Task.Status.IN_PROGRESS).toList();
        assertThat(inProgress).extracting(Task::getTitle).containsExactly("A");

        // 欄內移動：B 移到 TODO 欄第 0 位（原順序 B,C → 移後仍 B,C；改移 C 到 0 → C,B）
        List<Task> todo = taskService.listByBoard(board.getId()).stream()
            .filter(t -> t.getStatus() == Task.Status.TODO).toList();
        TaskDto.MoveRequest mv2 = new TaskDto.MoveRequest();
        mv2.setStatus(Task.Status.TODO);
        mv2.setTargetIndex(0);
        taskService.moveTask(board.getId(), todo.get(1).getId(), mv2, member);
        List<Task> after = taskService.listByBoard(board.getId()).stream()
            .filter(t -> t.getStatus() == Task.Status.TODO).toList();
        assertThat(after).extracting(Task::getTitle).containsExactly("C", "B");
    }

    @Test
    void 更新任務_可指派看板成員為負責人() {
        Task t = taskService.createTask(board.getId(), save("指派"), chief);
        TaskDto.SaveRequest req = save("指派");
        req.setAssigneeId(member.getId());
        Task updated = taskService.updateTask(board.getId(), t.getId(), req, chief);
        assertThat(updated.getAssignee().getId()).isEqualTo(member.getId());
    }

    @Test
    void 指派非看板成員_被拒() {
        Task t = taskService.createTask(board.getId(), save("指派"), chief);
        TaskDto.SaveRequest req = save("指派");
        req.setAssigneeId(outsider.getId());
        assertThatThrownBy(() -> taskService.updateTask(board.getId(), t.getId(), req, chief))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 刪除任務() {
        Task t = taskService.createTask(board.getId(), save("刪我"), member);
        taskService.deleteTask(board.getId(), t.getId(), member);
        assertThat(taskService.listByBoard(board.getId())).isEmpty();
    }

    @Test
    void 歸檔看板_所有寫入被拒() {
        Task t = taskService.createTask(board.getId(), save("先建"), chief);
        boardService.archiveBoard(board.getId(), chief);
        assertThatThrownBy(() -> taskService.createTask(board.getId(), save("不行"), chief))
            .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> taskService.deleteTask(board.getId(), t.getId(), chief))
            .isInstanceOf(SecurityException.class);
    }
}
