package iuh.fit.dto.request.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class HideConversationRequest {

    @NotBlank(message = "Mã PIN không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã PIN phải gồm đúng 6 chữ số")
    String pinCode;
}
