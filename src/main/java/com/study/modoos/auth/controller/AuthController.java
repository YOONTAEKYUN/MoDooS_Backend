package com.study.modoos.auth.controller;

import com.study.modoos.auth.dto.TokenDto;
import com.study.modoos.auth.reponse.LoginResponse;
import com.study.modoos.auth.request.LoginRequest;
import com.study.modoos.auth.service.AuthService;
import com.study.modoos.auth.service.EmailService;
import com.study.modoos.common.response.NormalResponse;
import com.study.modoos.member.service.MemberService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/auth")
public class AuthController {

    private final AuthService authService;
    private final MemberService memberService;
    private final long COOKIE_EXPIRATION = 7776000; // 90일
    private final EmailService emailService;


    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest loginRequest,
                                               HttpServletResponse response) {
        // User 등록 및 Refresh Token 저장
        LoginResponse loginResponse = authService.login(loginRequest);

        // Refresh Token을 HttpOnly 쿠키에 저장하여 프론트엔드로 전달
        ResponseCookie responseCookie = ResponseCookie.from("refresh-token", loginResponse.getRefreshToken())
                .maxAge(COOKIE_EXPIRATION)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .build();

        // Access Token과 TokenType을 함께 헤더에 담아서 전송
        response.setHeader(HttpHeaders.AUTHORIZATION, loginResponse.getTokenType() + " " + loginResponse.getAccessToken());

        // 쿠키와 Access Token을 함께 응답
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .build();
    }


    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestHeader("Authorization") String requestAccessToken) {
        if (!authService.validate(requestAccessToken)) {
            return ResponseEntity.status(HttpStatus.OK).build(); // 재발급 필요X
        } else {
            System.out.println("토큰 재발급 필요");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); // 재발급 필요
        }
    }

    // 토큰 재발급
    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(@CookieValue(name = "refresh-token") String requestRefreshToken,
                                     @RequestHeader("Authorization") String requestAccessToken) {
        TokenDto reissuedTokenDto = authService.reissue(requestAccessToken, requestRefreshToken);

        if (reissuedTokenDto != null) { // 토큰 재발급 성공
            // RT 저장
            ResponseCookie responseCookie = ResponseCookie.from("refresh-token", reissuedTokenDto.getRefreshToken())
                    .maxAge(COOKIE_EXPIRATION)
                    .httpOnly(true)
                    .secure(true)
                    .build();
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                    // AT 저장
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + reissuedTokenDto.getAccessToken())
                    .build();

        } else { // Refresh Token 탈취 가능성
            // Cookie 삭제 후 재로그인 유도
            ResponseCookie responseCookie = ResponseCookie.from("refresh-token", "")
                    .maxAge(0)
                    .path("/")
                    .build();
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                    .build();
        }
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String requestAccessToken) {
        authService.logout(requestAccessToken);
        ResponseCookie responseCookie = ResponseCookie.from("refresh-token", "")
                .maxAge(0)
                .path("/")
                .build();

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .build();
    }

    @PostMapping("/email-confirm")
    public String emailConfirm(@RequestParam String email) throws Exception {
        return emailService.sendSimpleMessage(email);
    }

    @PostMapping("/email-check")
    public ResponseEntity<NormalResponse> emailCheck(@RequestParam String email) {
        if (memberService.emailCheck(email))
            return ResponseEntity.ok(NormalResponse.fail());
        return ResponseEntity.ok(NormalResponse.success());
    }

    @PostMapping("/changePw")
    public ResponseEntity<NormalResponse> changePassword(@RequestBody @Valid LoginRequest request) {
        authService.changePassword(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(NormalResponse.success());
    }
}