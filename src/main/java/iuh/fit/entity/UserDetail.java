package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * UserDetail entity - Stores user profile information
 * Related to UserAuth (userId)
 */
@Document(collection = "user_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDetail {
    
    @Id
    @Builder.Default
    String userId = UUID.randomUUID().toString(); // Same as UserAuth.userId
    String displayName;
    String firstName;
    String lastName;
    String avatarUrl;
    String coverPhotoUrl;
    String bio;
    Date dob;
    String gender;
    String address;
    String city;
    String education;
    String workplace;
    Boolean isOrgActive;
    LocalDateTime orgCode;
    LocalDateTime lastUpdateProfile;
}
