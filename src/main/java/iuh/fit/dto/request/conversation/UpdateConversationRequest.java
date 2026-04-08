package iuh.fit.dto.request.conversation;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConversationRequest {

    @Size(max = 100, message = "Tên nhóm không được vượt quá 100 ký tự")
    private String conversationName;

    private String conversationAvatarUrl;

    @Size(max = 500, message = "Mô tả nhóm không được vượt quá 500 ký tự")
    private String groupDescription;
}
