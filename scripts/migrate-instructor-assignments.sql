-- Migration Script: Convert institute-based instructor assignments to InstructorAssignment records
-- 
-- This script migrates existing instructors from the old institute-based access model
-- to the new InstructorAssignment model by creating CLASS-type assignments for all
-- classes in their assigned institutes.
--
-- Prerequisites:
-- 1. The student.instructor_assignments table must exist (run the Liquibase migration first)
-- 2. This should be run once after deploying the new schema
--
-- Run this with: psql -d edudron -f migrate-instructor-assignments.sql

-- First, let's see how many instructors we're dealing with
SELECT 'Existing instructors with institute assignments:' as info;
SELECT COUNT(DISTINCT ui.user_id) as instructor_count
FROM idp.user_institutes ui
JOIN idp.users u ON u.id = ui.user_id
WHERE u.role = 'INSTRUCTOR' AND u.active = true;

-- Begin transaction
BEGIN;

-- Create a function to generate ULIDs (if not exists)
-- Using a simplified approach - in production you might want to use a proper ULID generator
CREATE OR REPLACE FUNCTION generate_ulid() RETURNS VARCHAR(26) AS $$
DECLARE
    timestamp_part VARCHAR(10);
    random_part VARCHAR(16);
BEGIN
    -- Create timestamp part (10 characters)
    timestamp_part := LPAD(TO_HEX(EXTRACT(EPOCH FROM NOW())::BIGINT * 1000), 10, '0');
    -- Create random part (16 characters)
    random_part := LPAD(TO_HEX((RANDOM() * 10000000000000000)::BIGINT), 16, '0');
    -- Combine and return (use first 26 chars)
    RETURN UPPER(SUBSTRING(timestamp_part || random_part FROM 1 FOR 26));
END;
$$ LANGUAGE plpgsql;

-- Migrate instructors: Create CLASS assignments for each class in their assigned institutes
INSERT INTO student.instructor_assignments (
    id,
    client_id,
    instructor_user_id,
    assignment_type,
    class_id,
    section_id,
    course_id,
    scoped_class_ids,
    scoped_section_ids,
    created_at,
    updated_at
)
SELECT 
    generate_ulid() || LPAD(ROW_NUMBER() OVER ()::TEXT, 4, '0') as id,  -- Ensure unique ID
    u.client_id,
    u.id as instructor_user_id,
    'CLASS' as assignment_type,
    c.id as class_id,
    NULL as section_id,
    NULL as course_id,
    NULL as scoped_class_ids,
    NULL as scoped_section_ids,
    NOW() as created_at,
    NOW() as updated_at
FROM idp.users u
JOIN idp.user_institutes ui ON u.id = ui.user_id
JOIN student.classes c ON c.institute_id = ui.institute_id AND c.client_id = u.client_id
WHERE u.role = 'INSTRUCTOR' 
  AND u.active = true
  AND c.is_active = true
  -- Avoid duplicates: don't insert if assignment already exists
  AND NOT EXISTS (
      SELECT 1 FROM student.instructor_assignments ia 
      WHERE ia.instructor_user_id = u.id 
        AND ia.client_id = u.client_id 
        AND ia.class_id = c.id
        AND ia.assignment_type = 'CLASS'
  );

-- Report the migration results
SELECT 'Migration complete. New assignments created:' as info;
SELECT 
    assignment_type,
    COUNT(*) as assignment_count
FROM student.instructor_assignments
GROUP BY assignment_type;

-- Show sample of migrated data
SELECT 'Sample of migrated assignments:' as info;
SELECT 
    ia.id,
    ia.instructor_user_id,
    u.name as instructor_name,
    u.email as instructor_email,
    ia.assignment_type,
    ia.class_id,
    c.name as class_name,
    ia.created_at
FROM student.instructor_assignments ia
JOIN idp.users u ON u.id = ia.instructor_user_id
LEFT JOIN student.classes c ON c.id = ia.class_id
LIMIT 10;

COMMIT;

-- Verification queries (run these after migration to verify)
-- 
-- Check instructors who still have no assignments (may need manual attention):
-- SELECT u.id, u.name, u.email
-- FROM idp.users u
-- WHERE u.role = 'INSTRUCTOR' AND u.active = true
--   AND NOT EXISTS (
--       SELECT 1 FROM student.instructor_assignments ia WHERE ia.instructor_user_id = u.id
--   );
--
-- Check assignment counts per instructor:
-- SELECT 
--     ia.instructor_user_id,
--     u.name,
--     COUNT(*) as assignment_count
-- FROM student.instructor_assignments ia
-- JOIN idp.users u ON u.id = ia.instructor_user_id
-- GROUP BY ia.instructor_user_id, u.name
-- ORDER BY assignment_count DESC;
