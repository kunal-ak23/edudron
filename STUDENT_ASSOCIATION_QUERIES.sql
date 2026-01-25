-- =====================================================
-- SQL Queries to Check Student Associations with Classes and Sections
-- =====================================================
-- Schema: student
-- Table: enrollments
-- Note: batch_id represents section_id (for backward compatibility)
-- =====================================================

-- 1. Get all students with their class and section associations
-- =====================================================
SELECT 
    e.student_id,
    e.class_id,
    e.batch_id AS section_id,
    e.institute_id,
    e.course_id,
    e.enrolled_at,
    COUNT(*) OVER (PARTITION BY e.student_id) AS total_enrollments_per_student
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'  -- Replace with actual client_id (UUID)
ORDER BY e.student_id, e.enrolled_at DESC;

-- 2. Count students by class
-- =====================================================
SELECT 
    e.class_id,
    COUNT(DISTINCT e.student_id) AS student_count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id IS NOT NULL
GROUP BY e.class_id
ORDER BY student_count DESC;

-- 3. Count students by section (batch_id)
-- =====================================================
SELECT 
    e.batch_id AS section_id,
    COUNT(DISTINCT e.student_id) AS student_count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.batch_id IS NOT NULL
GROUP BY e.batch_id
ORDER BY student_count DESC;

-- 4. Get students in a specific class
-- =====================================================
SELECT DISTINCT
    e.student_id,
    e.class_id,
    e.batch_id AS section_id,
    e.course_id,
    e.enrolled_at
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id = 'YOUR_CLASS_ID_HERE'  -- Replace with actual class_id
ORDER BY e.enrolled_at DESC;

-- 5. Get students in a specific section
-- =====================================================
SELECT DISTINCT
    e.student_id,
    e.class_id,
    e.batch_id AS section_id,
    e.course_id,
    e.enrolled_at
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.batch_id = 'YOUR_SECTION_ID_HERE'  -- Replace with actual section_id
ORDER BY e.enrolled_at DESC;

-- 6. Get students with their class but NO section (enrolled at class level only)
-- =====================================================
SELECT DISTINCT
    e.student_id,
    e.class_id,
    e.institute_id,
    e.course_id,
    e.enrolled_at
FROM student.enrollments e  
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id IS NOT NULL
    AND e.batch_id IS NULL
ORDER BY e.class_id, e.enrolled_at DESC;

-- 7. Get students enrolled in multiple sections
-- =====================================================
SELECT 
    e.student_id,
    COUNT(DISTINCT e.batch_id) AS section_count,
    STRING_AGG(DISTINCT e.batch_id, ', ') AS section_ids
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.batch_id IS NOT NULL
GROUP BY e.student_id
HAVING COUNT(DISTINCT e.batch_id) > 1
ORDER BY section_count DESC;

-- 8. Get students enrolled in multiple classes
-- =====================================================
SELECT 
    e.student_id,
    COUNT(DISTINCT e.class_id) AS class_count,
    STRING_AGG(DISTINCT e.class_id, ', ') AS class_ids
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id IS NOT NULL
GROUP BY e.student_id
HAVING COUNT(DISTINCT e.class_id) > 1
ORDER BY class_count DESC;

-- 9. Get class-section breakdown (students per class-section combination)
-- =====================================================
SELECT 
    e.class_id,
    e.batch_id AS section_id,
    COUNT(DISTINCT e.student_id) AS student_count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id IS NOT NULL
    AND e.batch_id IS NOT NULL
GROUP BY e.class_id, e.batch_id
ORDER BY e.class_id, student_count DESC;

-- 10. Find students in a class but NOT in any section
-- =====================================================
SELECT DISTINCT
    e.student_id,
    e.class_id,
    e.course_id,
    e.enrolled_at
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id = 'YOUR_CLASS_ID_HERE'  -- Replace with actual class_id
    AND e.batch_id IS NULL
ORDER BY e.enrolled_at DESC;

-- 11. Find students in a section but NOT in the parent class
-- =====================================================
-- Note: This shouldn't happen in normal cases, but useful for data validation
SELECT DISTINCT
    e.student_id,
    e.batch_id AS section_id,
    e.class_id,
    e.course_id
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.batch_id IS NOT NULL
    AND e.class_id IS NULL
ORDER BY e.batch_id;

