
---

# Authentication with Spring Security

This project demonstrates how to implement an authentication system using **Spring Security** with **JWT** token-based authentication, **OAuth2** authentication via Google and GitHub, and email functionalities using **Spring Boot**. It integrates with a **MySQL** database for user management and allows users to register, log in, verify their email, and reset their password.

---

## Project Overview

This application provides a secure authentication system with support for **username/password login**, **email verification**, **password reset**, **JWT-based authentication**, and **OAuth2 login**. The system supports **Google** and **GitHub** OAuth2 integrations and uses **MySQL** as the backend database.

Key features include:
- **User Registration** with email verification.
- **OAuth2 Authentication** with Google and GitHub login.
- **Password Reset** functionality.
- **JWT Authentication** for stateless login.
- **Email Services** to handle password reset and account verification.

---

## Tech Stack

- **Java 17**
- **Spring Boot 3.x**
    - **Spring Security** for managing user authentication and authorization.
    - **Spring Data JPA** for database interactions.
    - **Spring Boot Starter Mail** for sending emails (password reset and verification).
    - **Spring Boot Starter OAuth2 Client** for OAuth2-based login.
    - **JWT (JSON Web Token)** for token-based authentication.
- **MySQL** for the database.
- **Lombok** for reducing boilerplate code.
- **JUnit** for unit and integration testing.

---

## Features

- **User Registration**: Users can register with an email, username, and password.
- **Email Verification**: A verification code is sent to the user's email to confirm their account.
- **Password Reset**: Users can request a password reset email and reset their password using the code.
- **JWT Authentication**: Secure API endpoints using JWT tokens.
- **OAuth2 Login**: Supports login via **Google** and **GitHub**.
- **Logout**: Users can logout, invalidating the session and JWT token.

---

## Prerequisites

Ensure the following are installed and set up:

- **JDK 17** for compiling and running the project.
- **Maven** for dependency management and building the project.
- **MySQL** or a compatible database for storing user data.
- **Google OAuth2 credentials** and **GitHub OAuth2 credentials** for integrating social login.
- **Gmail SMTP credentials** for sending verification and reset emails.

---

## Setup and Installation

1. **Clone the repository**:
   ```
   git clone https://github.com/Gutierrez-16/authentication-with-spring-security.git
   cd authentication-with-spring-security
   ```

2. **Set up the database**:
    - Create a MySQL database named `auth_db` or any other name you prefer.
    - Update the database connection properties in `application.yml` (see the [Configuration](#configuration) section below).

3. **Configure the application properties**:
   Open `src/main/resources/application.yml` and update the following sections:

   ```yaml
   spring:
     jackson:
       time-zone: America/Lima
     datasource:
       url: ${DB_BASEDEDATOS}
       username: ${DB_USERNAME}
       password: ${DB_PASSWORD}
       driver-class-name: com.mysql.cj.jdbc.Driver
     jpa:
       hibernate:
         ddl-auto: update
       show-sql: true
     mail:
       host: smtp.gmail.com
       port: 587
       username: ${MAIL_USERNAME}
       password: ${MAIL_PASSWORD}
       properties:
         mail.smtp.auth: true
         mail.smtp.starttls.enable: true

   security:
     jwt:
       secret-key: ${JWT_SECRET}
       expiration-time: ${JWT_TIME}
     oauth2:
       client:
         registration:
           google:
             client-id: ${GOOGLE_CLIENT_ID}
             client-secret: ${GOOGLE_CLIENT_SECRET}
             scope: profile, email, openid
             client-name: Google
           github:
             client-id: ${GITHUB_CLIENT_ID}
             client-secret: ${GITHUB_CLIENT_SECRET}
             scope: read:user, user:email
             client-name: GitHub
       provider:
         google:
           issuer-uri: https://accounts.google.com
           jwk-set-uri: https://www.googleapis.com/oauth2/v3/certs
           authorization-uri: https://accounts.google.com/o/oauth2/auth
           token-uri: https://oauth2.googleapis.com/token
           user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo
           user-name-attribute: sub
         github:
           authorization-uri: https://github.com/login/oauth/authorize
           token-uri: https://github.com/login/oauth/access_token
           user-info-uri: https://api.github.com/user
           user-name-attribute: login
     redirect-uri-base: ${REDIRECT_URI_BASE}

   app:
     redirect:
       uri:
         app: ${URI_APP}
   ```

   Replace placeholders like `${DB_BASEDEDATOS}`, `${MAIL_USERNAME}`, `${JWT_SECRET}`, etc., with your actual values.

---

## Configuration

- **Database Configuration**: Make sure to set up a MySQL database for user data and update the `spring.datasource.url`, `spring.datasource.username`, and `spring.datasource.password` properties in the `application.yml` file.

- **Email Configuration**: The application uses **Gmail SMTP** for sending emails. You will need to set up a **Gmail account** and use it for the `MAIL_USERNAME` and `MAIL_PASSWORD`.

- **JWT Configuration**: Set the `JWT_SECRET` and `JWT_TIME` properties to secure your JWT token and define its expiration time.

- **OAuth2 Credentials**: You'll need to set up OAuth2 credentials for **Google** and **GitHub** by registering your application in their respective developer consoles and adding the **client ID** and **client secret** in the configuration.

---

## API Endpoints

### Authentication Endpoints

- **POST /api/v1/auth/login**
    - **Request Body**: `{ "username": "user", "password": "password123" }`
    - **Response**: JWT token upon successful authentication.

- **POST /api/v1/auth/register**
    - **Request Body**: `{ "email": "user@example.com", "username": "user", "password": "password123" }`
    - **Response**: A success message and instructions for email verification.

- **POST /api/v1/auth/verify**
    - **Request Body**: `{ "email": "user@example.com", "verificationCode": "123456" }`
    - **Response**: Success message indicating the account was verified.

- **POST /api/v1/auth/reset-password-request**
    - **Request Body**: `{ "email": "user@example.com" }`
    - **Response**: Success message indicating a reset email was sent.

- **POST /api/v1/auth/reset-password**
    - **Request Body**: `{ "email": "user@example.com", "verificationCode": "123456", "newPassword": "newpassword123" }`
    - **Response**: Success message confirming the password has been reset.

- **POST /api/v1/auth/logout**
    - **Request Header**: `Authorization: Bearer <token>`
    - **Response**: A success message confirming the user has logged out.

- **GET /api/v1/auth/oauth-login**
    - **Description**: OAuth2 login via Google or GitHub.
    - **Response**: Redirects to your application with a JWT token after successful login.

---

## Contributing

If you'd like to contribute, please follow these steps:

1. Fork the repository.
2. Create a new branch: `git checkout -b feature-name`.
3. Commit your changes: `git commit -am 'Add new feature'`.
4. Push to the branch: `git push origin feature-name`.
5. Submit a pull request.

---
