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
                {"Nguyễn Ngọc Hồng Minh", "0901234564", "minh.nguyen@fruvia.com", "Nữ", "Hà Nội", "Data Scientist đam mê AI", "Đại học Công nghiệp TP.HCM", "05/12/2004", "/default/image4.jpg"},
                {"Phan Thanh Tùng", "0901234565", "tung.phan@fruvia.com", "Nam", "Hải Phòng", "Fullstack Developer", "Đại học Công nghiệp TP.HCM", "12/03/2004", "/default/image5.jpg"},
                {"Đặng Minh Quân", "0901234566", "quan.dang@fruvia.com", "Nam", "Huế", "Mobile Developer", "Đại học Công nghiệp TP.HCM", "25/06/2004", "/default/image6.jpg"},
                {"Hoàng Thị Thu Hà", "0901234567", "ha.hoang@fruvia.com", "Nữ", "Nam Định", "QA Engineer", "Đại học Công nghiệp TP.HCM", "08/09/2004", "/default/image7.jpg"},
                {"Bùi Văn Tâm", "0901234568", "tam.bui@fruvia.com", "Nam", "Thanh Hóa", "DevOps Engineer", "Đại học Công nghiệp TP.HCM", "14/11/2004", "/default/image8.jpg"},
                {"Ngô Bảo Châu", "0901234569", "chau.ngo@fruvia.com", "Nữ", "Hưng Yên", "UI/UX Designer", "Đại học Công nghiệp TP.HCM", "20/01/2005", "/default/image1.jpg"},
                {"Phạm Anh Tuấn", "0901234570", "tuan.pham@fruvia.com", "Nam", "Quảng Ninh", "Backend Developer", "Đại học Công nghiệp TP.HCM", "05/02/2004", "/default/image2.jpg"},
                {"Vũ Việt Hoàng", "0901234571", "hoang.vu@fruvia.com", "Nam", "Thái Bình", "Product Manager", "Đại học Công nghiệp TP.HCM", "10/03/2004", "/default/image3.jpg"},
                {"Đỗ Thùy Linh", "0901234572", "linh.do@fruvia.com", "Nữ", "Vĩnh Phúc", "Content Creator", "Đại học Công nghiệp TP.HCM", "15/04/2004", "/default/image4.jpg"},
                {"Dương Hoàng Anh", "0901234573", "anh.duong@fruvia.com", "Nam", "Bắc Ninh", "Security Researcher", "Đại học Công nghiệp TP.HCM", "20/05/2004", "/default/image5.jpg"},
                {"Lý Gia Hân", "0901234574", "han.ly@fruvia.com", "Nữ", "Long An", "Data Analyst", "Đại học Công nghiệp TP.HCM", "25/06/2004", "/default/image6.jpg"},
                {"Trịnh Công Sơn", "0901234575", "son.trinh@fruvia.com", "Nam", "Thừa Thiên Huế", "Software Architect", "Đại học Công nghiệp TP.HCM", "30/07/2004", "/default/image7.jpg"},
                    {"Võ Hoàng Yến", "0901234576", "yen.vo@fruvia.com", "Nữ", "Vũng Tàu", "Project Manager", "Đại học Công nghiệp TP.HCM", "05/08/2004", "/default/image8.jpg"},
                {"Mai Phương Thúy", "0901234577", "thuy.mai@fruvia.com", "Nữ", "Khánh Hòa", "Business Analyst", "Đại học Công nghiệp TP.HCM", "10/09/2004", "/default/image1.jpg"},
                {"Đinh Tiến Dũng", "0901234578", "dung.dinh@fruvia.com", "Nam", "Nghệ An", "Database Administrator", "Đại học Công nghiệp TP.HCM", "15/10/2004", "/default/image2.jpg"},
                {"Hồ Xuân Hương", "0901234579", "huong.ho@fruvia.com", "Nữ", "Quảng Bình", "System Admin", "Đại học Công nghiệp TP.HCM", "20/11/2004", "/default/image3.jpg"},
                {"Trương Vĩnh Ký", "0901234580", "ky.truong@fruvia.com", "Nam", "Bến Tre", "Network Engineer", "Đại học Công nghiệp TP.HCM", "25/12/2004", "/default/image4.jpg"}
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
