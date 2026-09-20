package com.sakhtyar.identity.application;

import com.sakhtyar.identity.domain.Permission;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    public DatabaseUserDetailsService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        UserEntity user = repository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found."
                ));

        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_" + user.getRole().name());

        for (Permission permission : user.getRole().permissions()) {
            authorities.add(permission.name());
        }

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities.toArray(String[]::new))
                .disabled(!user.isEnabledForLogin())
                .build();
    }
}
