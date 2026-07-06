package com.taskflow.task;

import com.taskflow.board.Board;
import com.taskflow.board.BoardDto;
import com.taskflow.board.BoardMemberRepository;
import com.taskflow.board.BoardRepository;
import com.taskflow.board.BoardService;
import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

// 刻意「不」加類別層級 @Transactional：WebSocket 廣播路徑在無 open-in-view 的執行緒轉換 DTO，
// 若測試包在單一交易內會讓 session 一直開著、遮蔽 LazyInitializationException，反而測不到 bug。
@SpringBootTest
@ActiveProfiles("test")
class TaskSnapshotTest {

    @Autowired TaskService taskService;
    @Autowired BoardService boardService;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired BoardRepository boardRepository;
    @Autowired BoardMemberRepository boardMemberRepository;
    @Autowired TaskRepository taskRepository;

    User chief;
    Board board;

    @BeforeEach
    void setUp() {
        Department sec = new Department();
        sec.setName("資訊科");
        departmentRepository.save(sec);

        chief = new User();
        chief.setEmail("snapshot-chief@t.com");
        chief.setPasswordHash("x");
        chief.setDisplayName("資訊科長");
        chief.setRole(User.Role.SECTION_CHIEF);
        chief.setDepartment(sec);
        userRepository.save(chief);

        BoardDto.CreateRequest req = new BoardDto.CreateRequest();
        req.setName("快照測試看板");
        board = boardService.createBoard(req, chief.getId());   // 建立者自動成為 owner+成員

        // 指派負責人的任務：assignee 是 LAZY 關聯，正是觸發 bug 的條件
        TaskDto.SaveRequest task = new TaskDto.SaveRequest();
        task.setTitle("有負責人的任務");
        task.setAssigneeId(chief.getId());
        taskService.createTask(board.getId(), task, chief);
    }

    @AfterEach
    void tearDown() {
        taskRepository.deleteAll();
        boardMemberRepository.deleteAll();
        boardRepository.deleteAll();
        userRepository.deleteAll();
        departmentRepository.deleteAll();
    }

    // 回歸測試：MOVE 廣播用 listResponsesByBoard 在交易內轉換 DTO。
    // 在無環境交易（模擬 WebSocket 執行緒無 OSIV）下存取 LAZY 的 assignee 不得拋例外，
    // 且負責人姓名需正確帶出——若改回交易外轉換，本測試會以 LazyInitializationException 失敗。
    @Test
    void listResponsesByBoard_有負責人任務_無OSIV下正常轉換DTO() {
        List<TaskDto.Response> snapshot = taskService.listResponsesByBoard(board.getId());

        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.get(0).getTitle()).isEqualTo("有負責人的任務");
        assertThat(snapshot.get(0).getAssigneeName()).isEqualTo("資訊科長");
    }
}
