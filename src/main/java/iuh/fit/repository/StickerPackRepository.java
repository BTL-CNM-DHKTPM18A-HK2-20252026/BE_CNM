package iuh.fit.repository;

import iuh.fit.entity.StickerPack;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StickerPackRepository extends MongoRepository<StickerPack, String> {
}
