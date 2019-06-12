package org.opentripplanner.model.impl;

import org.junit.Test;
import org.opentripplanner.model.IdentityBean;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EntityByIdTest {
    private static final String ID = "99";

    private static final EntityByIdTest.E E = new E(ID);
    private static final String E_TO_STRING = E.toString();
    private static final String LIST_OF_E_TO_STRING = String.format("[%s]", E_TO_STRING);
    private static final String MAP_OF_E_TO_STRING = String.format("{%s=%s}", ID, E_TO_STRING);

    private EntityById<String, E> subject = new EntityById<>();


    @Test public void add() {
        subject.add(E);
        assertEquals(MAP_OF_E_TO_STRING, subject.toString());
    }

    @Test public void addAll() {
        subject.addAll(Collections.emptyList());
        assertEquals("{}", subject.toString());
        subject.addAll(Collections.singletonList(E));
        assertEquals(MAP_OF_E_TO_STRING, subject.toString());
    }

    @Test public void values() {
        subject.add(E);
        assertEquals(LIST_OF_E_TO_STRING, subject.values().toString());
    }

    @Test public void get() {
        subject.add(E);
        assertEquals(E_TO_STRING, subject.get(ID).toString());
    }

    @Test public void asImmutableMap() {
        subject.add(E);
        assertEquals(E_TO_STRING, subject.get(ID).toString());
    }

    @Test public void containsKey() {
        assertFalse(subject.containsKey(ID));
        subject.add(E);
        assertTrue(subject.containsKey(ID));
        assertFalse(subject.containsKey("Fake id"));
    }

    @Test public void reindex() {
        // Given a map with an entity and an original id
        String originalId = "OriginalId";
        EntityByIdTest.E e = new E(originalId);
        subject.add(e);

        // Verify element exist
        assertTrue(subject.containsKey(originalId));

        // When the elements id is changed
        e.setId("NewId");

        // Then the elements is not indexed by its id any more
        assertFalse(subject.containsKey(e.id));

        // Hence; use reindex to fix it
        subject.reindex();

        // then the map should be indexed by its id again
        assertTrue(subject.containsKey(e.id));
    }


    static class E extends IdentityBean<String> {
        private String id;

        E(String id) {
            setId(id);
        }

        @Override public String getId() {
            return id;
        }

        @Override public void setId(String id) {
            this.id = id;
        }

        @Override public String toString() {
            return "E-" + id;
        }
    }
}