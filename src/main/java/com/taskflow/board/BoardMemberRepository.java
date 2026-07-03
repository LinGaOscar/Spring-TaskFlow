package com.taskflow.board;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface BoardMemberRepository extends JpaRepository<BoardMember, BoardMemberId> {
    List<BoardMember> findByIdBoardId(Long boardId);

    boolean existsByIdBoardIdAndIdUserId(Long boardId, Long userId);

    @Query("SELECT m.id.userId FROM BoardMember m WHERE m.id.boardId = :pid")
    List<Long> findUserIdsByBoardId(@Param("pid") Long boardId);
}
