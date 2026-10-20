package za.co.transactiondispute.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.transactiondispute.dto.PageResponse;
import za.co.transactiondispute.dto.TransactionDto;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.integration.CoreBankingClient;
import za.co.transactiondispute.mapper.TransactionMapper;
import za.co.transactiondispute.repository.TransactionRepository;
import za.co.transactiondispute.security.Actor;
import za.co.transactiondispute.service.AccountService;
import za.co.transactiondispute.service.TransactionService;
import za.co.transactiondispute.validation.DisputeValidator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final CoreBankingClient coreBankingClient;
    private final AccountService accountService;
    private final TransactionMapper transactionMapper;

    @Override
    @Transactional
    public PageResponse<TransactionDto> getAccountTransactions(String accountNumber, LocalDate from, LocalDate to,
                                                               boolean refresh, int page, int size, Actor actor) {
        accountService.getAuthorisedAccount(accountNumber, actor);

        LocalDate toDate = to != null ? to : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(DisputeValidator.DISPUTE_WINDOW_DAYS);
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        LocalDateTime start = fromDate.atStartOfDay();
        LocalDateTime end = toDate.plusDays(1).atStartOfDay();

        if (refresh) {
            sync(accountNumber, start, end);
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "transactionDate", "id"));
        return PageResponse.from(transactionRepository.findForAccount(accountNumber, start, end, pageable)
                .map(transactionMapper::toDto));
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionDto getTransaction(String reference, Actor actor) {
        Transaction transaction = transactionRepository.findByTransactionReference(reference)
                .orElseThrow(() -> notFound(reference));
        accountService.getAuthorisedAccount(transaction.getAccountNumber(), actor);
        return transactionMapper.toDto(transaction);
    }

    @Override
    @Transactional
    public Transaction getTransactionForAccount(String reference, String accountNumber) {
        Transaction found = transactionRepository.findByTransactionReference(reference).orElse(null);
        if (found == null) {
            LocalDateTime now = LocalDateTime.now();
            sync(accountNumber, now.minusDays(DisputeValidator.DISPUTE_WINDOW_DAYS + 1), now.plusDays(1));
            found = transactionRepository.findByTransactionReference(reference).orElseThrow(() -> notFound(reference));
        }
        if (!found.getAccountNumber().equals(accountNumber)) {
            throw notFound(reference);
        }
        return found;
    }

    private void sync(String accountNumber, LocalDateTime from, LocalDateTime to) {
        List<Transaction> fresh = coreBankingClient.fetchTransactions(accountNumber, from, to).stream()
                .filter(dto -> dto.getTransactionReference() != null
                        && dto.getAmount() != null && dto.getType() != null && dto.getStatus() != null
                        && dto.getTransactionDate() != null && dto.getCurrency() != null && dto.getMerchantName() != null)
                .filter(dto -> !transactionRepository.existsByTransactionReference(dto.getTransactionReference()))
                .map(dto -> transactionMapper.toEntity(dto, accountNumber))
                .toList();
        if (!fresh.isEmpty()) {
            transactionRepository.saveAll(fresh);
            log.info("Synced {} new transactions for account ending {}", fresh.size(),
                    accountNumber.substring(Math.max(0, accountNumber.length() - 4)));
        }
    }

    private ResourceNotFoundException notFound(String reference) {
        return new ResourceNotFoundException("Transaction not found: " + reference);
    }
}
