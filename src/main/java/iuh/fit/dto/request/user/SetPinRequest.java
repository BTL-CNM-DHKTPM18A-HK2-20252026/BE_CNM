package iuh.fit.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetPinRequest {

    @NotBlank(message = "Mã PIN không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã PIN phải gồm đúng 6 chữ số")
    String pin;

    /**
     * Required only when changing an existing PIN.
     * Null/blank when setting PIN for the first time.
     */
    String currentPin;
}
