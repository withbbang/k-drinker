CREATE TABLE brewery_alias (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    brewery_id       BIGINT       NOT NULL,
    normalized_alias VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_brewery_alias_brewery FOREIGN KEY (brewery_id) REFERENCES brewery (id),
    CONSTRAINT uk_brewery_alias UNIQUE (brewery_id, normalized_alias)
);
CREATE INDEX idx_brewery_alias_normalized_alias ON brewery_alias (normalized_alias);
