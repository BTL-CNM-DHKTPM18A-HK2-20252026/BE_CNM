package iuh.fit.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.PostMedia;

@Repository
public interface PostMediaRepository extends MongoRepository<PostMedia, String> {

    List<PostMedia> findByPostIdOrderByCreatedAtAsc(String postId);

    void deleteByPostId(String postId);
}
