package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * UserDetail entity - Stores user profile information
 * Related to UserAuth (userId)
 */
@Document(collection = "user_detail")
public class UserDetail {

    @Id
    private String userId = UUID.randomUUID().toString();
    private String displayName;
    private String firstName;
    private String lastName;
    private String avatarUrl = "";
    private String coverPhotoUrl = "";
    private String bio = "";
    private Date dob;
    private String gender = "";
    private String gmail = "";
    private String address = "";
    private String city = "";
    private String education = "";
    private String workplace = "";
    private Boolean isOrgActive;
    private LocalDateTime orgCode;
    private LocalDateTime lastUpdateProfile;

    public UserDetail() {}

    public UserDetail(String userId, String displayName, String firstName, String lastName, String avatarUrl, 
                     String coverPhotoUrl, String bio, Date dob, String gender, String gmail, String address, 
                     String city, String education, String workplace, Boolean isOrgActive, LocalDateTime orgCode, 
                     LocalDateTime lastUpdateProfile) {
        this.userId = userId;
        this.displayName = displayName;
        this.firstName = firstName;
        this.lastName = lastName;
        this.avatarUrl = avatarUrl;
        this.coverPhotoUrl = coverPhotoUrl;
        this.bio = bio;
        this.dob = dob;
        this.gender = gender;
        this.gmail = gmail;
        this.address = address;
        this.city = city;
        this.education = education;
        this.workplace = workplace;
        this.isOrgActive = isOrgActive;
        this.orgCode = orgCode;
        this.lastUpdateProfile = lastUpdateProfile;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getCoverPhotoUrl() { return coverPhotoUrl; }
    public void setCoverPhotoUrl(String coverPhotoUrl) { this.coverPhotoUrl = coverPhotoUrl; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public Date getDob() { return dob; }
    public void setDob(Date dob) { this.dob = dob; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getGmail() { return gmail; }
    public void setGmail(String gmail) { this.gmail = gmail; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getWorkplace() { return workplace; }
    public void setWorkplace(String workplace) { this.workplace = workplace; }
    public Boolean getIsOrgActive() { return isOrgActive; }
    public void setIsOrgActive(Boolean isOrgActive) { this.isOrgActive = isOrgActive; }
    public LocalDateTime getOrgCode() { return orgCode; }
    public void setOrgCode(LocalDateTime orgCode) { this.orgCode = orgCode; }
    public LocalDateTime getLastUpdateProfile() { return lastUpdateProfile; }
    public void setLastUpdateProfile(LocalDateTime lastUpdateProfile) { this.lastUpdateProfile = lastUpdateProfile; }

    public static UserDetailBuilder builder() {
        return new UserDetailBuilder();
    }

    public static class UserDetailBuilder {
        private String userId = UUID.randomUUID().toString();
        private String displayName;
        private String firstName;
        private String lastName;
        private String avatarUrl = "";
        private String coverPhotoUrl = "";
        private String bio = "";
        private Date dob;
        private String gender = "";
        private String gmail = "";
        private String address = "";
        private String city = "";
        private String education = "";
        private String workplace = "";
        private Boolean isOrgActive;
        private LocalDateTime orgCode;
        private LocalDateTime lastUpdateProfile;

        public UserDetailBuilder userId(String userId) { this.userId = userId; return this; }
        public UserDetailBuilder displayName(String displayName) { this.displayName = displayName; return this; }
        public UserDetailBuilder firstName(String firstName) { this.firstName = firstName; return this; }
        public UserDetailBuilder lastName(String lastName) { this.lastName = lastName; return this; }
        public UserDetailBuilder avatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; return this; }
        public UserDetailBuilder coverPhotoUrl(String coverPhotoUrl) { this.coverPhotoUrl = coverPhotoUrl; return this; }
        public UserDetailBuilder bio(String bio) { this.bio = bio; return this; }
        public UserDetailBuilder dob(Date dob) { this.dob = dob; return this; }
        public UserDetailBuilder gender(String gender) { this.gender = gender; return this; }
        public UserDetailBuilder gmail(String gmail) { this.gmail = gmail; return this; }
        public UserDetailBuilder address(String address) { this.address = address; return this; }
        public UserDetailBuilder city(String city) { this.city = city; return this; }
        public UserDetailBuilder education(String education) { this.education = education; return this; }
        public UserDetailBuilder workplace(String workplace) { this.workplace = workplace; return this; }
        public UserDetailBuilder isOrgActive(Boolean isOrgActive) { this.isOrgActive = isOrgActive; return this; }
        public UserDetailBuilder orgCode(LocalDateTime orgCode) { this.orgCode = orgCode; return this; }
        public UserDetailBuilder lastUpdateProfile(LocalDateTime lastUpdateProfile) { this.lastUpdateProfile = lastUpdateProfile; return this; }

        public UserDetail build() {
            return new UserDetail(userId, displayName, firstName, lastName, avatarUrl, coverPhotoUrl, bio, dob, gender, gmail, address, city, education, workplace, isOrgActive, orgCode, lastUpdateProfile);
        }
    }
}
