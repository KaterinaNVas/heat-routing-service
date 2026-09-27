package ru.lct.heatrouting.network;

import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Groups proposed connections that lead to the same physical tie-in. */
@Component
public class OksConnectionGrouper {
    private static final double TOLERANCE_METERS = 0.01;

    public List<List<Candidate>> group(List<Candidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        List<List<Candidate>> groups = new ArrayList<>();
        Set<String> seenOks = new HashSet<>();
        for (Candidate candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            if (!seenOks.add(candidate.getOksId())) {
                throw new IllegalArgumentException("ОКС повторяется: " + candidate.getOksId());
            }
            boolean placed = false;
            for (List<Candidate> group : groups) {
                if (sameTieIn(group.get(0), candidate)) {
                    group.add(candidate);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                List<Candidate> group = new ArrayList<>();
                group.add(candidate);
                groups.add(group);
            }
        }
        List<List<Candidate>> result = new ArrayList<>();
        for (List<Candidate> group : groups) {
            result.add(List.copyOf(group));
        }
        return List.copyOf(result);
    }

    private boolean sameTieIn(Candidate a, Candidate b) {
        if (a.existingChamber != b.existingChamber
                || a.point.distance(b.point) > TOLERANCE_METERS) {
            return false;
        }
        if (a.existingChamber) {
            // A chamber can be incident to more than one existing segment.
            return a.chamberId.equals(b.chamberId);
        }
        // Newly proposed chamber IDs are often placeholders reused for every OKS.
        return a.existingSegmentId.equals(b.existingSegmentId);
    }

    public static final class Candidate {
        private final String oksId;
        private final boolean existingChamber;
        private final String chamberId;
        private final String existingSegmentId;
        private final Point point;

        public Candidate(String oksId, ConnectionResolver.Connection connection) {
            this(oksId, Objects.requireNonNull(connection, "connection").isExistingChamber(),
                    connection.getChamberId(), connection.getExistingSegmentId(), connection.getPoint());
        }

        public Candidate(String oksId, boolean existingChamber, String chamberId,
                         String existingSegmentId, Point point) {
            if (oksId == null || oksId.isBlank() || chamberId == null || chamberId.isBlank()
                    || existingSegmentId == null || existingSegmentId.isBlank()
                    || point == null || point.isEmpty() || point.getSRID() != 32637) {
                throw new IllegalArgumentException("Неверные данные точки присоединения");
            }
            this.oksId = oksId;
            this.existingChamber = existingChamber;
            this.chamberId = chamberId;
            this.existingSegmentId = existingSegmentId;
            this.point = point;
        }

        public String getOksId() { return oksId; }
        public boolean isExistingChamber() { return existingChamber; }
        public String getChamberId() { return chamberId; }
        public String getExistingSegmentId() { return existingSegmentId; }
        public Point getPoint() { return point; }
    }
}
