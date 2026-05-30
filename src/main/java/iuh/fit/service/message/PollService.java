package iuh.fit.service.message;

import iuh.fit.entity.Poll;
import iuh.fit.repository.PollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollService {

    private final PollRepository pollRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    @Transactional
    public Poll vote(String userId, String pollId, List<String> optionIds) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bình chọn"));

        // Validate deadline
        if (poll.getDeadline() != null && poll.getDeadline().isBefore(java.time.LocalDateTime.now())) {
            throw new RuntimeException("Bình chọn đã hết hạn");
        }

        // Update votes
        for (Poll.PollOption option : poll.getOptions()) {
            List<String> voters = option.getVoterIds();
            if (voters == null) {
                voters = new ArrayList<>();
            }
            
            if (optionIds.contains(option.getOptionId())) {
                // User voted for this option
                if (!voters.contains(userId)) {
                    voters.add(userId);
                }
            } else {
                // User did not vote for this option
                voters.remove(userId);
            }
            option.setVoterIds(voters);
        }

        Poll savedPoll = pollRepository.save(poll);
        broadcastPollUpdate(savedPoll);
        return savedPoll;
    }

    @Transactional
    public Poll addOption(String userId, String pollId, String content) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bình chọn"));

        if (Boolean.FALSE.equals(poll.getAllowAddOptions())) {
            throw new RuntimeException("Không được phép thêm phương án mới");
        }

        // Validate deadline
        if (poll.getDeadline() != null && poll.getDeadline().isBefore(java.time.LocalDateTime.now())) {
            throw new RuntimeException("Bình chọn đã hết hạn");
        }

        // Check if option already exists
        boolean exists = poll.getOptions().stream()
                .anyMatch(opt -> opt.getContent().trim().equalsIgnoreCase(content.trim()));
        if (exists) {
            throw new RuntimeException("Lựa chọn này đã tồn tại");
        }

        if (poll.getOptions() == null) {
            poll.setOptions(new ArrayList<>());
        }

        Poll.PollOption newOption = Poll.PollOption.builder()
                .optionId(UUID.randomUUID().toString())
                .content(content.trim())
                .voterIds(new ArrayList<>())
                .build();

        poll.getOptions().add(newOption);
        Poll savedPoll = pollRepository.save(poll);
        broadcastPollUpdate(savedPoll);
        return savedPoll;
    }

    private void broadcastPollUpdate(Poll poll) {
        Map<String, Object> updateEvent = new HashMap<>();
        updateEvent.put("type", "POLL_VOTE_UPDATE");
        updateEvent.put("pollId", poll.getPollId());
        updateEvent.put("conversationId", poll.getConversationId());
        updateEvent.put("poll", poll);

        messagingTemplate.convertAndSend("/topic/chat/" + poll.getConversationId(), updateEvent);
        log.info("Broadcasted poll update event for pollId: {} to conversationId: {}", poll.getPollId(), poll.getConversationId());
    }
}
