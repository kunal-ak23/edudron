-- Verify the exact table structure and data
SELECT 
    table_name,
    column_name,
    data_type,
    is_nullable
FROM information_schema.columns
WHERE table_schema = 'student' 
    AND table_name = 'sections'
ORDER BY ordinal_position;

-- Test the exact query that JPA would generate
SELECT *
FROM student.sections
WHERE id = '01KF5Z83J9CE865416EBF08497'
    AND client_id = '501e99b4-f969-40e7-9891-3e3e127b90de';

-- Check if column naming is snake_case vs camelCase
SELECT 
    id,
    client_id,
    name,
    class_id,
    is_active
FROM student.sections
WHERE id IN (
    '01KF5Z83J9CE865416EBF08497',
    '01KF5Z83JE92BAFC69C1D130B3'
);
