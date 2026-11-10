package com.tripbler.backend.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tripbler.backend.auth.entity.RefreshToken;
import com.tripbler.backend.user.entity.User;

public interface RefreshTokenRepository
    extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(
        String token
    );

    Optional<RefreshToken> findByUser(
        User user
    );

    void deleteByUser(
        User user
    );

    /**
     * 값이 정확히 일치하는 Refresh Token 행만 하나의 DELETE 문으로 지운다.
     *
     * 조회 후 삭제가 아니라 조건부 삭제 한 번이라, 그 사이 같은 사용자가 다시 로그인해
     * 토큰 값이 바뀌었다면 아무 행도 지우지 않는다. 지운 행 수를 돌려준다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken r where r.token = :token")
    int deleteByToken(
        @Param("token") String token
    );
}