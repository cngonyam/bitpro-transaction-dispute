package za.co.transactiondispute.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import za.co.transactiondispute.dto.TransactionDto;
import za.co.transactiondispute.exception.ExternalServiceException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Reads transactions from a remote core banking API: GET {base-url}/accounts/{n}/transactions?from=&to=. */
@Component
@ConditionalOnProperty(name = "core-banking.mode", havingValue = "rest")
public class RestCoreBankingClient implements CoreBankingClient {

    private final RestClient restClient;

    public RestCoreBankingClient(RestClient.Builder builder,
                                 @Value("${core-banking.base-url}") String baseUrl,
                                 @Value("${core-banking.api-key:}") String apiKey) {
        RestClient.Builder b = builder.baseUrl(baseUrl);
        if (!apiKey.isBlank()) {
            b.defaultHeader("X-Api-Key", apiKey);
        }
        this.restClient = b.build();
    }

    @Override
    public List<TransactionDto> fetchTransactions(String accountNumber, LocalDateTime from, LocalDateTime to) {
        try {
            TransactionDto[] body = restClient.get()
                    .uri(u -> u.path("/accounts/{accountNumber}/transactions")
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .build(accountNumber))
                    .retrieve()
                    .body(TransactionDto[].class);
            return body == null ? List.of() : Arrays.asList(body);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Core banking request failed", ex);
        }
    }
}
