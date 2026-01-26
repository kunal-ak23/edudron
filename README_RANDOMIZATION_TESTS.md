# Exam Randomization Tests

This document describes the comprehensive test suite for the exam randomization feature.

## Test Coverage

### 1. ExamRandomizationTest.java (Student Service)
**Location**: `student/src/test/java/com/datagami/edudron/student/service/ExamRandomizationTest.java`

Tests the generation and persistence of randomization when a student starts an exam.

**Test Cases:**
- ✅ `testRandomizeQuestions_Success` - Verifies question order is randomized and saved
- ✅ `testRandomizeMcqOptions_Success` - Verifies MCQ option order is randomized and saved
- ✅ `testBothRandomizations_Success` - Verifies both randomizations work together
- ✅ `testNoRandomization_Success` - Verifies no randomization when disabled
- ✅ `testResumeExistingSubmission_NoNewRandomization` - Verifies existing randomization is preserved
- ✅ `testExamApiFails_ContinuesWithoutRandomization` - Verifies graceful failure handling
- ✅ `testOnlyRandomizesMcqQuestions` - Verifies only MCQ/TRUE_FALSE questions get option randomization

**Key Validations:**
- All question IDs are present after randomization
- All option IDs are present after randomization
- Randomization is saved to submission
- Original exam continues if API fails

### 2. ExamRandomizationApplicationTest.java (Student Web Controller)
**Location**: `student/src/test/java/com/datagami/edudron/student/web/ExamRandomizationApplicationTest.java`

Tests the application of saved randomization when returning exam to student.

**Test Cases:**
- ✅ `testApplyQuestionOrderRandomization` - Verifies questions are reordered based on saved order
- ✅ `testApplyMcqOptionOrderRandomization` - Verifies options are reordered based on saved order
- ✅ `testNoSubmission_ReturnsOriginal` - Verifies original order when no submission exists
- ✅ `testSubmissionWithoutRandomization_ReturnsOriginal` - Verifies original order when randomization not saved
- ✅ `testMissingQuestionIds_HandlesGracefully` - Verifies graceful handling of data inconsistencies
- ✅ `testCorrectAnswersMaintained_AfterRandomization` - **CRITICAL**: Verifies correct answer flags follow options

**Key Validations:**
- Questions displayed in saved randomized order
- Options displayed in saved randomized order
- Correct answer flags remain accurate after reordering
- Graceful handling of missing or invalid IDs

### 3. ExamGradingWithRandomizationTest.java (Content Service)
**Location**: `content/src/test/java/com/datagami/edudron/content/service/ExamGradingWithRandomizationTest.java`

Tests that grading works correctly with randomized exams (most critical test suite).

**Test Cases:**
- ✅ `testGradingWithRandomizedQuestionOrder` - Verifies grading is correct regardless of question order
- ✅ `testGradingWithRandomizedMcqOptions` - Verifies grading is correct regardless of option order
- ✅ `testWrongAnswerWithRandomization` - Verifies wrong answers still get 0 points
- ✅ `testGradingByIdNotPosition` - **CRITICAL**: Verifies grading uses IDs, not positions
- ✅ `testPartialCreditWithRandomization` - Verifies partial credit calculation is accurate

**Key Validations:**
- Grading based on option IDs, not display positions
- Correct answers get full points regardless of position
- Wrong answers get zero points regardless of position
- Total scores calculated correctly with mixed answers

## Running the Tests

### Run all randomization tests:
```bash
# From project root
./gradlew test --tests "*Randomization*"
```

### Run individual test suites:
```bash
# Test randomization generation
./gradlew :student:test --tests "ExamRandomizationTest"

# Test randomization application
./gradlew :student:test --tests "ExamRandomizationApplicationTest"

# Test grading with randomization
./gradlew :content:test --tests "ExamGradingWithRandomizationTest"
```

### Run specific test:
```bash
./gradlew :student:test --tests "ExamRandomizationTest.testRandomizeQuestions_Success"
```

