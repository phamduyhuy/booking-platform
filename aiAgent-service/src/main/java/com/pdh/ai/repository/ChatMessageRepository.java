package com.pdh.ai.repository;

import com.pdh.ai.model.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByTimestampAsc(String conversationId);

    List<ChatMessage> findByConversationIdOrderByTimestampDesc(String conversationId, Pageable pageable);

    void deleteByConversationId(String conversationId);

    Optional<ChatMessage> findTopByConversationIdOrderByTimestampDesc(String conversationId);

    Optional<ChatMessage> findFirstByConversationIdAndParentMessageIsNullOrderByTimestampAsc(String conversationId);

    interface ConversationInfo {
        String getConversationId();
        String getTitle();
        Instant getCreatedAt();
        Instant getLastUpdated();
    }

    @Query("SELECT m.conversationId as conversationId, m.title as title, m.timestamp as createdAt, " +
           "(SELECT MAX(sub.timestamp) FROM ChatMessage sub WHERE sub.conversationId = m.conversationId) as lastUpdated " +
           "FROM ChatMessage m " +
           "WHERE m.parentMessage IS NULL AND m.conversationId LIKE :userIdPrefix% " +
           "ORDER BY lastUpdated DESC")
    List<ConversationInfo> findUserConversations(@Param("userIdPrefix") String userIdPrefix);
}
