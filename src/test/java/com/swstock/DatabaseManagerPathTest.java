package com.swstock;

import com.swstock.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerPathTest {

    private String originalProp;

    @BeforeEach
    void setUp() {
        originalProp = System.getProperty("swstock.db.path");
    }

    @AfterEach
    void tearDown() {
        if (originalProp != null) {
            System.setProperty("swstock.db.path", originalProp);
        } else {
            System.clearProperty("swstock.db.path");
        }
    }

    @Test
    void testCustomSystemPropertyPath() {
        String testCustomPath = "target/custom_test_swstock.db";
        System.setProperty("swstock.db.path", testCustomPath);

        String resolved = DatabaseManager.resolveDefaultDatabasePath();
        assertEquals(testCustomPath, resolved);
    }

    @Test
    void testResolvedPathIsNotNullAndEndsWithDb() {
        System.clearProperty("swstock.db.path");
        String resolved = DatabaseManager.resolveDefaultDatabasePath();
        assertNotNull(resolved);
        assertTrue(resolved.endsWith("swstock.db"), "Caminho do banco deve terminar com swstock.db");
    }

    @Test
    void testGetDbPathExtraction() {
        DatabaseManager dm = DatabaseManager.getInstance("target/sample_db_test.db");
        assertNotNull(dm.getDbUrl());
        assertTrue(dm.getDbUrl().startsWith("jdbc:sqlite:"));
        assertEquals("target/sample_db_test.db", dm.getDbPath());
    }
}
