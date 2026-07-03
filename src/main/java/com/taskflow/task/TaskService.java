package com.taskflow.task;

import com.taskflow.board.Board;
import com.taskflow.board.BoardService;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final BoardService boardService;
    private final UserRepository userRepository;

    // 所有寫入操作的統一安全閘：沿用看板權限（歸檔、跨科、非成員均被拒）
    private void checkWrite(Long boardId, User user) {
        if (!boardService.canWriteBoard(boardId, user)) {
            throw new SecurityException("無編輯此看板的權限");
        }
    }

    @Transactional(readOnly = true)
    public List<Task> listByBoard(Long boardId) {
        return taskRepository.findByBoardIdOrderByStatusAscSortOrderAsc(boardId);
    }

    @Transactional
    public Task createTask(Long boardId, TaskDto.SaveRequest req, User caller) {
        checkWrite(boardId, caller);
        Board board = boardService.getById(boardId);
        Task t = new Task();
        t.setBoard(board);
        applyFields(t, req, boardId);
        // 新任務固定進 TODO 欄尾端，符合「先建立再排程」的看板習慣
        t.setStatus(Task.Status.TODO);
        t.setSortOrder(taskRepository
            .findByBoardIdAndStatusOrderBySortOrderAsc(boardId, Task.Status.TODO).size());
        return taskRepository.save(t);
    }

    @Transactional
    public Task updateTask(Long boardId, Long taskId, TaskDto.SaveRequest req, User caller) {
        checkWrite(boardId, caller);
        Task t = getInBoard(boardId, taskId);
        applyFields(t, req, boardId);
        return taskRepository.save(t);
    }

    // 拖曳移動：目標欄整欄重排 sortOrder，確保順序連續無空洞
    @Transactional
    public Task moveTask(Long boardId, Long taskId, TaskDto.MoveRequest req, User caller) {
        checkWrite(boardId, caller);
        Task moving = getInBoard(boardId, taskId);
        Task.Status oldStatus = moving.getStatus();

        List<Task> target = new ArrayList<>(taskRepository
            .findByBoardIdAndStatusOrderBySortOrderAsc(boardId, req.getStatus()));
        target.removeIf(t -> t.getId().equals(taskId));
        int idx = Math.max(0, Math.min(req.getTargetIndex(), target.size()));
        moving.setStatus(req.getStatus());
        target.add(idx, moving);
        for (int i = 0; i < target.size(); i++) target.get(i).setSortOrder(i);
        taskRepository.saveAll(target);

        // 來源欄也重排，避免長期累積出重複的 sortOrder
        if (oldStatus != req.getStatus()) {
            List<Task> source = taskRepository
                .findByBoardIdAndStatusOrderBySortOrderAsc(boardId, oldStatus);
            for (int i = 0; i < source.size(); i++) source.get(i).setSortOrder(i);
            taskRepository.saveAll(source);
        }
        return moving;
    }

    @Transactional
    public void deleteTask(Long boardId, Long taskId, User caller) {
        checkWrite(boardId, caller);
        taskRepository.delete(getInBoard(boardId, taskId));
    }

    // 任務必須屬於指定看板，防止以其他看板 ID 繞過權限檢查（IDOR）
    private Task getInBoard(Long boardId, Long taskId) {
        Task t = taskRepository.findById(taskId)
            .orElseThrow(() -> new EntityNotFoundException("任務不存在"));
        if (!t.getBoard().getId().equals(boardId)) {
            throw new EntityNotFoundException("任務不存在");
        }
        return t;
    }

    private void applyFields(Task t, TaskDto.SaveRequest req, Long boardId) {
        t.setTitle(req.getTitle());
        t.setDescription(req.getDescription());
        t.setDueDate(req.getDueDate());
        t.setPriority(req.getPriority() != null ? req.getPriority() : Task.Priority.MEDIUM);
        if (req.getAssigneeId() != null) {
            // 負責人必須是看板成員或看板負責人，避免指派給無權限者造成幽靈任務
            User assignee = userRepository.findById(req.getAssigneeId())
                .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
            Board board = boardService.getById(boardId);
            boolean isOwner = board.getOwner() != null
                && board.getOwner().getId().equals(assignee.getId());
            if (!isOwner && !boardService.isMember(boardId, assignee.getId())) {
                throw new IllegalArgumentException("負責人必須是看板成員");
            }
            t.setAssignee(assignee);
        } else {
            t.setAssignee(null);
        }
    }
}
