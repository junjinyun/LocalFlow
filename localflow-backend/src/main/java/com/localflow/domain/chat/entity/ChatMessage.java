package com.localflow.domain.chat.entity;

import com.localflow.domain.chat.domain.ChatMessageType;
import com.localflow.domain.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatMessageType type;

    @Column(nullable = false, length = 20000)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    public static ChatMessage create(Project project, ChatMessageType type, String content) {
        ChatMessage message = new ChatMessage();
        message.id = UUID.randomUUID().toString();
        message.project = project;
        message.type = type;
        message.content = content;
        message.createdAt = Instant.now();
        return message;
    }

    public String getId() { return id; }
    public Project getProject() { return project; }
    public ChatMessageType getType() { return type; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
