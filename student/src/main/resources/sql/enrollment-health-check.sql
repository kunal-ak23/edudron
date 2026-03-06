-- ============================================================================
-- ENROLLMENT HEALTH CHECK QUERIES
-- Run these against production to assess broken enrollment data before repair.
-- All queries are read-only SELECTs — safe to run anytime.
-- ============================================================================


-- ─────────────────────────────────────────────────────────────────────────────
-- QUERY 1: Enrollments with WRONG classId
-- The enrollment says classId=X, but the section it belongs to is actually
-- under classId=Y. This happens when old transfer code moved the sectionId
-- but didn't update classId properly, or only moved one enrollment.
-- ─────────────────────────────────────────────────────────────────────────────
SELECT
    e.id AS enrollment_id,
    e.student_id,
    e.course_id,
    e.batch_id AS section_id,
    e.class_id AS enrollment_class_id,
    s.class_id AS actual_class_id,
    e.institute_id AS enrollment_institute_id,
    c.institute_id AS actual_institute_id
FROM student.enrollments e
JOIN student.sections s ON e.batch_id = s.id AND e.client_id = s.client_id
JOIN student.classes c ON s.class_id = c.id AND s.client_id = c.client_id
WHERE e.course_id != '__PLACEHOLDER_ASSOCIATION__'
  AND (e.class_id != s.class_id OR e.institute_id != c.institute_id)
ORDER BY e.student_id, e.batch_id;

-- Expected: 0 rows. Any rows here = stale data from broken transfers.


-- ─────────────────────────────────────────────────────────────────────────────
-- QUERY 2: Students with enrollments SPLIT across multiple sections
-- A student should typically only be in ONE section. If they were partially
-- transferred (old bug: only 1 of 3 enrollments moved), they'll show up here
-- with enrollments in 2+ sections.
-- ─────────────────────────────────────────────────────────────────────────────
SELECT
    e.student_id,
    e.client_id,
    COUNT(DISTINCT e.batch_id) AS section_count,
    ARRAY_AGG(DISTINCT e.batch_id) AS section_ids,
    COUNT(*) AS total_enrollments
FROM student.enrollments e
WHERE e.course_id != '__PLACEHOLDER_ASSOCIATION__'
  AND e.batch_id IS NOT NULL
GROUP BY e.student_id, e.client_id
HAVING COUNT(DISTINCT e.batch_id) > 1
ORDER BY section_count DESC;

-- Expected: 0 rows (or very few — only legitimate multi-section students).
-- Any student with 2 sections who was recently transferred = broken data.


-- ─────────────────────────────────────────────────────────────────────────────
-- QUERY 3: Students missing course enrollments for their section
-- For each student in a section, checks which courses are assigned to that
-- section but the student does NOT have an enrollment for.
-- This is the PRIMARY symptom of the bug: transfer moved 1 enrollment but
-- didn't create enrollments for other courses assigned to the destination.
-- ─────────────────────────────────────────────────────────────────────────────
WITH section_students AS (
    -- Distinct students per section (excluding placeholders)
    SELECT DISTINCT
        e.client_id,
        e.batch_id AS section_id,
        e.student_id
    FROM student.enrollments e
    WHERE e.batch_id IS NOT NULL
      AND e.course_id != '__PLACEHOLDER_ASSOCIATION__'
),
section_assigned_courses AS (
    -- Courses assigned to each section
    SELECT
        c.client_id,
        unnest(c.assigned_to_section_ids) AS section_id,
        c.id AS course_id,
        c.title AS course_title
    FROM content.courses c
    WHERE c.is_published = true
      AND c.assigned_to_section_ids IS NOT NULL
      AND array_length(c.assigned_to_section_ids, 1) > 0
),
expected_enrollments AS (
    -- Cross join: every student × every course for their section
    SELECT
        ss.client_id,
        ss.section_id,
        ss.student_id,
        sac.course_id,
        sac.course_title
    FROM section_students ss
    JOIN section_assigned_courses sac
        ON ss.section_id = sac.section_id
        AND ss.client_id = sac.client_id
),
existing_enrollments AS (
    SELECT client_id, student_id, course_id
    FROM student.enrollments
    WHERE course_id != '__PLACEHOLDER_ASSOCIATION__'
)
SELECT
    ee.client_id,
    ee.section_id,
    ee.student_id,
    ee.course_id AS missing_course_id,
    ee.course_title AS missing_course_title
FROM expected_enrollments ee
LEFT JOIN existing_enrollments ex
    ON ee.client_id = ex.client_id
    AND ee.student_id = ex.student_id
    AND ee.course_id = ex.course_id
WHERE ex.course_id IS NULL
ORDER BY ee.client_id, ee.section_id, ee.student_id;

-- Expected: 0 rows. Each row = a student who should be enrolled in a course
-- but isn't. This is the exact data the repair endpoint will fix.


-- ─────────────────────────────────────────────────────────────────────────────
-- QUERY 4: Summary counts — quick overview of the extent
-- ─────────────────────────────────────────────────────────────────────────────

-- 4a. Count of enrollments with wrong classId
SELECT
    COUNT(*) AS mismatched_class_enrollments
FROM student.enrollments e
JOIN student.sections s ON e.batch_id = s.id AND e.client_id = s.client_id
WHERE e.course_id != '__PLACEHOLDER_ASSOCIATION__'
  AND e.class_id != s.class_id;

-- 4b. Count of students split across multiple sections
SELECT
    COUNT(*) AS students_in_multiple_sections
FROM (
    SELECT e.student_id, e.client_id
    FROM student.enrollments e
    WHERE e.course_id != '__PLACEHOLDER_ASSOCIATION__'
      AND e.batch_id IS NOT NULL
    GROUP BY e.student_id, e.client_id
    HAVING COUNT(DISTINCT e.batch_id) > 1
) sub;

-- 4c. Count of missing course enrollments
WITH section_students AS (
    SELECT DISTINCT e.client_id, e.batch_id AS section_id, e.student_id
    FROM student.enrollments e
    WHERE e.batch_id IS NOT NULL
      AND e.course_id != '__PLACEHOLDER_ASSOCIATION__'
),
section_assigned_courses AS (
    SELECT c.client_id, unnest(c.assigned_to_section_ids) AS section_id, c.id AS course_id
    FROM content.courses c
    WHERE c.is_published = true
      AND c.assigned_to_section_ids IS NOT NULL
      AND array_length(c.assigned_to_section_ids, 1) > 0
)
SELECT
    COUNT(*) AS missing_enrollments
FROM section_students ss
JOIN section_assigned_courses sac
    ON ss.section_id = sac.section_id AND ss.client_id = sac.client_id
LEFT JOIN student.enrollments ex
    ON ss.client_id = ex.client_id
    AND ss.student_id = ex.student_id
    AND sac.course_id = ex.course_id
WHERE ex.id IS NULL;


-- ─────────────────────────────────────────────────────────────────────────────
-- QUERY 5: Check transfer audit trail — see which students were transferred
-- Shows all past transfers so you can cross-reference with the broken data.
-- ─────────────────────────────────────────────────────────────────────────────
SELECT
    created_at,
    entity_id AS enrollment_id,
    actor,
    meta->>'studentId' AS student_id,
    meta->>'sourceSectionId' AS from_section,
    meta->>'destinationSectionId' AS to_section,
    meta->>'oldCourseId' AS old_course,
    meta->>'newCourseId' AS new_course
FROM common.audit_logs
WHERE action = 'TRANSFER'
  AND entity = 'Enrollment'
ORDER BY created_at DESC
LIMIT 50;
