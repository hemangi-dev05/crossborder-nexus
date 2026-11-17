
package com.fintech.controltower.service.security;

import com.fintech.controltower.domain.entity.Customer;
import com.fintech.controltower.domain.repository.CustomerRepository;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CustomerDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    public CustomerDetailsService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        Customer customer = customerRepository.findByEmail(
                email.trim().toLowerCase(Locale.ROOT)
        ).orElseThrow(() ->
                new UsernameNotFoundException("Customer not found")
        );

        return User.builder()
                .username(customer.getEmail())
                .password(customer.getPasswordHash())
                .roles(customer.getRole().name())
                .disabled(!customer.isEnabled())
                .build();
    }
}
