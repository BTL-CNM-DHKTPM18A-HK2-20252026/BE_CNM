package iuh.fit.entity;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.PrivacyLevel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * UserSetting entity - Stores user preferences and settings
 * Related to UserAuth (userId)
 */
@Document(collection = "user_setting")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSetting {
    
    @Id
    @Builder.Default
    String userId = UUID.randomUUID().toString(); // Same as UserAuth.userId
    
    Boolean allowFriendRequests;
    PrivacyLevel whoCanSeeProfile;
    PrivacyLevel whoCanSeePost;
    PrivacyLevel whoCanTagMe;
    PrivacyLevel whoCanSendMessages;
    Boolean showOnlineStatus;
    Boolean showReadReceipts;
}
