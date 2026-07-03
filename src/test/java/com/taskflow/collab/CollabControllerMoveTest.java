package com.taskflow.collab;

import com.taskflow.board.Board;
import com.taskflow.board.BoardService;
import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.task.Task;
import com.taskflow.task.TaskDto;
import com.taskflow.task.TaskService;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CollabControllerMoveTest {

    @Autowired CollabController collabController;
    @Autowired TaskService taskService;
    @Autowired BoardService boardService;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;
    @MockBean SimpMessagingTemplate broker;

    User chief;
    Board board;
    Task task;

    @BeforeEach
    void setUp() {
        Department div = new Department(); div.setName("資訊部"); departmentRepository.save(div);
        Department sec = new Department(); sec.setName("資訊科"); sec.setParent(div); departmentRepository.save(sec);
        chief = new User();
        chief.setEmail("chief@t.com"); chief.setPasswordHash("x");
        chief.setDisplayName("科長"); chief.setRole(User.Role.SECTION_CHIEF); chief.setDepartment(sec);
        userRepository.save(chief);
        var req = new com.taskflow.board.BoardDto.CreateRequest();
        req.setName("協作看板");
        board = boardService.createBoard(req, chief.getId());
        TaskDto.SaveRequest sr = new TaskDto.SaveRequest();
        sr.setTitle("要移動的任務");
        task = taskService.createTask(board.getId(), sr, chief);
    }

    Principal principalOf(User u) { return u::getEmail; }

    @Test
    void 移動任務_持久化並廣播TASK_MOVE() {
        TaskDto.MoveRequest mv = new TaskDto.MoveRequest();
        mv.setStatus(Task.Status.DONE);
        mv.setTargetIndex(0);

        collabController.onTaskMove(board.getId(), task.getId(), mv, principalOf(chief));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broker).convertAndSend(eq("/topic/board/" + board.getId() + "/tasks"), captor.capture());
        TaskChangeMessage msg = (TaskChangeMessage) captor.getValue();
        assertThat(msg.getType()).isEqualTo(TaskChangeMessage.Type.TASK_MOVE);
        assertThat(taskService.listByBoard(board.getId()).get(0).getStatus())
            .isEqualTo(Task.Status.DONE);
    }

    @Test
    void 無權限者移動_被拒且不廣播() {
        Department sec2 = new Department(); sec2.setName("資訊科2"); departmentRepository.save(sec2);
        User outsider = new User();
        outsider.setEmail("out@t.com"); outsider.setPasswordHash("x");
        outsider.setDisplayName("外人"); outsider.setRole(User.Role.PROJECT_MEMBER); outsider.setDepartment(sec2);
        userRepository.save(outsider);

        TaskDto.MoveRequest mv = new TaskDto.MoveRequest();
        mv.setStatus(Task.Status.DONE);
        mv.setTargetIndex(0);
        assertThatThrownBy(() ->
            collabController.onTaskMove(board.getId(), task.getId(), mv, principalOf(outsider)))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void 跨科成員join_presence_被拒() {
        // 部門隔離延伸到 presence：越權 join 不得混入他科看板的在線清單
        Department sec2 = new Department(); sec2.setName("資訊科2"); departmentRepository.save(sec2);
        User outsider = new User();
        outsider.setEmail("out2@t.com"); outsider.setPasswordHash("x");
        outsider.setDisplayName("外人2"); outsider.setRole(User.Role.PROJECT_MEMBER); outsider.setDepartment(sec2);
        userRepository.save(outsider);

        assertThatThrownBy(() ->
            collabController.handleJoin(board.getId(), principalOf(outsider)))
            .isInstanceOf(SecurityException.class);
    }
}
