package ru.lct.heatrouting.network;

import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.HeatChamber;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.util.Comparator;
import java.util.Objects;

/** Выбор камеры в месте присоединения согласно разделу 2.4 ТЗ. */
@Component
public class ConnectionResolver {
    private static final double MAX_DISTANCE_METERS = 10.0;
    private static final double TOPOLOGY_TOLERANCE_METERS = 0.01;

    public Connection resolve(InputDataset dataset, TerritoryRoutePlanner.Route route,
                              String newChamberId) {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(route, "route");
        Point chosen = Objects.requireNonNull(route.getConnectionPoint(), "connectionPoint");
        if (chosen.getSRID() != 32637) {
            throw new IllegalArgumentException("Расчёт присоединения требует EPSG:32637");
        }
        HeatNetworkSegment selected = dataset.getHeatNetwork().stream()
                .filter(s -> Objects.equals(s.getId(), route.getExistingSegmentId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Участок существующей сети не найден: " + route.getExistingSegmentId()));
        if (selected.getGeometry().distance(chosen) > TOPOLOGY_TOLERANCE_METERS) {
            throw new IllegalArgumentException("Точка присоединения не лежит на выбранном участке");
        }
        return dataset.getHeatChambers().stream()
                .filter(c -> c.getGeometry() != null && c.getGeometry().distance(chosen) <= MAX_DISTANCE_METERS)
                .filter(c -> dataset.getHeatNetwork().stream().anyMatch(s ->
                        s.getGeometry().distance(c.getGeometry()) <= TOPOLOGY_TOLERANCE_METERS))
                .filter(c -> incidentExistingSegments(dataset, c.getGeometry()) + 1 <= 4)
                .min(Comparator.comparingDouble(c -> c.getGeometry().distance(chosen)))
                .map(c -> new Connection(c.getId(), c.getGeometry(), true, route.getExistingSegmentId()))
                .orElseGet(() -> new Connection(
                        Objects.requireNonNull(newChamberId, "newChamberId"),
                        chosen, false, route.getExistingSegmentId()));
    }

    private int incidentExistingSegments(InputDataset dataset, Point chamber) {
        int count = 0;
        for (HeatNetworkSegment segment : dataset.getHeatNetwork()) {
            if (segment.getGeometry().distance(chamber) > TOPOLOGY_TOLERANCE_METERS) continue;
            boolean start = segment.getGeometry().getStartPoint().distance(chamber) <= TOPOLOGY_TOLERANCE_METERS;
            boolean end = segment.getGeometry().getEndPoint().distance(chamber) <= TOPOLOGY_TOLERANCE_METERS;
            count += start || end ? 1 : 2;
        }
        return count;
    }

    public static final class Connection {
        private final String chamberId;
        private final Point point;
        private final boolean existingChamber;
        private final String existingSegmentId;

        private Connection(String chamberId, Point point, boolean existingChamber,
                           String existingSegmentId) {
            this.chamberId = chamberId;
            this.point = point;
            this.existingChamber = existingChamber;
            this.existingSegmentId = existingSegmentId;
        }
        public String getChamberId() { return chamberId; }
        public Point getPoint() { return point; }
        public boolean isExistingChamber() { return existingChamber; }
        public String getExistingSegmentId() { return existingSegmentId; }
    }
}
