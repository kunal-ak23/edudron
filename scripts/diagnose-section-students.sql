-- Diagnosis Script for Section Student Association
-- Replace the placeholders with your actual IDs

-- STEP 1: Verify the section exists
-- Replace with your section ID: 01KFSZ83J9CE865416EBF08497
-- Replace with your client ID: 501e99b4-f969-40e7-9891-3e3e127b90de

SELECT 
    'Section Info' as check_type,
    id, 
    name, 
    class_id, 
    is_active, 
    student_count,
    max_students
FROM student.sections
WHERE id = '01KFSZ83J9CE865416EBF08497'
  AND client_id = '501e99b4-f969-40e7-9891-3e3e127b90de';

-- STEP 2: Check how many students exist in the identity service
SELECT 
    'Total Students in Identity Service' as check_type,
    COUNT(*) as count
FROM idp.users
WHERE client_id = '501e99b4-f969-40e7-9891-3e3e127b90de'
  AND role = 'STUDENT'
  AND active = true;

-- STEP 3: Check if students have ANY enrollments (associations)
SELECT 
    'Students with Enrollments' as check_type,
    COUNT(DISTINCT student_id) as count
FROM student.enrollments
WHERE client_id = '501e99b4-f969-40e7-9891-3e3e127b90de';

-- STEP 4: Check enrollments specifically for this section
SELECT 
    'Enrollments for This Section' as check_type,
    COUNT(*) as enrollment_count,
    COUNT(DISTINCT student_id) as unique_students
FROM student.enrollments
WHERE batch_id = '01KFSZ83J9CE865416EBF08497'
  AND client_id = '501e99b4-f969-40e7-9891-3e3e127b90de';

-- STEP 5: Detailed view of students in this section (if any)
SELECT 
    e.id as enrollment_id,
    e.student_id,
    u.name as student_name,
    u.email as student_email,
    e.course_id,
    e.batch_id as section_id,
    e.class_id,
    e.enrolled_at
FROM student.enrollments e
LEFT JOIN idp.users u ON e.student_id = u.id
WHERE e.batch_id = '01KFSZ83J9CE865416EBF08497'
  AND e.client_id = '501e99b4-f969-40e7-9891-3e3e127b90de'
ORDER BY e.enrolled_at DESC;

-- STEP 6: Check if students are associated with other sections/classes
SELECT 
    s.id as section_id,
    s.name as section_name,
    COUNT(DISTINCT e.student_id) as student_count
FROM student.sections s
LEFT JOIN student.enrollments e 
    ON s.id = e.batch_id 
    AND s.client_id = e.client_id
WHERE s.client_id = '501e99b4-f969-40e7-9891-3e3e127b90de'
GROUP BY s.id, s.name
ORDER BY s.name;

-- STEP 7: Find "orphan" students (in identity but not associated with any section)
SELECT 
    'Orphan Students (not associated with any section)' as check_type,
    COUNT(*) as count
FROM idp.users u
WHERE u.client_id = '501e99b4-f969-40e7-9891-3e3e127b90de'
  AND u.role = 'STUDENT'
  AND u.active = true
  AND NOT EXISTS (
      SELECT 1 
      FROM student.enrollments e 
      WHERE e.student_id = u.id 
        AND e.client_id = u.client_id
  );

-- STEP 8: List orphan students details
SELECT 
    u.id,
    u.name,
    u.email,
    u.phone,
    u.created_at
FROM idp.users u
WHERE u.client_id = '501e99b4-f969-40e7-9891-3e3e127b90de'
  AND u.role = 'STUDENT'
  AND u.active = true
  AND NOT EXISTS (
      SELECT 1 
      FROM student.enrollments e 
      WHERE e.student_id = u.id 
        AND e.client_id = u.client_id
  )
ORDER BY u.name;
