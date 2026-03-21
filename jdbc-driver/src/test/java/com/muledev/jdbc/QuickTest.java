package com.muledev.jdbc;
import java.sql.*;
public class QuickTest {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:clouderby://clouderby-server-l2yy7y.pnwfdv.jpn-e1.cloudhub.io:443/app?secure=true";
        System.out.println("Connecting to: " + url);
        try (Connection conn = DriverManager.getConnection(url)) {
            System.out.println("Connected!");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1 as test")) {
                rs.next();
                System.out.println("Result: " + rs.getInt(1));
            }
        }
    }
}
