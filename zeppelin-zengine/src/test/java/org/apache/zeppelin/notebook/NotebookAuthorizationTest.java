package org.apache.zeppelin.notebook;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class NotebookAuthorizationTest {

    NotebookAuthorization noteAuth = NotebookAuthorization.getInstance();

    @Test
    public void testSaveToFile() {
        Set<String> entities = new HashSet<String>();
        entities.add("user1");
        entities.add("user2");
        noteAuth.setOwners("r", entities);
        noteAuth.setOwners("2A94M5J1Z", entities);
    }
}
