package za.co.transactiondispute.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import za.co.transactiondispute.enums.DisputeStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisputeStatusUpdateRequest {

    @NotNull
    private DisputeStatus status;

    @Size(max = 1000)
    private String note;
}
