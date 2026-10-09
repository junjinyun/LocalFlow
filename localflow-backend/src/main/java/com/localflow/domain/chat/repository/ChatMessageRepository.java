package com.localflow.domain.chat.repository;

import com.localflow.domain.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    List<ChatMessage> findAllByProject_IdOrderByCreatedAtAsc(String projectId);
    void deleteAllByProject_Id(String projectId);
}
