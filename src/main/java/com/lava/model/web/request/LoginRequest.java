package com.lava.model.web.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email, @NotBlank String password) {

    @Override
    public String toString() {
        return String.format("LoginRequest{email='%s'}", email);
    }
}
