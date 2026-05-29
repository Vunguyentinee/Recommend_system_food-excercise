# Test Endpoints Script

This folder contains a PowerShell script to quickly test the API endpoints.

## Prerequisites

- Spring Boot app running at http://localhost:8080
- The user ID used in the script must exist in dbo.Users (SQL Server)

## Run

```powershell
PowerShell -ExecutionPolicy Bypass -File .\tools\test_endpoints.ps1 -BaseUrl "http://localhost:8080" -UserId 31 -TopK 5
```

## Notes

- Change `-UserId` to a real user in your database.
- If `rate-food` fails with a FK error, the user ID is not in dbo.Users.

