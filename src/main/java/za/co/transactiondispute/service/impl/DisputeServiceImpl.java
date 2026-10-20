package za.co.transactiondispute.service.impl;

import org.springframework.stereotype.Service;
import za.co.transactiondispute.config.CustomerAccountContext;
import za.co.transactiondispute.dto.DisputeCreateRequest;
import za.co.transactiondispute.entity.Dispute;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.DisputeStatus;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.integration.EmailClient;
import za.co.transactiondispute.integration.SmsClient;
import za.co.transactiondispute.repository.DisputeRepository;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.service.DisputeService;
import za.co.transactiondispute.validation.DisputeValidator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final TransactionRepository transactionRepository;
    private final DisputeValidator disputeValidator;
    private final EmailClient emailClient;
    private final SmsClient smsClient;
    private final CustomerAccountContext customerAccountContext;

    public DisputeServiceImpl(DisputeRepository disputeRepository,
                              TransactionRepository transactionRepository,
                              DisputeValidator disputeValidator,
                              EmailClient emailClient,
                              SmsClient smsClient,
                              CustomerAccountContext customerAccountContext) {
        this.disputeRepository = disputeRepository;
        this.transactionRepository = transactionRepository;
        this.disputeValidator = disputeValidator;
        this.emailClient = emailClient;
        this.smsClient = smsClient;
        this.customerAccountContext = customerAccountContext;
    }

    @Override
    public List<Dispute> getAllDisputes() {
        return disputeRepository.findByAccountNumber(customerAccountContext.getCurrentAccountNumber());
    }

    @Override
    public Optional<Dispute> getDisputeById(Long id) {
        return disputeRepository.findById(id)
                .filter(dispute -> dispute.getAccountNumber().equalsIgnoreCase(customerAccountContext.getCurrentAccountNumber()))
                .or(() -> {
                    throw new ResourceNotFoundException("Dispute not found with id: " + id);
                });
    }

    @Override
    public Dispute createDispute(DisputeCreateRequest request) {
        disputeValidator.validateCreateRequest(request);

        String currentAccountNumber = customerAccountContext.getCurrentAccountNumber();
        if (!request.getAccountNumber().equalsIgnoreCase(currentAccountNumber)) {
            throw new IllegalArgumentException("Customers can only submit disputes for their own account.");
        }

        Transaction transaction = transactionRepository.findByTransactionReference(request.getTransactionReference())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with reference: " + request.getTransactionReference()));

        if (!transaction.getAccountNumber().equalsIgnoreCase(currentAccountNumber)) {
            throw new IllegalArgumentException("Customers can only dispute transactions on their own account.");
        }

        LocalDateTime now = LocalDateTime.now();
        Dispute dispute = Dispute.builder()
                .disputeId("DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .transactionReference(transaction.getTransactionReference())
                .accountNumber(request.getAccountNumber())
                .disputedAmount(request.getDisputedAmount())
                .reasonCode(request.getReasonCode())
                .description(request.getDescription())
                .status(DisputeStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Dispute saved = disputeRepository.save(dispute);
        emailClient.sendEmail("customer@example.com", "Dispute Created", "Dispute " + saved.getDisputeId() + " has been created.");
        smsClient.sendSms("+27123456789", "Dispute " + saved.getDisputeId() + " is under review");

        return saved;
    }
}
