package iuh.fit.configuration;

import iuh.fit.entity.Conversations;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.enums.AccountStatus;
import iuh.fit.enums.ConversationType;
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

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;

@Configuration
@Slf4j
public class DataInitializer {

    private final String ddlAuto;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            @Value("${spring.data.mongodb.ddl-auto:none}") String ddlAuto,
            PasswordEncoder passwordEncoder) {
        this.ddlAuto = ddlAuto;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    CommandLineRunner initDatabase(MongoTemplate mongoTemplate) {
        return args -> {
            if ("create-drop".equalsIgnoreCase(ddlAuto)) {
                log.info(">> ddl-auto is 'create-drop'. Dropping existing database...");
                mongoTemplate.getDb().drop();
            }

            log.info("Checking and initializing database collections...");

            // 1. Tạo danh sách 4 user mặc định nếu chưa có
            String[][] defaultUsers = {
                {"Nguyễn Quang Huy", "0399614016", "huy.nguyen@fruvia.com", "Nam", "TP. Hồ Chí Minh", "Sinh viên IUH - Khoa CNTT", "Đại học Công nghiệp TP.HCM (IUH)", "20/04/2004", "/default/image1.jpg"},
                {"Lê Mẫn Nghi", "0901234562", "nghi.le@fruvia.com", "Nữ", "Đà Lạt, Lâm Đồng", "Yêu thích du lịch và lập trình", "Đại học Công nghiệp TP.HCM", "15/08/2004", "/default/image2.jpg"},
                {"Trần Hồng Nhiên", "0901234563", "nhien.tran@fruvia.com", "Nữ", "Cần Thơ", "Chuyên gia về thiết kế UI/UX", "Đại học Công nghiệp TP.HCM", "10/10/2004", "/default/image3.jpg"},
                {"Nguyễn Ngọc Hồng Minh", "0901234564", "minh.nguyen@fruvia.com", "Nữ", "Hà Nội", "Data Scientist đam mê AI", "Đại học Công nghiệp TP.HCM", "05/12/2004", "/default/image4.jpg"}
            };

            for (String[] userData : defaultUsers) {
                String fullName = userData[0];
                String phone = userData[1];
                String email = userData[2];
                String gender = userData[3];
                String city = userData[4];
                String bio = userData[5];
                String education = userData[6];
                String dobString = userData[7];
                String avatarUrl = userData[8];
                Date dob = new SimpleDateFormat("dd/MM/yyyy").parse(dobString);

                if (!mongoTemplate.exists(Query.query(
                        Criteria.where("phoneNumber").is(phone)), UserAuth.class)) {
                    
                    // Tạo UserAuth
                    UserAuth userAuth = UserAuth.builder()
                            .phoneNumber(phone)
                            .email(email)
                            .passwordHash(passwordEncoder.encode("TestUser123@")) // Sử dụng hash ở đây
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
                    log.info(">> Created default user: {} with full profile info", fullName);
                } else {
                    // Update avatar if missing for existing users
                    UserDetail existingDetail = mongoTemplate.findOne(
                            Query.query(Criteria.where("displayName").is(fullName)), 
                            UserDetail.class
                    );
                    if (existingDetail != null && (existingDetail.getAvatarUrl() == null || existingDetail.getAvatarUrl().isEmpty() || existingDetail.getAvatarUrl().contains("ui-avatars.com"))) {
                        existingDetail.setAvatarUrl(avatarUrl);
                        existingDetail.setLastUpdateProfile(LocalDateTime.now());
                        mongoTemplate.save(existingDetail);
                        log.info(">> Updated avatar for existing user: {} to local path: {}", fullName, avatarUrl);
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
