-- 웹 검색으로 채운 연락처 칸 이름(쉼표 구분: address,phone,homepage,instagram)과 그 출처 URL. 텔레그램 개요의 항목별 출처 링크에 쓴다.
ALTER TABLE brewery ADD COLUMN web_fields VARCHAR(255) NULL;
ALTER TABLE brewery ADD COLUMN web_source_url VARCHAR(1000) NULL;
