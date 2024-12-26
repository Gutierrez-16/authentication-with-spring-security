package shujaa.authentication_with_spring.security.service.impl;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import shujaa.authentication_with_spring.security.controller.advice.AuthException;
import shujaa.authentication_with_spring.security.controller.response.LoginResponse;
import shujaa.authentication_with_spring.security.dto.LoginUserDto;
import shujaa.authentication_with_spring.security.dto.RegisterUserDto;
import shujaa.authentication_with_spring.security.entity.Role;
import shujaa.authentication_with_spring.security.entity.User;
import shujaa.authentication_with_spring.security.entity.UserRole;
import shujaa.authentication_with_spring.security.repository.IRoleRepository;
import shujaa.authentication_with_spring.security.repository.IUserRepository;
import shujaa.authentication_with_spring.security.security.jwt.CustomUserDetails;
import shujaa.authentication_with_spring.security.security.jwt.JwtService;
import shujaa.authentication_with_spring.security.security.jwt.TokenBlacklistService;
import shujaa.authentication_with_spring.security.service.IAuthentication;
import jakarta.mail.MessagingException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import shujaa.authentication_with_spring.security.utils.IEmailService;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class AuthenticationServiceImpl implements IAuthentication {

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final IUserRepository iUserRepository;
    private final IRoleRepository iRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final IEmailService emailService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthenticationServiceImpl(JwtService jwtService, AuthenticationManager authenticationManager, IUserRepository iUserRepository, IRoleRepository iRoleRepository, PasswordEncoder passwordEncoder, IEmailService emailService, TokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.iUserRepository = iUserRepository;
        this.iRoleRepository = iRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public LoginResponse login(LoginUserDto loginUserDto) {
        validateLoginCredentials(loginUserDto);

        Authentication authentication = authenticateUser(loginUserDto);
        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
        verifyAccountStatus(customUserDetails);

        String token = jwtService.generateToken(customUserDetails);
        return new LoginResponse(token, jwtService.getExpirationTime());
    }

    @Override
    public void logout(String token) {
        tokenBlacklistService.blacklistToken(token);
        SecurityContextHolder.clearContext();
    }

    @Override
    public User signup(RegisterUserDto registerUserDto) {
        checkIfUserExists(registerUserDto);

        User newUser = createNewUser(registerUserDto);
        String verificationCode = generateVerificationCode();
        newUser.setVerificationCode(verificationCode);
        newUser.setVerificationCodeExpiresAt(LocalDateTime.now().plusHours(1));

        iUserRepository.save(newUser);
        sendVerificationEmail(newUser);

        return newUser;
    }

    @Override
    public void verifyUser(String email, String verificationCode) {
        User user = getUserByEmailOrThrow(email);
        validateVerificationCode(user, verificationCode);
        enableUserAccount(user);
    }

    @Override
    public void resendVerificationCode(String email) {
        User user = getUserByEmailOrThrow(email);
        if (user.isEnabled()) {
            throw new RuntimeException("Account is already verified");
        }
        String verificationCode = generateVerificationCode();
        user.setVerificationCode(verificationCode);
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusHours(1));
        sendVerificationEmail(user);
        iUserRepository.save(user);
    }

    @Override
    public void requestPasswordReset(String email) {
        User user = getUserByEmailOrThrow(email);
        String verificationCode = generateVerificationCode();
        user.setVerificationCode(verificationCode);
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
        sendVerificationEmail(user);
        iUserRepository.save(user);
    }

    @Override
    public void resetPassword(String email, String verificationCode, String newPassword) {
        User user = getUserByEmailOrThrow(email);
        validateVerificationCode(user, verificationCode);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        iUserRepository.save(user);
    }

    @Override
    public String handleOAuthLogin(OAuth2AuthenticationToken authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("Authentication token or principal is null");
        }

        OAuth2User oAuth2User = authentication.getPrincipal();
        String provider = authentication.getAuthorizedClientRegistrationId();
        String email = oAuth2User.getAttribute("email");
        String name = getOAuthProviderName(oAuth2User, provider);

        User user = iUserRepository.findByEmail(email).orElseGet(() -> createOAuthUser(name, email));
        CustomUserDetails userDetails = new CustomUserDetails(user);
        return jwtService.generateToken(userDetails);
    }

    private void validateLoginCredentials(LoginUserDto loginUserDto) {
        if (loginUserDto == null || loginUserDto.getIdentifier() == null || loginUserDto.getPassword() == null) {
            throw new AuthException.UserNotFoundException("Username and password must not be null");
        }
    }

    private Authentication authenticateUser(LoginUserDto loginUserDto) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginUserDto.getIdentifier(), loginUserDto.getPassword())
            );
        } catch (Exception e) {
            throw new AuthException.UserNotFoundException("Invalid username or password");
        }
    }

    private void verifyAccountStatus(CustomUserDetails customUserDetails) {
        if (!customUserDetails.getUser().isEnabled()) {
            throw new AuthException.AccountNotVerifiedException("Account not verified. Please verify your account.");
        }
    }

    private void checkIfUserExists(RegisterUserDto registerUserDto) {
        if (iUserRepository.findByEmail(registerUserDto.getEmail()).isPresent()) {
            throw new AuthException.UserAlreadyExistsException("Email already registered");
        }

        if (iUserRepository.findByUsername(registerUserDto.getUsername()).isPresent()) {
            throw new AuthException.UsernameAlreadyExistsException("Username already taken");
        }
    }

    private User createNewUser(RegisterUserDto registerUserDto) {
        User newUser = new User();
        newUser.setEmail(registerUserDto.getEmail());
        newUser.setUsername(registerUserDto.getUsername());
        newUser.setPassword(passwordEncoder.encode(registerUserDto.getPassword()));
        newUser.setEnabled(false);

        Role role = iRoleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("User role not found"));

        UserRole userRole = new UserRole();
        userRole.setRole(role);
        userRole.setUser(newUser);
        userRole.setStatus(true);

        newUser.getUserRoles().add(userRole);
        return newUser;
    }

    private void sendVerificationEmail(User user) {
        String subject = "Please verify your email";
        String htmlMessage = "<html><body>Please use the following code to verify your email: " + user.getVerificationCode() + "</body></html>";
        try {
            emailService.sendVerificationEmail(user.getEmail(), subject, htmlMessage);
        } catch (MessagingException e) {
            throw new RuntimeException("Error sending verification email.");
        }
    }

    private String generateVerificationCode() {
        Random random = new Random();
        return String.valueOf(random.nextInt(900000) + 100000);
    }

    private User getUserByEmailOrThrow(String email) {
        return iUserRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void validateVerificationCode(User user, String verificationCode) {
        if (user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Verification code has expired");
        }
        if (!user.getVerificationCode().equals(verificationCode)) {
            throw new RuntimeException("Invalid verification code");
        }
    }

    private void enableUserAccount(User user) {
        user.setEnabled(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        iUserRepository.save(user);
    }

    private String getOAuthProviderName(OAuth2User oAuth2User, String provider) {
        String fullName;
        if ("google".equals(provider)) {
            fullName = oAuth2User.getAttribute("name");
        } else if ("github".equals(provider)) {
            fullName = oAuth2User.getAttribute("login");
        } else {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }

        if (fullName != null && fullName.contains(" ")) {
            return fullName.split(" ")[0];
        }
        return fullName;
    }


    private User createOAuthUser(String name, String email) {
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setUsername(name);
        newUser.setPassword(null);
        newUser.setEnabled(true);

        Role role = iRoleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("User role not found"));

        UserRole userRole = new UserRole();
        userRole.setRole(role);
        userRole.setUser(newUser);
        userRole.setStatus(true);

        newUser.getUserRoles().add(userRole);
        return iUserRepository.save(newUser);
    }
}
