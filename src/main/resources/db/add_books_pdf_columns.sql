-- Chạy script này trong SSMS (database demo_login_db) nếu bảng books chưa có cột PDF
USE demo_login_db;
GO

IF COL_LENGTH('dbo.books', 'pdf_path') IS NULL
BEGIN
    ALTER TABLE dbo.books ADD pdf_path NVARCHAR(500) NULL;
END
GO

IF COL_LENGTH('dbo.books', 'pdf_name') IS NULL
BEGIN
    ALTER TABLE dbo.books ADD pdf_name NVARCHAR(255) NULL;
END
GO
