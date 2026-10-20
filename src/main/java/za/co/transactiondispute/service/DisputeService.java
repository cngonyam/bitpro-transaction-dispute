package za.co.transactiondispute.service;

import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.dto.DisputeResponse;
import za.co.transactiondispute.dto.DisputeStatusUpdateRequest;
import za.co.transactiondispute.dto.PageResponse;
import za.co.transactiondispute.enums.DisputeStatus;
import za.co.transactiondispute.security.Actor;

public interface DisputeService {

    DisputeResponse createDispute(DisputeCreateRequest request, Actor actor);

    PageResponse<DisputeResponse> getDisputes(DisputeStatus status, String accountNumber, int page, int size, Actor actor);

    DisputeResponse getDispute(String disputeId, Actor actor);

    DisputeResponse cancelDispute(String disputeId, Actor actor);

    DisputeResponse updateStatus(String disputeId, DisputeStatusUpdateRequest request, Actor actor);
}
