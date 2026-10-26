package za.co.transactiondispute.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private Long id;
    private String username;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String role;
    private String email;
    private String phone;
    private String department;
    private String region;
}
