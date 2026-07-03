package com.taskflow.task;

import com.taskflow.board.BoardService;
import com.taskflow.common.ApiResponse;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/boards/{boardId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final BoardService boardService;
    private final UserRepository userRepository;

    // 初始載入用；後續增刪改走 WebSocket（見 collab 模組）確保多人同步
    // 回傳統一 ApiResponse 信封（{success,message,data}）：前端全站只判斷 success 旗標，
    // 與 BoardController/UserController 及 GlobalExceptionHandler 的錯誤格式保持一致
    @GetMapping
    public ApiResponse<List<TaskDto.Response>> list(@PathVariable Long boardId, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        if (!boardService.canReadBoard(boardId, user)) {
            throw new SecurityException("無檢視此看板的權限");
        }
        return ApiResponse.ok(
            taskService.listByBoard(boardId).stream().map(TaskDto.Response::from).toList());
    }
}
