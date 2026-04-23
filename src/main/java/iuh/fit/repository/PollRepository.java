package iuh.fit.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.Poll;
import java.util.List;

@Repository
public interface PollRepository extends MongoRepository<Poll, String> {
    List<Poll> findByConversationId(String conversationId);
}
