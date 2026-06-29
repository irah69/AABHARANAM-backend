package com.murgan.ecommerce.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.murgan.ecommerce.domain.User;
import com.murgan.ecommerce.repository.UserRepository;

@Service
public class UserPrincipalService implements UserDetailsService {

	private final UserRepository userRepository;

	public UserPrincipalService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(usernameOrEmail)
			.or(() -> userRepository.findByUsername(usernameOrEmail))
			.orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
		List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
			.map(r -> new SimpleGrantedAuthority(r.getName()))
			.toList();
        System.out.println("User: " + user.getEmail());

user.getRoles().forEach(role ->
    System.out.println("Role = " + role.getName())
);
		return org.springframework.security.core.userdetails.User
			.withUsername(user.getEmail())
			.password(user.getPasswordHash())
			.disabled(!user.isEnabled())
			.authorities(authorities)
			.build();
	}
}

