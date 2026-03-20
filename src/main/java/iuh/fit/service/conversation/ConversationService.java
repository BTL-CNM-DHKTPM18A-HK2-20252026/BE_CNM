package iuh.fit.service.conversation;

import iuh.fit.dto.request.conversation.CreateConversationRequest;
import iuh.fit.dto.response.conversation.ConversationResponse;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.Conversations;
import iuh.fit.enums.ConversationType;
import iuh.fit.enums.MemberRole;
import iuh.fit.mapper.ConversationMapper;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationMapper conversationMapper;

    @Transactional
    public ConversationResponse getOrCreatePrivateConversation(String user1Id, String user2Id) {
        // Tìm xem đã có hội thoại 1-1 giữa 2 người này chưa
        List<ConversationMember> user1Convs = conversationMemberRepository.findByUserId(user1Id);
        List<ConversationMember> user2Convs = conversationMemberRepository.findByUserId(user2Id);

        // Lấy danh sách conversationId chung
        List<String> commonConvIds = user1Convs.stream()
                .map(ConversationMember::getConversationId)
                .filter(id -> user2Convs.stream().anyMatch(c2 -> c2.getConversationId().equals(id)))
                .collect(Collectors.toList());

        for (String convId : commonConvIds) {
            Optional<Conversations> conv = conversationRepository.findById(convId);
            if (conv.isPresent() && conv.get().getConversationType() == ConversationType.PRIVATE) {
                // Kiểm tra xem hội thoại này CÓ ĐÚNG CHỈ CÓ 2 NGƯỜI không
                if (conversationMemberRepository.countByConversationId(convId) == 2) {
                    List<ConversationMember> members = conversationMemberRepository.findByConversationId(convId);
                    return conversationMapper.toResponse(conv.get(), members);
                }
            }
        }

        // Nếu chưa có, tạo mới
        Conversations newConv = Conversations.builder()
                .conversationId(UUID.randomUUID().toString())
                .conversationType(ConversationType.PRIVATE)
                .createdAt(LocalDateTime.now())
                .isDeleted(false)
                .build();
        newConv = conversationRepository.save(newConv);

        // Thêm member
        ConversationMember m1 = ConversationMember.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(newConv.getConversationId())
                .userId(user1Id)
                .joinedAt(LocalDateTime.now())
                .role(MemberRole.MEMBER)
                .build();
        
        ConversationMember m2 = ConversationMember.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(newConv.getConversationId())
                .userId(user2Id)
                .joinedAt(LocalDateTime.now())
                .role(MemberRole.MEMBER)
                .build();

        conversationMemberRepository.saveAll(List.of(m1, m2));
        
        log.info("Created new private conversation between {} and {}", user1Id, user2Id);
        return conversationMapper.toResponse(newConv, List.of(m1, m2));
    }

    @Transactional
    public ConversationResponse createGroupConversation(String creatorId, CreateConversationRequest request) {
        Conversations group = Conversations.builder()
                .conversationId(UUID.randomUUID().toString())
                .conversationType(ConversationType.GROUP)
                .conversationName(request.getConversationName())
                .avatarUrl(request.getConversationAvatarUrl())
                .creatorId(creatorId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();
        group = conversationRepository.save(group);

        List<ConversationMember> members = new ArrayList<>();
        
        // Thêm người tạo làm ADMIN
        members.add(ConversationMember.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(group.getConversationId())
                .userId(creatorId)
                .role(MemberRole.ADMIN)
                .joinedAt(LocalDateTime.now())
                .build());

        // Thêm các thành viên khác
        if (request.getMemberIds() != null) {
            for (String memberId : request.getMemberIds()) {
                if (!memberId.equals(creatorId)) {
                    members.add(ConversationMember.builder()
                            .id(UUID.randomUUID().toString())
                            .conversationId(group.getConversationId())
                            .userId(memberId)
                            .role(MemberRole.MEMBER)
                            .joinedAt(LocalDateTime.now())
                            .build());
                }
            }
        }

        members = conversationMemberRepository.saveAll(members);
        log.info("Created new group conversation: {}", group.getConversationName());
        
        return conversationMapper.toResponse(group, members);
    }

    public List<ConversationResponse> getUserConversations(String userId) {
        List<ConversationMember> memberships = conversationMemberRepository.findByUserId(userId);
        return memberships.stream()
                .map(m -> {
                    Conversations conv = conversationRepository.findById(m.getConversationId()).orElse(null);
                    if (conv == null || Boolean.TRUE.equals(conv.getIsDeleted())) return null;
                    List<ConversationMember> members = conversationMemberRepository.findByConversationId(conv.getConversationId());
                    return conversationMapper.toResponse(conv, members);
                })
                .filter(resp -> resp != null)
                .collect(Collectors.toList());
    }
}
