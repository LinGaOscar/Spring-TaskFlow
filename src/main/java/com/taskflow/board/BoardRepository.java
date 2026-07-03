package com.taskflow.board;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface BoardRepository extends JpaRepository<Board, Long> {
    // SECTION_CHIEF / PROJECT_LEADER 查本科看板（依歸檔狀態過濾）
    List<Board> findByDepartmentIdAndArchived(Long departmentId, boolean archived);

    // PROJECT_MEMBER 查自己被加入或擁有的看板
    @Query("""
        SELECT DISTINCT p FROM Board p
        LEFT JOIN BoardMember m ON m.id.boardId = p.id
        WHERE (p.owner.id = :userId OR m.id.userId = :userId) AND p.archived = :archived
    """)
    List<Board> findByMemberOrOwner(@Param("userId") Long userId, @Param("archived") boolean archived);

    // DIRECTOR 查本部所有科的看板
    @Query("SELECT p FROM Board p WHERE p.department.id IN :sectionIds AND p.archived = :archived")
    List<Board> findBySectionIds(@Param("sectionIds") List<Long> sectionIds, @Param("archived") boolean archived);
}
