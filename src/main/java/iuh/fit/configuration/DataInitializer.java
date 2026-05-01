package iuh.fit.configuration;

import iuh.fit.entity.Conversations;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.entity.UserSetting;
import iuh.fit.enums.AccountStatus;
import iuh.fit.enums.ConversationType;
import iuh.fit.enums.PrivacyLevel;
import iuh.fit.repository.UserSettingRepository;
import iuh.fit.service.conversation.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.bson.Document;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@Slf4j
public class DataInitializer {

        private static final String S3_PUBLIC_BASE = "https://fruvia-chat-storage.s3.ap-southeast-1.amazonaws.com/public";

        private final String ddlAuto;
        private final PasswordEncoder passwordEncoder;
        private final ConversationService conversationService;
        private final UserSettingRepository userSettingRepository;

        public DataInitializer(
                        @Value("${spring.data.mongodb.ddl-auto:none}") String ddlAuto,
                        PasswordEncoder passwordEncoder,
                        ConversationService conversationService,
                        UserSettingRepository userSettingRepository) {
                this.ddlAuto = ddlAuto;
                this.passwordEncoder = passwordEncoder;
                this.conversationService = conversationService;
                this.userSettingRepository = userSettingRepository;
        }

        @Bean
        CommandLineRunner initDatabase(MongoTemplate mongoTemplate) {
                return args -> {
                        if ("create-drop".equalsIgnoreCase(ddlAuto)) {
                                log.info(">> ddl-auto is 'create-drop'. Dropping existing database...");
                                mongoTemplate.getDb().drop();
                        }

                        log.info("Checking and initializing database collections...");

                        // Fix: Drop the broken unique index on participants (no partial filter) and
                        // recreate with proper partial filter
                        // Drop the broken unique index on participants (it indexes individual array
                        // elements,
                        // not the array as a whole, so it prevents a user from being in multiple
                        // PRIVATE conversations)
                        try {
                                mongoTemplate.getCollection("conversations").dropIndex("p2p_unique_participants");
                                log.info(">> Dropped broken unique index 'p2p_unique_participants'");
                        } catch (Exception e) {
                                // Index doesn't exist, that's fine
                        }
                        // Create a non-unique compound index for query performance on
                        // findPrivateConversation
                        try {
                                Document keys = new Document("conversationType", 1).append("participants", 1);
                                com.mongodb.client.model.IndexOptions indexOptions = new com.mongodb.client.model.IndexOptions()
                                                .name("idx_conversation_type_participants");
                                mongoTemplate.getCollection("conversations").createIndex(keys, indexOptions);
                                log.info(">> Created compound index 'idx_conversation_type_participants' for conversation queries");
                        } catch (Exception e) {
                                log.warn(">> Index 'idx_conversation_type_participants' already exists or creation failed: {}",
                                                e.getMessage());
                        }

                        // 1. Tạo danh sách 4 user mặc định nếu chưa có
                        // { name, email, gender, city, bio, education, dob, avatar, phoneNumber }
                        String[][] defaultUsers = {
                                        { "Nguyễn Quang Huy", "huy@gmail.com", "Nam",
                                                        "TP. Hồ Chí Minh",
                                                        "Sinh viên IUH - Khoa CNTT", "Đại học Công nghiệp TP.HCM (IUH)",
                                                        "20/04/2004",
                                                        "/avatar/image1.jpg", "0399614015" },
                                        { "Lê Mẫn Nghi", "nghi.le@gmail.com", "Nữ", "Đà Lạt, Lâm Đồng",
                                                        "Yêu thích du lịch và lập trình", "Đại học Công nghiệp TP.HCM",
                                                        "15/08/2004",
                                                        "/avatar/image2.jpg", "                " },
                                        { "Trần Hồng Nhiên", "nhien.tran@gmail.com", "Nữ", "Cần Thơ",
                                                        "Chuyên gia về thiết kế UI/UX", "Đại học Công nghiệp TP.HCM",
                                                        "10/10/2004",
                                                        "/avatar/image3.jpg", "0901000003" },
                                        { "Nguyễn Ngọc Hồng Minh", "minh.nguyen@gmail.com", "Nữ", "Hà Nội",
                                                        "Data Scientist đam mê AI", "Đại học Công nghiệp TP.HCM",
                                                        "05/12/2004",
                                                        "/avatar/image4.jpg", "0901000004" },
                                        { "Phan Thanh Tùng", "tung.phan@gmail.com", "Nam", "Hải Phòng",
                                                        "Fullstack Developer", "Đại học Công nghiệp TP.HCM",
                                                        "12/03/2004", "/avatar/image5.jpg", "0901000005" },
                                        { "Đặng Minh Quân", "quan.dang@gmail.com", "Nam", "Huế",
                                                        "Mobile Developer",
                                                        "Đại học Công nghiệp TP.HCM", "25/06/2004",
                                                        "/avatar/image6.jpg", "0901000006" },
                                        { "Hoàng Thị Thu Hà", "ha.hoang@gmail.com", "Nữ", "Nam Định",
                                                        "QA Engineer",
                                                        "Đại học Công nghiệp TP.HCM", "08/09/2004",
                                                        "/avatar/image7.jpg", "0901000007" },
                                        { "Bùi Văn Tâm", "tam.bui@gmail.com", "Nam", "Thanh Hóa",
                                                        "DevOps Engineer",
                                                        "Đại học Công nghiệp TP.HCM", "14/11/2004",
                                                        "/avatar/image8.jpg", "0901000008" },
                                        { "Bảo Châu", "chau.ngo@gmail.com", "Nữ", "Hưng Yên",
                                                        "UI/UX Designer",
                                                        "Đại học Công nghiệp TP.HCM", "20/01/2005",
                                                        "/avatar/image1.jpg", "0901000009" },
                                        { "Phạm Anh Tuấn", "tuan.pham@gmail.com", "Nam", "Quảng Ninh",
                                                        "Backend Developer",
                                                        "Đại học Công nghiệp TP.HCM", "05/02/2004",
                                                        "/avatar/image2.jpg", "0901000010" },
                                        { "Vũ Việt Hoàng", "hoang.vu@gmail.com", "Nam", "Thái Bình",
                                                        "Product Manager",
                                                        "Đại học Công nghiệp TP.HCM", "10/03/2004",
                                                        "/avatar/image3.jpg", "0901000011" },
                                        { "Đỗ Thùy Linh", "linh.do@gmail.com", "Nữ", "Vĩnh Phúc",
                                                        "Content Creator",
                                                        "Đại học Công nghiệp TP.HCM", "15/04/2004",
                                                        "/avatar/image4.jpg", "0901000012" },
                                        { "Dương Hoàng Anh", "anh.duong@gmail.com", "Nam", "Bắc Ninh",
                                                        "Security Researcher",
                                                        "Đại học Công nghiệp TP.HCM", "20/05/2004",
                                                        "/avatar/image5.jpg", "0901000013" },
                                        { "Lý Gia Hân", "han.ly@gmail.com", "Nữ", "Long An",
                                                        "Data Analyst",
                                                        "Đại học Công nghiệp TP.HCM", "25/06/2004",
                                                        "/avatar/image6.jpg", "0901000014" },
                                        { "Trịnh Công Sơn", "son.trinh@gmail.com", "Nam", "Thừa Thiên Huế",
                                                        "Software Architect", "Đại học Công nghiệp TP.HCM",
                                                        "30/07/2004", "/avatar/image7.jpg", "0901000015" },
                                        { "Võ Hoàng Yến", "yen.vo@gmail.com", "Nữ", "Vũng Tàu",
                                                        "Project Manager",
                                                        "Đại học Công nghiệp TP.HCM", "05/08/2004",
                                                        "/avatar/image8.jpg", "0901000016" },
                                        { "Mai Phương Thúy", "thuy.mai@gmail.com", "Nữ", "Khánh Hòa",
                                                        "Business Analyst",
                                                        "Đại học Công nghiệp TP.HCM", "10/09/2004",
                                                        "/avatar/image1.jpg", "0901000017" },
                                        { "Đinh Tiến Dũng", "dung.dinh@gmail.com", "Nam", "Nghệ An",
                                                        "Database Administrator", "Đại học Công nghiệp TP.HCM",
                                                        "15/10/2004",
                                                        "/avatar/image2.jpg", "0901000018" },
                                        { "Hồ Xuân Hương", "huong.ho@gmail.com", "Nữ", "Quảng Bình",
                                                        "System Admin",
                                                        "Đại học Công nghiệp TP.HCM", "20/11/2004",
                                                        "/avatar/image3.jpg", "0901000019" },
                                        { "Trương Vĩnh Ký", "ky.truong@gmail.com", "Nam", "Bến Tre",
                                                        "Network Engineer",
                                                        "Đại học Công nghiệp TP.HCM", "25/12/2004",
                                                        "/avatar/image4.jpg", "0901000020" }
                        };

                        for (String[] userData : defaultUsers) {
                                String fullName = userData[0];
                                String email = userData[1];
                                String gender = userData[2];
                                String city = userData[3];
                                String bio = userData[4];
                                String education = userData[5];
                                String dobString = userData[6];
                                String avatarUrl = userData[7];
                                String phoneNumber = userData[8];
                                Date dob = new SimpleDateFormat("dd/MM/yyyy").parse(dobString);
                                if (!mongoTemplate.exists(Query.query(
                                                Criteria.where("phoneNumber").is(phoneNumber)), UserAuth.class)) {

                                        // Tạo UserAuth
                                        UserAuth userAuth = UserAuth.builder()
                                                        .phoneNumber(phoneNumber)
                                                        .passwordHash(passwordEncoder.encode("TestUser123@")) // Sử dụng
                                                                                                              // hash ở
                                                                                                              // đây
                                                        .accountStatus(AccountStatus.ACTIVE)
                                                        .createdAt(LocalDateTime.now())
                                                        .isDeleted(false)
                                                        .isVerified(true)
                                                        .build();

                                        UserAuth savedAuth = mongoTemplate.save(userAuth);

                                        // Tạo UserDetail với thông tin đầy đủ
                                        UserDetail userDetail = UserDetail.builder()
                                                        .userId(savedAuth.getUserId())
                                                        .displayName(fullName)
                                                        .firstName(fullName.substring(fullName.lastIndexOf(" ") + 1))
                                                        .lastName(fullName.substring(0, fullName.lastIndexOf(" ")))
                                                        .gmail(email)
                                                        .gender(gender)
                                                        .city(city)
                                                        .address(city) // Tạm lấy thành phố làm địa chỉ
                                                        .bio(bio)
                                                        .dob(dob)
                                                        .education(education)
                                                        .workplace("IUH - Industrial University of Ho Chi Minh City")
                                                        .avatarUrl(S3_PUBLIC_BASE + avatarUrl)
                                                        .coverPhotoUrl(S3_PUBLIC_BASE + "/background_profile/image" + ((fullName.chars().sum() % 12) + 1) + ".jpg")
                                                        .lastUpdateProfile(LocalDateTime.now())
                                                        .build();

                                        mongoTemplate.save(userDetail);

                                        // Create UserSetting with default values
                                        UserSetting userSetting = UserSetting.builder()
                                                        .userId(savedAuth.getUserId())
                                                        .allowFriendRequests(true)
                                                        .whoCanSeeProfile(PrivacyLevel.PUBLIC)
                                                        .whoCanSeePost(PrivacyLevel.FRIEND_ONLY)
                                                        .whoCanTagMe(PrivacyLevel.FRIEND_ONLY)
                                                        .whoCanSendMessages(PrivacyLevel.PUBLIC)
                                                        .showOnlineStatus(true)
                                                        .showReadReceipts(true)
                                                        .allowSearchByPhone(true)
                                                        .allowSearchByQR(true)
                                                        .allowSearchByGroup(true)
                                                        .blockStrangerMessages(false)
                                                        .blockStrangerProfileView(false)
                                                        .build();

                                        userSettingRepository.save(userSetting);

                                        // Tạo Cloud của tôi (Self-chat)
                                        conversationService.getOrCreateSelfConversation(savedAuth.getUserId());

                                        log.info(">> Created default user: {} with full profile info and My Documents",
                                                        fullName);
                                } else {
                                        // Cập nhật existing user
                                        UserAuth existingAuth = mongoTemplate.findOne(
                                                        Query.query(Criteria.where("phoneNumber").is(phoneNumber)),
                                                        UserAuth.class);
                                        if (existingAuth != null) {
                                                conversationService
                                                                .getOrCreateSelfConversation(existingAuth.getUserId());
                                        }

                                        // Update avatar if missing for existing users
                                        UserDetail existingDetail = mongoTemplate.findOne(
                                                        Query.query(Criteria.where("displayName").is(fullName)),
                                                        UserDetail.class);
                                        if (existingDetail != null
                                                        && (existingDetail.getAvatarUrl() == null
                                                                        || existingDetail.getAvatarUrl().isEmpty()
                                                                        || existingDetail.getAvatarUrl()
                                                                                        .contains("ui-avatars.com"))) {
                                                existingDetail.setAvatarUrl(avatarUrl);
                                                existingDetail.setLastUpdateProfile(LocalDateTime.now());
                                                mongoTemplate.save(existingDetail);
                                                log.info(">> Updated avatar for existing user: {} to local path: {}",
                                                                fullName, avatarUrl);
                                        }
                                }
                        }

                        // 2. Khởi tạo Collection conversations nếu chưa có
                        if (!mongoTemplate.collectionExists("conversations")) {
                                Conversations welcomeGroup = Conversations.builder()
                                                .conversationName("Cộng đồng Fruvia Chat")
                                                .conversationType(ConversationType.GROUP)
                                                .groupDescription("Chào mừng bạn đến với Fruvia Chat!")
                                                .createdAt(LocalDateTime.now())
                                                .isDeleted(false)
                                                .isPinned(true)
                                                .build();
                                mongoTemplate.save(welcomeGroup);
                                log.info(">> Collection 'conversations' created with a sample group.");
                        }

                        log.info("Database initialization completed.");
                };
        }
}
