package com.kojaeeon.dashboard.database;

import java.io.IOException;
import java.sql.*;

/** Add another Spring bean implementing this interface to support another DB. */
public interface DatabaseConnector {
  String type();
  Connection open(QueryProfile.ConnectionSettings settings) throws SQLException, IOException;
}
