CREATE DATABASE IF NOT EXISTS smart_cafeteria CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'cafe_app'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL ON smart_cafeteria.* TO 'cafe_app'@'localhost';
FLUSH PRIVILEGES;
