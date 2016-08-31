CREATE TABLE `user` (
  `user_name` varchar(255) UNIQUE NOT NULL,
  `password` text NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `user_role` (
  `user_name` varchar(255) NOT NULL,
  `role_name` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `role_permission` (
  `role_name` varchar(255) NOT NULL,
  `permission` text
) ENGINE=InnoDB DEFAULT CHARSET=utf8;