package za.co.transactiondispute.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.dto.DisputeResponse;
import za.co.transactiondispute.dto.DisputeStatusUpdateRequest;
import za.co.transactiondispute.dto.PageResponse;
import za.co.transactiondispute.enums.DisputeReason;
import za.co.transactiondispute.enums.DisputeStatus;
import za.co.transactiondispute.security.Actor;
import za.co.transactiondispute.service.DisputeService;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/disputes")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @PostMapping
    public ResponseEntity<DisputeResponse> createDispute(@Valid @RequestBody DisputeCreateRequest request,
                                                         Authentication authentication) {
        DisputeResponse created = disputeService.createDispute(request, Actor.from(authentication));
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.getDisputeId()).toUri())
                .body(created);
    }

    @GetMapping
    public PageResponse<DisputeResponse> getDisputes(@RequestParam(required = false) DisputeStatus status,
                                                     @RequestParam(required = false) String accountNumber,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     Authentication authentication) {
        return disputeService.getDisputes(status, accountNumber, page, size, Actor.from(authentication));
    }

    @GetMapping("/reason-codes")
    public Map<DisputeReason, String> getReasonCodes() {
        return Arrays.stream(DisputeReason.values())
                .collect(Collectors.toMap(Function.identity(), DisputeReason::getLabel, (a, b) -> a,
                        () -> new java.util.EnumMap<>(DisputeReason.class)));
    }

    @GetMapping("/{disputeId}")
    public DisputeResponse getDispute(@PathVariable String disputeId, Authentication authentication) {
        return disputeService.getDispute(disputeId, Actor.from(authentication));
    }

    @PostMapping("/{disputeId}/cancel")
    public DisputeResponse cancelDispute(@PathVariable String disputeId, Authentication authentication) {
        return disputeService.cancelDispute(disputeId, Actor.from(authentication));
    }

    @PatchMapping("/{disputeId}/status")
    public DisputeResponse updateStatus(@PathVariable String disputeId,
                                        @Valid @RequestBody DisputeStatusUpdateRequest request,
                                        Authentication authentication) {
        return disputeService.updateStatus(disputeId, request, Actor.from(authentication));
    }
}
