package ru.aptechka.backend.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import ru.aptechka.backend.account.AccountService;
import ru.aptechka.backend.account.CurrentUser;

final class GuestAuthenticationFilter extends OncePerRequestFilter {

	private final AccountService accounts;

	GuestAuthenticationFilter(AccountService accounts) {
		this.accounts = accounts;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			CurrentUser guest = accounts.guest();
			var authentication = new UsernamePasswordAuthenticationToken(guest, null, guest.getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		filterChain.doFilter(request, response);
	}
}
