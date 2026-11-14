CREATE TABLE brewery (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    address        VARCHAR(255) NULL,
    phone          VARCHAR(255) NULL,
    homepage       VARCHAR(255) NULL,
    instagram_handle VARCHAR(255) NULL,
    other_sns      VARCHAR(255) NULL,
    source         VARCHAR(255) NOT NULL,
    source_url     VARCHAR(255) NULL,
    verified       BOOLEAN      NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_brewery_normalized_name ON brewery (normalized_name);

CREATE TABLE drink (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    aliases         VARCHAR(255) NULL,
    category        VARCHAR(255) NULL,
    abv             DECIMAL(4,1) NULL,
    volume_ml       INT          NULL,
    brewery_id      BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_drink_brewery FOREIGN KEY (brewery_id) REFERENCES brewery (id)
);
CREATE INDEX idx_drink_normalized_name ON drink (normalized_name);

CREATE TABLE post (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    drink_id            BIGINT        NULL,
    rating              DECIMAL(2,1)  NULL,
    memo                VARCHAR(2000) NULL,
    taste_notes         VARCHAR(255)  NULL,
    pairing             VARCHAR(255)  NULL,
    caption             VARCHAR(4000) NULL,
    caption_edited      BOOLEAN       NOT NULL,
    hashtags            VARCHAR(255)  NULL,
    status              VARCHAR(255)  NOT NULL,
    failed_stage        VARCHAR(255)  NULL,
    failure_reason      VARCHAR(1000) NULL,
    version             INT           NOT NULL,
    source              VARCHAR(255)  NOT NULL,
    telegram_message_id BIGINT        NULL,
    ig_media_id         VARCHAR(255)  NULL,
    permalink           VARCHAR(255)  NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    published_at        DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_post_drink FOREIGN KEY (drink_id) REFERENCES drink (id)
);
CREATE INDEX idx_post_status ON post (status);

CREATE TABLE post_photo (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    post_id       BIGINT       NOT NULL,
    position      INT          NOT NULL,
    original_path VARCHAR(255) NOT NULL,
    rendered_path VARCHAR(255) NULL,
    public_token  VARCHAR(43)  NOT NULL,
    focus_x       DOUBLE       NULL,
    focus_y       DOUBLE       NULL,
    focus_w       DOUBLE       NULL,
    focus_h       DOUBLE       NULL,
    fit_mode      VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_post_photo_public_token UNIQUE (public_token),
    CONSTRAINT fk_post_photo_post FOREIGN KEY (post_id) REFERENCES post (id)
);
CREATE INDEX idx_post_photo_post_position ON post_photo (post_id, position);

CREATE TABLE instagram_token (
    id            INT          NOT NULL,
    access_token  VARCHAR(255) NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    refreshed_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);
