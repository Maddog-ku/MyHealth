package com.myhealth.chat;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserIdOrderByCreatedAtAsc(Long userId);

    /** Most recent messages first; used to feed a bounded window into the model. */
    List<ChatMessage> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Transactional
    void deleteByUserId(Long userId);
}
