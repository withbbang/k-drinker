-- 통합 로그인의 시스템 사용자 토큰은 만료되지 않으므로 만료 시각을 비울 수 있게 한다.
ALTER TABLE instagram_token MODIFY expires_at DATETIME(6) NULL;
