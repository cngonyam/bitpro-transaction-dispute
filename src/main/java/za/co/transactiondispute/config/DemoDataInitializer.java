package za.co.transactiondispute.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.transactiondispute.dto.AccountDto;
import za.co.transactiondispute.mapper.AccountMapper;
import za.co.transactiondispute.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    public DemoDataInitializer(AccountRepository accountRepository, AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<AccountDto> demo = List.of(
                AccountDto.builder().accountNumber("1052348976").ownerUsername("thabo").customerName("Thabo Mokoena")
                        .email("thabo@example.com").mobileNumber("+27821234567").currency("ZAR")
                        .availableBalance(new BigDecimal("15250.75")).build(),
                AccountDto.builder().accountNumber("1052348977").ownerUsername("lerato").customerName("Lerato Dlamini")
                        .email("lerato@example.com").mobileNumber("+27831234567").currency("ZAR")
                        .availableBalance(new BigDecimal("4800.00")).build());
        demo.stream()
                .filter(a -> !accountRepository.existsByAccountNumber(a.getAccountNumber()))
                .forEach(a -> accountRepository.save(accountMapper.toEntity(a)));
    }
}