## Test Architecture

All tests follow the same pattern:
1. **@ExtendWith(MockitoExtension.class)** - Use Mockito for mocking
2. **@Mock** - Mock repositories and external dependencies
3. **@InjectMocks** - Auto-inject mocks into service under test
4. **@BeforeEach** - Set up tenant context and test data
5. **@AfterEach** - Clean up tenant context
6. **ReflectionTestUtils** - Set private fields (like ObjectMapper)

## Critical Test Scenarios

### Scenario 1: Different Students, Different Orders
```
Student A starts exam → Gets order [Q3, Q1, Q2]
Student B starts exam → Gets order [Q2, Q3, Q1] (different)
Both see consistent order throughout their attempt
Both graded correctly
```

### Scenario 2: Same Student, Consistent Order
```
Student starts exam → Gets order [Q3, Q1, Q2]
Student refreshes page → Still sees [Q3, Q1, Q2]
Student resumes later → Still sees [Q3, Q1, Q2]
```

### Scenario 3: Grading Independence
```
Question 1 correct answer: Option C (ID: "opt3")
Student A sees: [A, B, C, D] → Selects C → Gets "opt3" → Correct
Student B sees: [C, D, A, B] → Selects C → Gets "opt3" → Correct
Both receive same score
```

## Integration Testing Checklist

Manual integration tests to perform:

1. **Create Exam with Randomization**
   - [ ] Create exam with both randomization options enabled
   - [ ] Verify settings are saved

2. **Multiple Students Test**
   - [ ] Have 3-5 different students start the same exam
   - [ ] Verify they see different question/option orders
   - [ ] Verify each student sees consistent order on refresh

3. **Grading Test**
   - [ ] Have students answer questions correctly
   - [ ] Verify all receive correct scores
   - [ ] Have students answer questions incorrectly
   - [ ] Verify all receive zero for wrong answers

4. **Resume Test**
   - [ ] Student starts exam
   - [ ] Student closes browser
   - [ ] Student returns and resumes
   - [ ] Verify same randomized order is shown

5. **No Randomization Test**
   - [ ] Create exam without randomization
   - [ ] Verify all students see same order
   - [ ] Verify grading still works

## Edge Cases Covered

- ✅ API failure during exam start (continues without randomization)
- ✅ Missing question IDs in saved order (skips invalid IDs)
- ✅ Submission without randomization data (shows original order)
- ✅ Non-MCQ questions (SHORT_ANSWER, ESSAY) don't get option randomization
- ✅ TRUE_FALSE questions get option randomization
- ✅ Correct answer with ID at any position graded correctly
- ✅ Partial credit calculated correctly with randomization

## Test Data Structure

### Mock Exam Structure:
```json
{
  "id": "exam123",
  "randomizeQuestions": true,
  "randomizeMcqOptions": true,
  "questions": [
    {
      "id": "q1",
      "questionType": "MULTIPLE_CHOICE",
      "points": 2,
      "options": [
        {"id": "q1opt1", "isCorrect": true},
        {"id": "q1opt2", "isCorrect": false},
        {"id": "q1opt3", "isCorrect": false},
        {"id": "q1opt4", "isCorrect": false}
      ]
    }
  ]
}
```

### Mock Submission with Randomization:
```json
{
  "id": "sub123",
  "questionOrder": ["q3", "q1", "q2"],
  "mcqOptionOrders": {
    "q1": ["q1opt3", "q1opt1", "q1opt4", "q1opt2"],
    "q2": ["q2opt2", "q2opt4", "q2opt1", "q2opt3"]
  }
}
```

## Success Criteria

All tests should pass with:
- ✅ All randomization properly saved
- ✅ All randomization consistently applied
- ✅ All grading 100% accurate
- ✅ No data loss or corruption
- ✅ Graceful error handling

## Future Test Considerations

Consider adding:
- Performance tests (randomization overhead)
- Concurrent student start tests (race conditions)
- Database constraint tests
- Frontend integration tests (if testing framework added)
