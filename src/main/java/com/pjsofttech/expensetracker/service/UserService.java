package com.pjsofttech.expensetracker.service;

import com.pjsofttech.expensetracker.custom_exceptions.DuplicateEmailException;
import com.pjsofttech.expensetracker.dto.*;
import com.pjsofttech.expensetracker.model.Contact;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.repository.ContactRepository;
import com.pjsofttech.expensetracker.repository.ExpenseRepository;
import com.pjsofttech.expensetracker.repository.UserRepository;
import jakarta.transaction.Transactional;
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
    @Autowired
    private ExpenseRepository expenseRepository;



    //Helper Methods
    private Contact toUser(UserRequestDto userRequestDto,User loggedInUser){
        return Contact.builder()
                .name(userRequestDto.getName())
                .email(userRequestDto.getEmail())
                .phoneNumber(userRequestDto.getPhoneNumber())
                .owner(loggedInUser)
                .active(true)
                .build();
    }


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
        Contact contact = toUser(userRequestDto,loggedInUser);
        if(contactRepository.existsByEmailAndOwner(contact.getEmail(),loggedInUser)){
            throw new DuplicateEmailException("User With this email Already Exists!");
        }

        Contact savedContact = contactRepository.save(contact);
        return UserResponseDto.builder()
                .id(savedContact.getId())
                .name(savedContact.getName())
                .phoneNumber(savedContact.getPhoneNumber())
                .email(savedContact.getEmail())
                .build();
    }



    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(()->new UsernameNotFoundException("User Not found"));
    }

    public List<UserResponseDto> getContacts(User loggedInUser) {

        return contactRepository.findByOwner_IdAndActiveTrue(loggedInUser.getId())
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


    @Transactional
    public String deleteUser(Long contactId, User loggedInUser) {
        Contact contact = contactRepository
                .findByIdAndOwner(contactId, loggedInUser)
                .orElseThrow(() -> new RuntimeException("Contact not found"));
//        expenseRepository.deleteByContact_Id(contactId); All Expenses to particular user should not be deleted
//        contactRepository.delete(contact);
        contact.setActive(false);
        contactRepository.save(contact);

        return "Contact deleted successfully";
    }

}
