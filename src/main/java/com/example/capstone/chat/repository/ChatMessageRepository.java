package com.example.capstone.chat.repository;

import com.example.capstone.chat.entity.ChatMessage;
import com.example.capstone.chat.entity.ChatRoom;
import com.example.capstone.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatRoomOrderByCreatedTimeAsc(ChatRoom chatRoom);
    ChatMessage findFirstByUserAndChatRoom(UserEntity otherUser, ChatRoom chatRoom);
}
