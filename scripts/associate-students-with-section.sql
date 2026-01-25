-- Script to Associate Existing Students with a Section
-- This creates placeholder enrollments to establish the student-section relationship

-- IMPORTANT: Replace these values with your actual IDs
-- Section ID: 01KFSZ83J9CE865416EBF08497
-- Class ID: 01KFSZ83HQ0A66BDD01A0C3DB4
-- Institute ID: 01KFSRV0T1D39E20016FB874CB
-- Client ID: 501e99b4-f969-40e7-9891-3e3e127b90de

-- OPTION 1: Associate ALL students in the tenant with this section
-- Use this if you want to add all students to the section

INSERT INTO student.enrollments (
    id,
    client_id,
    student_id,
    course_id,
    batch_id,
    class_id,
    institute_id,
    enrolled_at
)
SELECT 
    gen_random_uuid()::text || substring(encode(gen_random_bytes(10), 'base64'), 1, 16) as id,  -- Generate ULID-like ID
    u.client_id,
    u.id as student_id,
    '__PLACEHOLDER_ASSOCIATION__' as course_id,
    '01KFSZ83J9CE865416EBF08497' as batch_id,  -- Section ID
    '01KFSZ83HQ0A66BDD01A0C3DB4' as class_id,   -- Class ID
    '01KFSRV0T1D39E20016FB874CB' as institute_id, -- Institute ID
    NOW() as enrolled_at
FROM idp.users u
WHERE u.client_id = '501e99b4-f969-40e7-9891-3e3e127b90de'::uuid
  AND u.role = 'STUDENT'
  AND u.active = true
  AND NOT EXISTS (
      -- Don't create duplicate associations
      SELECT 1 
      FROM student.enrollments e 
      WHERE e.student_id = u.id 
        AND e.batch_id = '01KFSZ83J9CE865416EBF08497'
        AND e.client_id = u.client_id
  );

-- Verify the associations were created
SELECT 
    COUNT(*) as total_associations,
    COUNT(DISTINCT student_id) as unique_students
FROM student.enrollments
WHERE batch_id = '01KFSZ83J9CE865416EBF08497'
  AND client_id = '501e99b4-f969-40e7-9891-3e3e127b90de'::uuid;

-- OPTION 2: Associate SPECIFIC students by email
-- Use this if you want to add only certain students
-- Uncomment and modify the WHERE clause

/*
INSERT INTO student.enrollments (
    id,
    client_id,
    student_id,
    course_id,
    batch_id,
    class_id,
    institute_id,
    enrolled_at
)
SELECT 
    gen_random_uuid()::text || substring(encode(gen_random_bytes(10), 'base64'), 1, 16) as id,
    u.client_id,
    u.id as student_id,
    '__PLACEHOLDER_ASSOCIATION__' as course_id,
    '01KFSZ83J9CE865416EBF08497' as batch_id,
    '01KFSZ83HQ0A66BDD01A0C3DB4' as class_id,
    '01KFSRV0T1D39E20016FB874CB' as institute_id,
    NOW() as enrolled_at
FROM idp.users u
WHERE u.client_id = '501e99b4-f969-40e7-9891-3e3e127b90de'::uuid
  AND u.role = 'STUDENT'
  AND u.active = true
  AND u.email IN (
      'student1@example.com',
      'student2@example.com',
      'student3@example.com'
      -- Add more emails as needed
  )
  AND NOT EXISTS (
      SELECT 1 
      FROM student.enrollments e 
      WHERE e.student_id = u.id 
        AND e.batch_id = '01KFSZ83J9CE865416EBF08497'
        AND e.client_id = u.client_id
  );
*/

-- Update the section's student_count field
UPDATE student.sections
SET student_count = (
    SELECT COUNT(DISTINCT student_id)
    FROM student.enrollments
    WHERE batch_id = '01KFSZ83J9CE865416EBF08497'
      AND client_id = '501e99b4-f969-40e7-9891-3e3e127b90de'::uuid
)
WHERE id = '01KFSZ83J9CE865416EBF08497'
  AND client_id = '501e99b4-f969-40e7-9891-3e3e127b90de'::uuid;

-- Final verification
SELECT 
    s.name as section_name,
    s.student_count,
    COUNT(DISTINCT e.student_id) as actual_students,
    array_agg(DISTINCT u.email ORDER BY u.email) as student_emails
FROM student.sections s
LEFT JOIN student.enrollments e 
    ON s.id = e.batch_id 
    AND s.client_id = e.client_id
LEFT JOIN idp.users u 
    ON e.student_id = u.id
WHERE s.id = '01KFSZ83J9CE865416EBF08497'
  AND s.client_id = '501e99b4-f969-40e7-9891-3e3e127b90de'::uuid
GROUP BY s.id, s.name, s.student_count;
