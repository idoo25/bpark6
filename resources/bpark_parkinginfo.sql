-- MySQL dump 10.13  Distrib 8.0.41, for Win64 (x86_64)
--
-- Host: localhost    Database: bpark
-- ------------------------------------------------------
-- Server version	8.0.41

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `parkinginfo`
--

DROP TABLE IF EXISTS `parkinginfo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `parkinginfo` (
  `ParkingInfo_ID` int NOT NULL AUTO_INCREMENT,
  `ParkingSpot_ID` int NOT NULL,
  `User_ID` int NOT NULL,
  `Date_Of_Placing_Order` datetime DEFAULT NULL,
  `Actual_start_time` datetime DEFAULT NULL,
  `Actual_end_time` datetime DEFAULT NULL,
  `Estimated_start_time` datetime DEFAULT NULL,
  `Estimated_end_time` datetime DEFAULT NULL,
  `IsOrderedEnum` enum('yes','no') NOT NULL DEFAULT 'no',
  `IsLate` enum('yes','no') NOT NULL DEFAULT 'no',
  `IsExtended` enum('yes','no') NOT NULL DEFAULT 'no',
  `statusEnum` enum('preorder','active','finished','cancelled') NOT NULL,
  PRIMARY KEY (`ParkingInfo_ID`),
  KEY `ParkingSpot_ID` (`ParkingSpot_ID`),
  KEY `idx_user_id` (`User_ID`),
  KEY `idx_status` (`statusEnum`),
  KEY `idx_estimated_start` (`Estimated_start_time`),
  KEY `idx_actual_start` (`Actual_start_time`),
  CONSTRAINT `parkinginfo_ibfk_1` FOREIGN KEY (`User_ID`) REFERENCES `users` (`User_ID`),
  CONSTRAINT `parkinginfo_ibfk_2` FOREIGN KEY (`ParkingSpot_ID`) REFERENCES `parkingspot` (`ParkingSpot_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `parkinginfo`
--

LOCK TABLES `parkinginfo` WRITE;
/*!40000 ALTER TABLE `parkinginfo` DISABLE KEYS */;
INSERT INTO `parkinginfo` VALUES (1,1,7,'2025-06-18 00:00:00',NULL,NULL,'2025-06-20 18:15:00','2025-06-20 22:15:00','yes','no','no','preorder'),(2,1,7,'2025-06-18 00:00:00',NULL,NULL,'2025-06-20 10:30:00','2025-06-20 14:30:00','yes','no','no','preorder'),(3,1,7,'2025-06-18 00:00:00',NULL,NULL,'2025-06-24 07:00:00','2025-06-24 11:00:00','yes','no','no','preorder'),(4,1,1,'2025-06-15 00:00:00',NULL,NULL,'2025-06-18 20:35:00','2025-06-18 23:59:00','yes','no','no','cancelled'),(5,2,7,'2025-06-15 00:00:00',NULL,NULL,'2025-06-18 20:50:00','2025-06-18 23:59:00','yes','no','no','cancelled'),(6,1,7,'2025-06-19 00:42:29','2025-06-19 00:42:29',NULL,'2025-06-19 00:42:29','2025-06-19 04:42:29','no','no','no','active'),(7,2,7,'2025-06-19 00:42:33','2025-06-19 00:42:34','2025-06-19 00:55:26','2025-06-19 00:42:34','2025-06-19 04:42:34','no','no','no','finished'),(8,3,7,'2025-06-19 00:42:35','2025-06-19 00:42:36','2025-06-19 00:44:14','2025-06-19 00:42:36','2025-06-19 04:42:36','no','no','no','finished'),(9,3,7,'2025-06-19 00:44:44',NULL,NULL,'2025-06-23 06:15:00','2025-06-23 10:15:00','yes','no','no','preorder'),(10,3,7,'2025-06-19 00:53:07','2025-06-19 00:53:07','2025-06-19 00:54:37','2025-06-19 00:53:07','2025-06-19 04:53:07','no','no','no','finished');
/*!40000 ALTER TABLE `parkinginfo` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-06-19 11:15:49
