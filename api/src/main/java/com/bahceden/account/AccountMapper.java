package com.bahceden.account;

import com.bahceden.account.domain.Account;
import com.bahceden.account.dto.AccountView;
import java.util.Comparator;
import org.springframework.stereotype.Component;

/**
 * Hesap entity'sinden DTO'ya dönüşüm. Elle yazılır ({@code ProductMapper} ile
 * aynı gerekçe); entity dışarıya verilmez (AGENTS.md §11.2).
 */
@Component
public class AccountMapper {

    public AccountView toView(Account account) {
        return new AccountView(
                account.getEmail(),
                account.getRoles().stream().sorted(Comparator.comparing(Enum::name)).toList(),
                account.isEmailVerified());
    }
}
