/*
 * Copyright 2010 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 *distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you maynot use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicablelaw or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.end2end;

import static org.apache.phoenix.util.TestUtil.TEST_PROPERTIES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import org.apache.phoenix.query.QueryServices;
import org.apache.phoenix.util.PropertiesUtil;
import org.apache.phoenix.util.ReadOnlyProps;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.Maps;

public class QueryWithOffset extends BaseOwnClusterHBaseManagedTimeIT {
    private static String tableName = "T";
    private static String[] strings = { "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
            "q", "r", "s", "t", "u", "v", "w", "x", "y", "z" };
    private static String ddl = "CREATE TABLE " + tableName + " (t_id VARCHAR NOT NULL,\n" + "k1 INTEGER NOT NULL,\n"
            + "k2 INTEGER NOT NULL,\n" + "C3.k3 INTEGER,\n" + "C2.v1 VARCHAR,\n"
            + "CONSTRAINT pk PRIMARY KEY (t_id, k1, k2)) split on ('e','i','o')";

    @BeforeClass
    public static void doSetup() throws Exception {
        Map<String, String> props = Maps.newHashMapWithExpectedSize(1);
        // Must update config before starting server
        setUpTestDriver(new ReadOnlyProps(props.entrySet().iterator()));
    }

    @Test
    public void testLimitOffset() throws SQLException {
        Connection conn;
        Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
        conn = DriverManager.getConnection(getUrl(), props);
        createTestTable(getUrl(), ddl);
        initTableValues(conn);
        int limit = 10;
        int offset = 10;
        updateStatistics(conn);
        ResultSet rs;
        rs = conn.createStatement()
                .executeQuery("SELECT t_id from " + tableName + " order by t_id limit " + limit + " offset " + offset);
        int i = 0;
        while (i++ < limit) {
            assertTrue(rs.next());
            assertEquals(strings[offset + i - 1], rs.getString(1));
        }

        limit = 35;
        rs = conn.createStatement().executeQuery("SELECT t_id from " + tableName + " union all SELECT t_id from "
                + tableName + " offset " + offset + " FETCH FIRST " + limit + " rows only");
        i = 0;
        while (i++ < strings.length - offset) {
            assertTrue(rs.next());
            assertEquals(strings[offset + i - 1], rs.getString(1));
        }
        i = 0;
        while (i++ < limit - strings.length - offset) {
            assertTrue(rs.next());
            assertEquals(strings[i - 1], rs.getString(1));
        }
        conn.close();
    }

    @Test
    public void testOffsetSerialQueryExecutedOnServer() throws SQLException {
        Connection conn;
        Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
        conn = DriverManager.getConnection(getUrl(), props);
        int offset = 10;
        createTestTable(getUrl(), ddl);
        initTableValues(conn);
        updateStatistics(conn);
        String query = "SELECT t_id from " + tableName + " offset " + offset;
        ResultSet rs = conn.createStatement().executeQuery("EXPLAIN " + query);
        rs.next();
        rs.next();
        rs.next();
        assertEquals("    SERVER OFFSET " + offset, rs.getString(1));
        rs = conn.createStatement().executeQuery(query);
        int i = 0;
        while (i++ < strings.length - offset) {
            assertTrue(rs.next());
            assertEquals(strings[offset + i - 1], rs.getString(1));
        }
        conn.close();
    }

    @Test
    public void testOffsetWithoutLimit() throws SQLException {
        Connection conn;
        Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
        conn = DriverManager.getConnection(getUrl(), props);
        int offset = 10;
        createTestTable(getUrl(), ddl);
        initTableValues(conn);
        updateStatistics(conn);
        ResultSet rs;
        rs = conn.createStatement()
                .executeQuery("SELECT t_id from " + tableName + " order by t_id offset " + offset + " row");
        int i = 0;
        while (i++ < strings.length - offset) {
            assertTrue(rs.next());
            assertEquals(strings[offset + i - 1], rs.getString(1));
        }

        rs = conn.createStatement().executeQuery(
                "SELECT t_id,count(*) from " + tableName + " group by t_id order by t_id offset " + offset + " row");

        i = 0;
        while (i++ < strings.length - offset) {
            assertTrue(rs.next());
            assertEquals(strings[offset + i - 1], rs.getString(1));
        }

        rs = conn.createStatement().executeQuery("SELECT t_id from " + tableName + " union all SELECT t_id from "
                + tableName + " offset " + offset + " rows");
        i = 0;
        while (i++ < strings.length - offset) {
            assertTrue(rs.next());
            assertEquals(strings[offset + i - 1], rs.getString(1));
        }
        i = 0;
        while (i++ < strings.length) {
            assertTrue(rs.next());
            assertEquals(strings[i - 1], rs.getString(1));
        }
        conn.close();
    }

    private static void initTableValues(Connection conn) throws SQLException {
        for (int i = 0; i < 26; i++) {
            conn.createStatement().execute("UPSERT INTO " + tableName + " values('" + strings[i] + "'," + i + ","
                    + (i + 1) + "," + (i + 2) + ",'" + strings[25 - i] + "')");
        }
        conn.commit();
    }

    private static void updateStatistics(Connection conn) throws SQLException {
        String query = "UPDATE STATISTICS " + tableName + " SET \"" + QueryServices.STATS_GUIDEPOST_WIDTH_BYTES_ATTRIB
                + "\"=" + Long.toString(100);
        conn.createStatement().execute(query);
    }
}
