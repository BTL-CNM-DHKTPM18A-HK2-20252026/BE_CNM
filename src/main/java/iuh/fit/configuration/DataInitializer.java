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

@Configuration
@Slf4j
public class DataInitializer {

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
                        String[][] defaultUsers = {
                                        { "Nguyễn Quang Huy", "nguyenquanghuy1163@gmail.com", "Nam",
                                                        "TP. Hồ Chí Minh",
                                                        "Sinh viên IUH - Khoa CNTT", "Đại học Công nghiệp TP.HCM (IUH)",
                                                        "20/04/2004",
                                                        "/default/image1.jpg" },
                                        { "Lê Mẫn Nghi", "nghi.le@fruvia.com", "Nữ", "Đà Lạt, Lâm Đồng",
                                                        "Yêu thích du lịch và lập trình", "Đại học Công nghiệp TP.HCM",
                                                        "15/08/2004",
                                                        "/default/image2.jpg" },
                                        { "Trần Hồng Nhiên", "nhien.tran@fruvia.com", "Nữ", "Cần Thơ",
                                                        "Chuyên gia về thiết kế UI/UX", "Đại học Công nghiệp TP.HCM",
                                                        "10/10/2004",
                                                        "/default/image3.jpg" },
                                        { "Nguyễn Ngọc Hồng Minh", "minh.nguyen@fruvia.com", "Nữ", "Hà Nội",
                                                        "Data Scientist đam mê AI", "Đại học Công nghiệp TP.HCM",
                                                        "05/12/2004",
                                                        "/default/image4.jpg" },
                                        { "Phan Thanh Tùng", "tung.phan@fruvia.com", "Nam", "Hải Phòng",
                                                        "Fullstack Developer", "Đại học Công nghiệp TP.HCM",
                                                        "12/03/2004", "/default/image5.jpg" },
                                        { "Đặng Minh Quân", "quan.dang@fruvia.com", "Nam", "Huế",
                                                        "Mobile Developer",
                                                        "Đại học Công nghiệp TP.HCM", "25/06/2004",
                                                        "/default/image6.jpg" },
                                        { "Hoàng Thị Thu Hà", "ha.hoang@fruvia.com", "Nữ", "Nam Định",
                                                        "QA Engineer",
                                                        "Đại học Công nghiệp TP.HCM", "08/09/2004",
                                                        "/default/image7.jpg" },
                                        { "Bùi Văn Tâm", "tam.bui@fruvia.com", "Nam", "Thanh Hóa",
                                                        "DevOps Engineer",
                                                        "Đại học Công nghiệp TP.HCM", "14/11/2004",
                                                        "/default/image8.jpg" },
                                        { "Bảo Châu", "chau.ngo@fruvia.com", "Nữ", "Hưng Yên",
                                                        "UI/UX Designer",
                                                        "Đại học Công nghiệp TP.HCM", "20/01/2005",
                                                        "/default/image1.jpg" },
                                        { "Phạm Anh Tuấn", "tuan.pham@fruvia.com", "Nam", "Quảng Ninh",
                                                        "Backend Developer",
                                                        "Đại học Công nghiệp TP.HCM", "05/02/2004",
                                                        "/default/image2.jpg" },
                                        { "Vũ Việt Hoàng", "hoang.vu@fruvia.com", "Nam", "Thái Bình",
                                                        "Product Manager",
                                                        "Đại học Công nghiệp TP.HCM", "10/03/2004",
                                                        "/default/image3.jpg" },
                                        { "Đỗ Thùy Linh", "linh.do@fruvia.com", "Nữ", "Vĩnh Phúc",
                                                        "Content Creator",
                                                        "Đại học Công nghiệp TP.HCM", "15/04/2004",
                                                        "/default/image4.jpg" },
                                        { "Dương Hoàng Anh", "anh.duong@fruvia.com", "Nam", "Bắc Ninh",
                                                        "Security Researcher",
                                                        "Đại học Công nghiệp TP.HCM", "20/05/2004",
                                                        "/default/image5.jpg" },
                                        { "Lý Gia Hân", "han.ly@fruvia.com", "Nữ", "Long An",
                                                        "Data Analyst",
                                                        "Đại học Công nghiệp TP.HCM", "25/06/2004",
                                                        "/default/image6.jpg" },
                                        { "Trịnh Công Sơn", "son.trinh@fruvia.com", "Nam", "Thừa Thiên Huế",
                                                        "Software Architect", "Đại học Công nghiệp TP.HCM",
                                                        "30/07/2004", "/default/image7.jpg" },
                                        { "Võ Hoàng Yến", "yen.vo@fruvia.com", "Nữ", "Vũng Tàu",
                                                        "Project Manager",
                                                        "Đại học Công nghiệp TP.HCM", "05/08/2004",
                                                        "/default/image8.jpg" },
                                        { "Mai Phương Thúy", "thuy.mai@fruvia.com", "Nữ", "Khánh Hòa",
                                                        "Business Analyst",
                                                        "Đại học Công nghiệp TP.HCM", "10/09/2004",
                                                        "/default/image1.jpg" },
                                        { "Đinh Tiến Dũng", "dung.dinh@fruvia.com", "Nam", "Nghệ An",
                                                        "Database Administrator", "Đại học Công nghiệp TP.HCM",
                                                        "15/10/2004",
                                                        "/default/image2.jpg" },
                                        { "Hồ Xuân Hương", "huong.ho@fruvia.com", "Nữ", "Quảng Bình",
                                                        "System Admin",
                                                        "Đại học Công nghiệp TP.HCM", "20/11/2004",
                                                        "/default/image3.jpg" },
                                        { "Trương Vĩnh Ký", "ky.truong@fruvia.com", "Nam", "Bến Tre",
                                                        "Network Engineer",
                                                        "Đại học Công nghiệp TP.HCM", "25/12/2004",
                                                        "/default/image4.jpg" }
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
                                Date dob = new SimpleDateFormat("dd/MM/yyyy").parse(dobString);

                                if (!mongoTemplate.exists(Query.query(
                                                Criteria.where("email").is(email)), UserAuth.class)) {

                                        // Tạo UserAuth
                                        UserAuth userAuth = UserAuth.builder()
                                                        .email(email)
                                                        .passwordHash(passwordEncoder.encode("TestUser123@")) // Sử dụng
                                                                                                              // hash ở
                                                                                                              // đây
                                                        .accountStatus(AccountStatus.ACTIVE)
                                                        .createdAt(LocalDateTime.now())
                                                        .isDeleted(false)
                                                        .build();

                                        UserAuth savedAuth = mongoTemplate.save(userAuth);

                                        // Tạo UserDetail với thông tin đầy đủ
                                        UserDetail userDetail = UserDetail.builder()
                                                        .userId(savedAuth.getUserId())
                                                        .displayName(fullName)
                                                        .firstName(fullName.substring(fullName.lastIndexOf(" ") + 1))
                                                        .lastName(fullName.substring(0, fullName.lastIndexOf(" ")))
                                                        .gender(gender)
                                                        .city(city)
                                                        .address(city) // Tạm lấy thành phố làm địa chỉ
                                                        .bio(bio)
                                                        .dob(dob)
                                                        .education(education)
                                                        .workplace("IUH - Industrial University of Ho Chi Minh City")
                                                        .avatarUrl(avatarUrl)
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
                                                        .build();

                                        userSettingRepository.save(userSetting);

                                        // Tạo Cloud của tôi (Self-chat)
                                        conversationService.getOrCreateSelfConversation(savedAuth.getUserId());

                                        log.info(">> Created default user: {} with full profile info and My Documents",
                                                        fullName);
                                } else {
                                        // Cập nhật existing user
                                        UserAuth existingAuth = mongoTemplate.findOne(
                                                        Query.query(Criteria.where("email").is(email)),
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
