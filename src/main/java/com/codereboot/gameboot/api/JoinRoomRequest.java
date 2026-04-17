package com.codereboot.gameboot.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinRoomRequest(
	@NotBlank
	@Pattern(regexp = "^[A-Za-z0-9]{5}$")
	String roomCode
) {
}