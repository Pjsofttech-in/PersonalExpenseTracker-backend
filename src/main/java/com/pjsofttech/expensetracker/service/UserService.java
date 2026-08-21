package com.pjsofttech.expensetracker.service;

import com.pjsofttech.expensetracker.dto.*;
import com.pjsofttech.expensetracker.model.Contact;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.repository.ContactRepository;
import com.pjsofttech.expensetracker.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ContactRepository contactRepository;
    public AuthenticateUserRes  registerUser(@Valid AuthenticateUserReq authUser) {
        User user = User.builder()
                .name(authUser.getName())
                .email(authUser.getEmail())
                .password(passwordEncoder.encode(authUser.getPassword()))
                .phoneNumber(authUser.getPhoneNumber())
                .build();
        userRepository.save(user);
        return AuthenticateUserRes.builder()
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .build();
    }

    public UserResponseDto addUser(@Valid UserRequestDto userRequestDto,User loggedInUser) {
        Contact contact = Contact.builder()
                .name(userRequestDto.getName())
                .phoneNumber(userRequestDto.getPhoneNumber())
                .email(userRequestDto.getEmail())
                .owner(loggedInUser)
                .build();
        contactRepository.save(contact);
        return UserResponseDto.builder()
                .name(contact.getName())
                .phoneNumber(contact.getPhoneNumber())
                .email(contact.getEmail())
                .build();
    }



    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(()->new UsernameNotFoundException("User Not found"));
    }

    public List<UserResponseDto> getContacts(User loggedInUser) {

        return contactRepository.findByOwner_Id(loggedInUser.getId())
                .stream()
                .map(contact -> UserResponseDto.builder()
                        .id(contact.getId())
                        .name(contact.getName())
                        .phoneNumber(contact.getPhoneNumber())
                        .email(contact.getEmail())
                        .build())
                .toList();
    }

    public ContactResponseDto updateUser(
            Long contactId,
            UserRequestDto userRequestDto,
            User loggedInUser) {

        Contact contact = contactRepository
                .findByIdAndOwner(contactId, loggedInUser)
                .orElseThrow(() -> new RuntimeException("Contact not found"));

        contact.setName(userRequestDto.getName());
        contact.setEmail(userRequestDto.getEmail());
        contact.setPhoneNumber(userRequestDto.getPhoneNumber());

        Contact updatedContact = contactRepository.save(contact);

        return ContactResponseDto.builder()
                .id(updatedContact.getId())
                .name(updatedContact.getName())
                .email(updatedContact.getEmail())
                .phoneNumber(updatedContact.getPhoneNumber())
                .build();
    }

    public String deleteUser(Long contactId, User loggedInUser) {

        Contact contact = contactRepository
                .findByIdAndOwner(contactId, loggedInUser)
                .orElseThrow(() -> new RuntimeException("Contact not found"));

        contactRepository.delete(contact);

        return "Contact deleted successfully";
    }

}
