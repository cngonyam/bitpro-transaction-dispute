package za.co.transactiondispute.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class ApiError {
    private String message;
    private String path;
    private int status;
    private LocalDateTime timestamp;
    private List<String> errors;
}
