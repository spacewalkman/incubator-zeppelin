/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `note` (
  `id` varchar(100) NOT NULL,
  `createdBy` varchar(255) DEFAULT NULL,
  `projectId` varchar(255) DEFAULT NULL,
  `team` varchar(255) DEFAULT NULL,
  `content` longtext,
  `last_updated_time` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `note_revision` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `noteId` varchar(255) NOT NULL,
  `committer` varchar(255) NOT NULL,
  `team` varchar(255) DEFAULT NULL,
  `projectId` varchar(255) DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  `commit_date` datetime NOT NULL,
  `content` longtext,
  `note_name` varchar(255) NOT NULL,
  `sha1` varchar(255) NOT NULL,
  `is_submit` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `role_permission` (
  `role_name` varchar(255) NOT NULL,
  `permission` varchar(255) DEFAULT NULL,
  UNIQUE KEY `role_permission_unique` (`role_name`,`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user` (
  `user_name` varchar(255) NOT NULL,
  `password` text NOT NULL,
  UNIQUE KEY `user_name` (`user_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_role` (
  `user_name` varchar(255) NOT NULL,
  `role_name` varchar(255) NOT NULL,
  UNIQUE KEY `user_role_uniqe` (`user_name`,`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;
