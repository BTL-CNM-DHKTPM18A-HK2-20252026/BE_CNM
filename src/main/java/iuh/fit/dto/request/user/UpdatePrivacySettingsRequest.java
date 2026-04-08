package iuh.fit.dto.request.user;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdatePrivacySettingsRequest {

    @JsonProperty("show_read_receipts")
    Boolean showReadReceipts;

    @JsonProperty("show_online_status")
    Boolean showOnlineStatus;

    @JsonProperty("allow_search_by_phone")
    Boolean allowSearchByPhone;

    @JsonProperty("allow_search_by_qr")
    Boolean allowSearchByQR;

    @JsonProperty("allow_search_by_group")
    Boolean allowSearchByGroup;

    @JsonProperty("block_stranger_messages")
    Boolean blockStrangerMessages;

    @JsonProperty("block_stranger_profile_view")
    Boolean blockStrangerProfileView;
}
