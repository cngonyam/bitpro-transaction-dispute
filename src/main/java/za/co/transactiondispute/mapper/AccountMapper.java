package za.co.transactiondispute.mapper;

import org.springframework.stereotype.Component;
import za.co.transactiondispute.dto.AccountDto;
import za.co.transactiondispute.entity.Account;
import za.co.transactiondispute.enums.AccountStatus;

@Component
public class AccountMapper {

    public AccountDto toDto(Account a) {
        return AccountDto.builder()
                .accountNumber(a.getAccountNumber())
                .ownerUsername(a.getOwnerUsername())
                .customerName(a.getCustomerName())
                .email(a.getEmail())
                .mobileNumber(a.getMobileNumber())
                .currency(a.getCurrency())
                .availableBalance(a.getAvailableBalance())
                .status(a.getStatus())
                .build();
    }

    public Account toEntity(AccountDto dto) {
        return Account.builder()
                .accountNumber(dto.getAccountNumber())
                .ownerUsername(dto.getOwnerUsername())
                .customerName(dto.getCustomerName())
                .email(dto.getEmail())
                .mobileNumber(dto.getMobileNumber())
                .currency(dto.getCurrency())
                .availableBalance(dto.getAvailableBalance())
                .status(dto.getStatus() == null ? AccountStatus.ACTIVE : dto.getStatus())
                .build();
    }
}
