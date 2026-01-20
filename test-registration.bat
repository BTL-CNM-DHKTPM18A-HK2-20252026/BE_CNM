@echo off
echo ========================================
echo Fruvia Backend - User Registration Test
echo ========================================
echo.

REM Wait for server to be ready
echo Waiting for server to start...
timeout /t 5 /nobreak > nul

REM Test 1: Register User Successfully
echo Test 1: Register New User
echo Endpoint: POST http://localhost:8080/api/v1/users
echo.

curl -X POST "http://localhost:8080/api/v1/users" ^
  -H "Content-Type: application/json" ^
  -d "{\"phoneNumber\":\"0123456789\",\"email\":\"john.doe@example.com\",\"password\":\"password123\",\"displayName\":\"John Doe\",\"firstName\":\"John\",\"lastName\":\"Doe\"}"

echo.
echo.
echo ========================================
echo.

REM Test 2: Register Another User
echo Test 2: Register Another Valid User
echo Endpoint: POST http://localhost:8080/api/v1/users
echo.

curl -X POST "http://localhost:8080/api/v1/users" ^
  -H "Content-Type: application/json" ^
  -d "{\"phoneNumber\":\"0987654321\",\"email\":\"jane.smith@example.com\",\"password\":\"securepass456\",\"displayName\":\"Jane Smith\",\"firstName\":\"Jane\",\"lastName\":\"Smith\"}"

echo.
echo.
echo ========================================
echo.

REM Test 3: Duplicate Email (Should Fail)
echo Test 3: Register Duplicate Email - Should Fail
echo.

curl -X POST "http://localhost:8080/api/v1/users" ^
  -H "Content-Type: application/json" ^
  -d "{\"phoneNumber\":\"0999888777\",\"email\":\"john.doe@example.com\",\"password\":\"password456\",\"displayName\":\"Duplicate User\"}"

echo.
echo.
echo ========================================
echo All Tests Completed!
echo ========================================
pause
