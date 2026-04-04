package iuh.fit.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import iuh.fit.entity.ImageUploadMetadata;

public interface ImageUploadMetadataRepository extends MongoRepository<ImageUploadMetadata, String> {

    Optional<ImageUploadMetadata> findByS3Key(String s3Key);
}
