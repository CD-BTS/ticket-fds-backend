-- Apply once to an existing BTS database, after reviewing SHOW CREATE TABLE concert (if present).
-- No existing tables or data are modified. Do not rerun the complete schema.sql on an existing DB.
CREATE TABLE `concert` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `service_id` varchar(50) COLLATE utf8mb4_bin NOT NULL,
  `concert_id` varchar(50) COLLATE utf8mb4_bin NOT NULL,
  `name` varchar(200) NOT NULL,
  `starts_at` datetime NOT NULL COMMENT 'UTC',
  `ends_at` datetime NOT NULL COMMENT 'UTC',
  `venue` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX `concert_service_concert` ON `concert` (`service_id`, `concert_id`);
