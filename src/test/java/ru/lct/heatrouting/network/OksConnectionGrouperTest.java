package ru.lct.heatrouting.network;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OksConnectionGrouperTest {
    private final GeometryFactory factory = new GeometryFactory();
    private final OksConnectionGrouper grouper = new OksConnectionGrouper();

    private Point point(double x, double y) {
        Point point = factory.createPoint(new Coordinate(x, y));
        point.setSRID(32637);
        return point;
    }

    private OksConnectionGrouper.Candidate existing(String oks, String chamber,
                                                    String segment, double x) {
        return new OksConnectionGrouper.Candidate(oks, true, chamber, segment, point(x, 0));
    }

    private OksConnectionGrouper.Candidate proposed(String oks, String segment, double x) {
        return new OksConnectionGrouper.Candidate(
                oks, false, "v1_chamber_1", segment, point(x, 0));
    }

    @Test
    void groupsSameExistingChamberAcrossIncidentSegments() {
        List<List<OksConnectionGrouper.Candidate>> groups = grouper.group(List.of(
                existing("1", "106", "pipe-a", 0),
                existing("2", "106", "pipe-b", 0.005)));
        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).size());
    }

    @Test
    void doesNotGroupReusedTemporaryChamberIdAtDifferentLocations() {
        List<List<OksConnectionGrouper.Candidate>> groups = grouper.group(List.of(
                proposed("4", "pipe-a", 0), proposed("7", "pipe-a", 20)));
        assertEquals(2, groups.size());
    }

    @Test
    void groupsMatchingNewTieInButNotDifferentSegments() {
        List<List<OksConnectionGrouper.Candidate>> groups = grouper.group(List.of(
                proposed("4", "pipe-a", 0), proposed("7", "pipe-a", 0.005),
                proposed("10", "pipe-b", 0)));
        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).size());
    }

    @Test
    void rejectsDuplicateOks() {
        assertThrows(IllegalArgumentException.class, () -> grouper.group(List.of(
                existing("1", "106", "pipe-a", 0),
                existing("1", "108", "pipe-b", 30))));
    }
}
