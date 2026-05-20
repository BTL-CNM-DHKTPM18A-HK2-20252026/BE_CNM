package iuh.fit.dto.request.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecordClickRequest(
        @NotBlank(message = "targetId must not be blank") @Size(max = 100, message = "targetId must not exceed 100 characters") String targetId,

        @NotBlank(message = "name must not be blank") @Size(max = 200, message = "name must not exceed 200 characters") String name,

        @Size(max = 500, message = "avatar URL must not exceed 500 characters") String avatar,

        @NotBlank(message = "type must not be blank") @Pattern(regexp = "USER|GROUP", message = "type must be USER or GROUP") String type) {
}
