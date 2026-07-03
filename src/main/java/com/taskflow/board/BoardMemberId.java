package com.taskflow.board;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Embeddable
@Getter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class BoardMemberId implements Serializable {
    // 明確指定欄位名稱，避免 Hibernate 與 @JoinColumn 產生重複 mapping 衝突
    @Column(name = "board_id")
    private Long boardId;

    @Column(name = "user_id")
    private Long userId;
}
