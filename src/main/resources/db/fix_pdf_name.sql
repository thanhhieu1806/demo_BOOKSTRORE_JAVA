-- Cập nhật pdf_name cho sách đã có pdf_path (chạy 1 lần trong SSMS)
USE demo_login_db;
GO

UPDATE dbo.books
SET pdf_name = RIGHT(REPLACE(pdf_path, '\', '/'),
    CHARINDEX('/', REVERSE(REPLACE(pdf_path, '\', '/'))) - 1)
WHERE pdf_path IS NOT NULL
  AND pdf_path <> ''
  AND (pdf_name IS NULL OR pdf_name = '')
  AND CHARINDEX('/', REVERSE(REPLACE(pdf_path, '\', '/'))) > 1;
GO
