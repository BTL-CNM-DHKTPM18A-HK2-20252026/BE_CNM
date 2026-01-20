# Test Script for User Registration
# PowerShell Script

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Fruvia Backend - User Registration Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080/api/v1"

# Test 1: Register User Successfully
Write-Host "Test 1: Register New User" -ForegroundColor Yellow
Write-Host "Endpoint: POST $baseUrl/users" -ForegroundColor Gray

$registerRequest = @{
    phoneNumber = "0123456789"
    email = "john.doe@example.com"
    password = "password123"
    displayName = "John Doe"
    firstName = "John"
    lastName = "Doe"
} | ConvertTo-Json

Write-Host "Request Body:" -ForegroundColor Gray
Write-Host $registerRequest -ForegroundColor White
Write-Host ""

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/users" -Method Post -Body $registerRequest -ContentType "application/json"
    Write-Host "✓ SUCCESS" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Gray
    $response | ConvertTo-Json -Depth 10 | Write-Host -ForegroundColor White
    
    $userId = $response.user_id
    Write-Host ""
    Write-Host "User ID: $userId" -ForegroundColor Green
    
    # Test 2: Get User by ID
    Write-Host ""
    Write-Host "Test 2: Get User By ID" -ForegroundColor Yellow
    Write-Host "Endpoint: GET $baseUrl/users/$userId" -ForegroundColor Gray
    
    $userResponse = Invoke-RestMethod -Uri "$baseUrl/users/$userId" -Method Get
    Write-Host "✓ SUCCESS" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Gray
    $userResponse | ConvertTo-Json -Depth 10 | Write-Host -ForegroundColor White
    
} catch {
    Write-Host "✗ FAILED" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails.Message) {
        Write-Host "Details: $($_.ErrorDetails.Message)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan

# Test 3: Register Duplicate Email (Should Fail)
Write-Host ""
Write-Host "Test 3: Register Duplicate Email (Should Fail)" -ForegroundColor Yellow
Write-Host "Endpoint: POST $baseUrl/users" -ForegroundColor Gray

$duplicateRequest = @{
    phoneNumber = "0999888777"
    email = "john.doe@example.com"  # Same email as Test 1
    password = "password456"
    displayName = "Duplicate User"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/users" -Method Post -Body $duplicateRequest -ContentType "application/json"
    Write-Host "✗ UNEXPECTED SUCCESS (Should have failed)" -ForegroundColor Red
} catch {
    Write-Host "✓ EXPECTED FAILURE" -ForegroundColor Green
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan

# Test 4: Register Another Valid User
Write-Host ""
Write-Host "Test 4: Register Another Valid User" -ForegroundColor Yellow

$registerRequest2 = @{
    phoneNumber = "0987654321"
    email = "jane.smith@example.com"
    password = "securepass456"
    displayName = "Jane Smith"
    firstName = "Jane"
    lastName = "Smith"
} | ConvertTo-Json

try {
    $response2 = Invoke-RestMethod -Uri "$baseUrl/users" -Method Post -Body $registerRequest2 -ContentType "application/json"
    Write-Host "✓ SUCCESS" -ForegroundColor Green
    Write-Host "User ID: $($response2.user_id)" -ForegroundColor Green
} catch {
    Write-Host "✗ FAILED" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "All Tests Completed!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