-- 12. Get detailed student-class-section-course associations
-- =====================================================
SELECT 
    e.student_id,
    e.class_id,
    e.batch_id AS section_id,
    e.institute_id,
    e.course_id,
    e.enrolled_at,
    ROW_NUMBER() OVER (
        PARTITION BY e.student_id, e.course_id 
        ORDER BY e.enrolled_at DESC
    ) AS enrollment_rank
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
ORDER BY e.student_id, e.course_id, e.enrolled_at DESC;

-- 13. Count enrollments by class and section (including nulls)
-- =====================================================
SELECT 
    COALESCE(e.class_id, 'NO_CLASS') AS class_id,
    COALESCE(e.batch_id, 'NO_SECTION') AS section_id,
    COUNT(*) AS enrollment_count,
    COUNT(DISTINCT e.student_id) AS unique_students
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
GROUP BY e.class_id, e.batch_id
ORDER BY enrollment_count DESC;

-- 14. Find duplicate enrollments (same student, same course, multiple class/section)
-- =====================================================
SELECT 
    e.student_id,
    e.course_id,
    COUNT(*) AS enrollment_count,
    STRING_AGG(DISTINCT e.class_id, ', ') AS class_ids,
    STRING_AGG(DISTINCT e.batch_id, ', ') AS section_ids
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
GROUP BY e.student_id, e.course_id
HAVING COUNT(*) > 1
ORDER BY enrollment_count DESC;

-- 15. Get student count summary by class and section
-- =====================================================
SELECT 
    e.class_id,
    e.batch_id AS section_id,
    COUNT(DISTINCT e.student_id) AS unique_students,
    COUNT(*) AS total_enrollments,
    MIN(e.enrolled_at) AS first_enrollment,
    MAX(e.enrolled_at) AS last_enrollment
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id IS NOT NULL
GROUP BY e.class_id, e.batch_id
ORDER BY e.class_id, unique_students DESC;

