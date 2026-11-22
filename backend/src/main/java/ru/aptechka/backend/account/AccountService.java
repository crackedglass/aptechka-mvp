package ru.aptechka.backend.account;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.aptechka.backend.common.Errors;
import ru.aptechka.backend.tracking.AccessAudit;

@Service
public class AccountService implements UserDetailsService {

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final AccessAudit audit;

	public AccountService(AppUserRepository users, PasswordEncoder passwordEncoder, Clock clock, AccessAudit audit) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.audit = audit;
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		return users.findByEmail(normalizeEmail(username))
				.map(CurrentUser::of)
				.orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
	}

	@Transactional
	public AppUser register(String email, String password, String displayName) {
		String normalized = normalizeEmail(email);
		if (users.existsByEmail(normalized)) {
			throw Errors.conflict("Пользователь с такой почтой уже зарегистрирован");
		}
		var now = clock.instant();
		AppUser user = users.saveAndFlush(new AppUser(normalized, passwordEncoder.encode(password), displayName.trim(), now));
		audit.record(user.getId(), now, user.getId(), "ACCOUNT_CREATED", null,
				Map.of("email", normalized, "displayName", user.getDisplayName()));
		return user;
	}

}
