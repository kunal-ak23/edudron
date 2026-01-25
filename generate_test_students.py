#!/usr/bin/env python3
"""
Script to generate test students for all permutations of Institute -> Class -> Section
Each permutation gets 10 students with email format: student<no>@testuni.com
"""

import uuid
from datetime import datetime, timezone
import json

# IDs from the Test University structure
INSTITUTE_ID = "01KF5RV0T1D39E20016FB874CB"
CLASS_ID = "01KF5Z83HQ0A66BDD01A0C3DB4"
CLASS_NAME = "Computer engineering"
CLASS_CODE = "COM2024"

SECTIONS = [
    {"id": "01KF5Z83J9CE865416EBF08497", "name": "Morning"},
    {"id": "01KF5Z83JE92BAFC69C1D130B3", "name": "Evening"}
]

# Configuration
STUDENTS_PER_SECTION = 10
EMAIL_DOMAIN = "testuni.com"
CLIENT_ID = "YOUR_CLIENT_ID_HERE"  # Replace with actual tenant/client UUID
COURSE_ID = "YOUR_COURSE_ID_HERE"  # Replace with actual course UUID if needed


def generate_ulid():
    """Generate a ULID-style ID (simplified version)"""
    # Note: For production, use a proper ULID library
    # This is a simplified version for demonstration
    timestamp = int(datetime.now(timezone.utc).timestamp() * 1000)
    random_part = uuid.uuid4().hex[:16].upper()
    return f"{timestamp:013X}{random_part}"


def generate_students_sql():
    """Generate SQL INSERT statements for students and enrollments"""
    
    print("-- =====================================================")
    print("-- Generated Test Students for Test Uni Inst")
    print(f"-- Generated at: {datetime.now(timezone.utc).isoformat()}")
    print("-- =====================================================")
    print()
    print("-- IMPORTANT: Replace 'YOUR_CLIENT_ID_HERE' with your actual tenant UUID")
    print("-- IMPORTANT: Replace 'YOUR_COURSE_ID_HERE' with your actual course UUID if needed")
    print()
    
    student_counter = 1
    all_students = []
    
    for section in SECTIONS:
        section_id = section["id"]
        section_name = section["name"]
        
        print(f"-- =====================================================")
        print(f"-- Section: {section_name} ({section_id})")
        print(f"-- =====================================================")
        print()
        
        section_students = []
        
        for i in range(1, STUDENTS_PER_SECTION + 1):
            student_id = generate_ulid()
            email = f"student{student_counter}@{EMAIL_DOMAIN}"
            first_name = f"Student{student_counter}"
            last_name = f"{section_name}"
            phone = f"+1555{student_counter:04d}"
            
            student_data = {
                "id": student_id,
                "email": email,
                "first_name": first_name,
                "last_name": last_name,
                "phone": phone,
                "section_id": section_id,
                "section_name": section_name
            }
            
            section_students.append(student_data)
            all_students.append(student_data)
            student_counter += 1
        
        # Generate INSERT statement for students table
        print("-- Insert Students")
        print("INSERT INTO student.students (")
        print("    id, client_id, email, first_name, last_name, phone, ")
        print("    is_active, created_at, updated_at")
        print(") VALUES")
        
        for idx, student in enumerate(section_students):
            comma = "," if idx < len(section_students) - 1 else ";"
            print(f"    ('{student['id']}', '{CLIENT_ID}', '{student['email']}', '{student['first_name']}', '{student['last_name']}', '{student['phone']}', true, NOW(), NOW()){comma}")
        
        print()
        
        # Generate INSERT statement for enrollments table
        print("-- Enroll Students in Section")
        print("INSERT INTO student.enrollments (")
        print("    id, client_id, student_id, institute_id, class_id, batch_id, course_id, ")
        print("    enrolled_at, created_at, updated_at")
        print(") VALUES")
        
        for idx, student in enumerate(section_students):
            enrollment_id = generate_ulid()
            comma = "," if idx < len(section_students) - 1 else ";"
            print(f"    ('{enrollment_id}', '{CLIENT_ID}', '{student['id']}', '{INSTITUTE_ID}', '{CLASS_ID}', '{section_id}', '{COURSE_ID}', NOW(), NOW(), NOW()){comma}")
        
        print()
        print()
    
    # Summary
    print("-- =====================================================")
    print("-- SUMMARY")
    print("-- =====================================================")
    print(f"-- Total Students Created: {len(all_students)}")
    print(f"-- Students per Section: {STUDENTS_PER_SECTION}")
    print(f"-- Total Sections: {len(SECTIONS)}")
    print()
    
    # Generate verification queries
    print("-- =====================================================")
    print("-- VERIFICATION QUERIES")
    print("-- =====================================================")
    print()
    print("-- 1. Count students by section")
    print("SELECT ")
    print("    e.batch_id AS section_id,")
    print("    COUNT(DISTINCT e.student_id) AS student_count")
    print("FROM student.enrollments e")
    print(f"WHERE e.client_id = '{CLIENT_ID}'")
    print(f"    AND e.class_id = '{CLASS_ID}'")
    print("GROUP BY e.batch_id")
    print("ORDER BY student_count DESC;")
    print()
    
    print("-- 2. List all created students with their sections")
    print("SELECT ")
    print("    s.id AS student_id,")
    print("    s.email,")
    print("    s.first_name,")
    print("    s.last_name,")
    print("    e.batch_id AS section_id")
    print("FROM student.students s")
    print("INNER JOIN student.enrollments e ON e.student_id = s.id")
    print(f"WHERE s.client_id = '{CLIENT_ID}'")
    print(f"    AND s.email LIKE '%@{EMAIL_DOMAIN}'")
    print("ORDER BY s.email;")
    print()
    
    # Generate JSON output for reference
    print("-- =====================================================")
    print("-- JSON DATA (for reference)")
    print("-- =====================================================")
    print("/*")
    print(json.dumps(all_students, indent=2))
    print("*/")


def generate_python_dict():
    """Generate Python dictionary for programmatic use"""
    
    student_counter = 1
    all_students = []
    
    for section in SECTIONS:
        section_id = section["id"]
        section_name = section["name"]
        
        for i in range(1, STUDENTS_PER_SECTION + 1):
            student_id = generate_ulid()
            email = f"student{student_counter}@{EMAIL_DOMAIN}"
            first_name = f"Student{student_counter}"
            last_name = f"{section_name}"
            phone = f"+1555{student_counter:04d}"
            
            student_data = {
                "id": student_id,
                "email": email,
                "first_name": first_name,
                "last_name": last_name,
                "phone": phone,
                "institute_id": INSTITUTE_ID,
                "class_id": CLASS_ID,
                "class_name": CLASS_NAME,
                "class_code": CLASS_CODE,
                "section_id": section_id,
                "section_name": section_name
            }
            
            all_students.append(student_data)
            student_counter += 1
    
    return all_students


if __name__ == "__main__":
    import sys
    
    if len(sys.argv) > 1 and sys.argv[1] == "--json":
        # Output as JSON
        students = generate_python_dict()
        print(json.dumps(students, indent=2))
    elif len(sys.argv) > 1 and sys.argv[1] == "--python":
        # Output as Python dict
        students = generate_python_dict()
        print("students = ", end="")
        print(students)
    else:
        # Default: Generate SQL
        generate_students_sql()