-- 16. Find students enrolled in "Morning" section specifically
-- =====================================================
-- First, find the section_id for "Morning" section
-- (You'll need to query the sections table or know the ID)
SELECT DISTINCT
    e.student_id,
    e.class_id,
    e.batch_id AS section_id,
    e.course_id,
    e.enrolled_at
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.batch_id = 'MORNING_SECTION_ID_HERE'  -- Replace with actual Morning section ID
ORDER BY e.enrolled_at DESC;

-- 17. Compare Morning vs Evening Batch student counts
-- =====================================================
SELECT 
    e.batch_id AS section_id,
    COUNT(DISTINCT e.student_id) AS student_count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.batch_id IN ('MORNING_SECTION_ID', 'EVENING_SECTION_ID')  -- Replace with actual IDs
GROUP BY e.batch_id;

-- 18. Find students in both Morning and Evening sections
-- =====================================================
SELECT 
    e.student_id,
    COUNT(DISTINCT e.batch_id) AS sections_count,
    STRING_AGG(DISTINCT e.batch_id, ', ') AS section_ids
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.batch_id IN ('MORNING_SECTION_ID', 'EVENING_SECTION_ID')  -- Replace with actual IDs
GROUP BY e.student_id
HAVING COUNT(DISTINCT e.batch_id) = 2  -- Enrolled in both
ORDER BY e.student_id;

-- =====================================================
-- HELPER QUERIES
-- =====================================================

-- Get your client_id (tenant UUID)
SELECT DISTINCT client_id FROM student.enrollments ORDER BY client_id;

-- Get all class IDs
SELECT DISTINCT class_id FROM student.enrollments 
WHERE client_id = 'YOUR_CLIENT_ID_HERE' AND class_id IS NOT NULL;

-- Get all section IDs (batch_id)
SELECT DISTINCT batch_id AS section_id FROM student.enrollments 
WHERE client_id = 'YOUR_CLIENT_ID_HERE' AND batch_id IS NOT NULL;

-- Get section names (if you have access to sections table)
-- Note: You may need to join with student.sections table if it exists
SELECT 
    s.id AS section_id,
    s.name AS section_name,
    s.class_id,
    COUNT(DISTINCT e.student_id) AS student_count
FROM student.sections s
LEFT JOIN student.enrollments e ON e.batch_id = s.id AND e.client_id = s.client_id
WHERE s.client_id = 'YOUR_CLIENT_ID_HERE'
GROUP BY s.id, s.name, s.class_id
ORDER BY s.class_id, s.name;

-- =====================================================
-- DEBUGGING QUERIES - Use these to diagnose the issue
-- =====================================================

-- 19. Check what batch_id values actually exist in enrollments for this class
-- =====================================================
SELECT 
    e.batch_id AS section_id,
    COUNT(DISTINCT e.student_id) AS student_count,
    COUNT(*) AS total_enrollments
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id = '01KEWJJA9S969674E999E2CB3A'  -- Replace with your class_id
GROUP BY e.batch_id
ORDER BY student_count DESC;

-- 20. Check if students have enrollments with NULL batch_id (class-level only)
-- =====================================================
SELECT 
    COUNT(DISTINCT e.student_id) AS students_without_section,
    COUNT(*) AS enrollments_without_section
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id = '01KEWJJA9S969674E999E2CB3A'  -- Replace with your class_id
    AND e.batch_id IS NULL;

-- 21. Get the actual section IDs for Morning and Evening Batch
-- =====================================================
SELECT 
    s.id AS section_id,
    s.name AS section_name,
    s.class_id
FROM student.sections s
WHERE s.client_id = 'YOUR_CLIENT_ID_HERE'
    AND s.class_id = '01KEWJJA9S969674E999E2CB3A'  -- Replace with your class_id
    AND s.name IN ('Morning', 'Evening Batch')
ORDER BY s.name;

-- 22. Check enrollments specifically for Morning section ID
-- =====================================================
-- First run query 21 to get the Morning section_id, then use it here
SELECT 
    e.student_id,
    e.batch_id AS section_id,
    e.class_id,
    e.course_id,
    e.enrolled_at
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.batch_id = '01KEWJRM2941FC08148AAA0AE5'  -- Morning section_id from query 21
ORDER BY e.enrolled_at DESC
LIMIT 10;

-- 23. Check enrollments specifically for Evening Batch section ID
-- =====================================================
SELECT 
    e.student_id,
    e.batch_id AS section_id,
    e.class_id,
    e.course_id,
    e.enrolled_at
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.batch_id = '01KEWJRYM26C36E5DCCA1AC290'  -- Evening Batch section_id from query 21
ORDER BY e.enrolled_at DESC
LIMIT 10;

-- 24. Compare: Students in class vs students in sections
-- =====================================================
SELECT 
    'Total students in class' AS category,
    COUNT(DISTINCT e.student_id) AS count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id = '01KEWJJA9S969674E999E2CB3A'  -- Replace with your class_id

UNION ALL

SELECT 
    'Students with ANY section' AS category,
    COUNT(DISTINCT e.student_id) AS count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id = '01KEWJJA9S969674E999E2CB3A'
    AND e.batch_id IS NOT NULL

UNION ALL

SELECT 
    'Students with NO section (class-level only)' AS category,
    COUNT(DISTINCT e.student_id) AS count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id = '01KEWJJA9S969674E999E2CB3A'
    AND e.batch_id IS NULL

UNION ALL

SELECT 
    'Students in Morning section' AS category,
    COUNT(DISTINCT e.student_id) AS count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.batch_id = '01KEWJRM2941FC08148AAA0AE5'  -- Morning section_id

UNION ALL

SELECT 
    'Students in Evening Batch section' AS category,
    COUNT(DISTINCT e.student_id) AS count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.batch_id = '01KEWJRYM26C36E5DCCA1AC290';  -- Evening Batch section_id

-- 25. Find students who appear to be in both sections (check for data issues)
-- =====================================================
SELECT 
    e.student_id,
    COUNT(DISTINCT e.batch_id) AS sections_count,
    STRING_AGG(DISTINCT e.batch_id, ', ') AS section_ids,
    STRING_AGG(DISTINCT e.course_id, ', ') AS course_ids
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id = '01KEWJJA9S969674E999E2CB3A'  -- Replace with your class_id
    AND e.batch_id IN ('01KEWJRM2941FC08148AAA0AE5', '01KEWJRYM26C36E5DCCA1AC290')  -- Morning and Evening IDs
GROUP BY e.student_id
HAVING COUNT(DISTINCT e.batch_id) > 1
ORDER BY e.student_id;

-- 26. Sample enrollments to see the actual data structure
-- =====================================================
SELECT 
    e.id,
    e.student_id,
    e.class_id,
    e.batch_id AS section_id,
    e.course_id,
    e.institute_id,
    e.enrolled_at
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID_HERE'
    AND e.class_id = '01KEWJJA9S969674E999E2CB3A'  -- Replace with your class_id
ORDER BY e.enrolled_at DESC
LIMIT 20;
