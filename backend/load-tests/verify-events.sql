-- Local test DB only. Replace values with the SERVICE_ID and RUN_ID used by k6.
-- Run section 1 BEFORE k6, section 2 AFTER k6 in the SAME MySQL session.
-- Autocommit avoids an old repeatable-read snapshot. No rows are modified.
-- Group token supports vus_10/50/100/200/500/1000 and baseline/surge/peak/decline/recovery.
-- Each process uses a fresh RUN_ID; compare each result file separately.
-- Section 1
SET autocommit = 1;
SET @service_id = 'load-test-issue18';
SET @run_id = 'REPLACE-WITH-K6-RUN-UUID';
SET @prefix = CONCAT('k6-', @run_id, '-');
SELECT COUNT(*) INTO @before_total FROM behavior_event;
SELECT COUNT(*) INTO @before_service FROM behavior_event WHERE service_id = @service_id;
SELECT COUNT(*) INTO @before_run FROM behavior_event
WHERE service_id = @service_id AND LEFT(external_event_id, CHAR_LENGTH(@prefix)) = @prefix;
SELECT @before_total AS before_total, @before_service AS before_service, @before_run AS before_run;
-- before_run must be zero for a fresh UUID.

-- Section 2 (run only after k6 has finished)
SELECT COUNT(*) INTO @after_total FROM behavior_event;
SELECT COUNT(*) INTO @after_service FROM behavior_event WHERE service_id = @service_id;
SELECT COUNT(*) INTO @after_run FROM behavior_event
WHERE service_id = @service_id AND LEFT(external_event_id, CHAR_LENGTH(@prefix)) = @prefix;
SELECT @before_total AS before_total, @after_total AS after_total,
       @after_total - @before_total AS total_added,
       @before_service AS before_service, @after_service AS after_service,
       @after_service - @before_service AS service_added,
       @after_run - @before_run AS run_added;

SELECT SUBSTRING_INDEX(SUBSTRING(external_event_id, CHAR_LENGTH(@prefix) + 1), '-', 1) AS test_group,
       COUNT(*) AS stored_events
FROM behavior_event
WHERE service_id = @service_id AND LEFT(external_event_id, CHAR_LENGTH(@prefix)) = @prefix
GROUP BY test_group;

-- Expected: zero rows. Checks the composite key across the entire table.
SELECT service_id, external_event_id, COUNT(*) AS duplicate_count
FROM behavior_event
GROUP BY service_id, external_event_id
HAVING COUNT(*) > 1;

-- Can also be executed in a new session after setting @service_id and @run_id again.
SELECT COUNT(*) AS run_events, COUNT(DISTINCT external_event_id) AS distinct_run_events
FROM behavior_event
WHERE service_id = @service_id AND LEFT(external_event_id, CHAR_LENGTH(@prefix)) = @prefix;
