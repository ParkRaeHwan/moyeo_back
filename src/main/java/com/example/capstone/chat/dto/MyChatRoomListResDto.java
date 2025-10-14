package com.example.capstone.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MyChatRoomListResDto {
    private Long roomId;
    private String otherUserNickname;
    private String otherUserImageUrl;
    private Long unReadCount;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
}
