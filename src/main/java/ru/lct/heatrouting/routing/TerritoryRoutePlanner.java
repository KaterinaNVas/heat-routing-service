package ru.lct.heatrouting.routing;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/** Первый 2D-поиск: граф видимости вокруг непроходимых ограничений в EPSG:32637. */
@Component
public class TerritoryRoutePlanner {
    private static final int MAX_VERTICES = 1600;
    private static final double PORTAL_MARGIN = 0.05;
    private static final double BUFFER_MARGIN = 0.10;
    private final GeometryFactory factory = new GeometryFactory();

    public Optional<Route> find(InputDataset metricDataset, ConnectionPoint oks, int diameter) {
        if (metricDataset == null || oks == null || oks.getGeometry() == null ||
                oks.getGeometry().getSRID() != 32637) {
            throw new IllegalArgumentException("Ожидается входной набор в EPSG:32637");
        }
        if (diameter <= 0) throw new IllegalArgumentException("ДУ должен быть положительным");
        if (metricDataset.getHeatNetwork().isEmpty()) return Optional.empty();
        double halfWidth = width(diameter) / 2;
        List<Geometry> barriers = new ArrayList<>();
        List<Restriction> barrierOwners = new ArrayList<>();
        Restriction ownOks = null;
        for (Restriction restriction : metricDataset.getRestrictions()) {
            Geometry geometry = restriction.getGeometry();
            if (geometry == null || geometry.isEmpty()) continue;
            if (restriction.getRestrictionType() == RestrictionType.OKS &&
                    geometry.covers(oks.getGeometry())) {
                if (ownOks == null || geometry.getBoundary().distance(oks.getGeometry()) <
                        ownOks.getGeometry().getBoundary().distance(oks.getGeometry())) {
                    ownOks = restriction;
                }
            }
            barriers.add(geometry.buffer(clearance(restriction.getRestrictionType(), diameter)
                    + halfWidth + BUFFER_MARGIN, 16));
            barrierOwners.add(restriction);
        }
        Point start = oks.getGeometry();
        if (ownOks != null) {
            Geometry expanded = ownOks.getGeometry().buffer(clearance(RestrictionType.OKS, diameter)
                    + halfWidth + BUFFER_MARGIN + PORTAL_MARGIN, 16);
            Coordinate nearest = DistanceOp.nearestPoints(ownOks.getGeometry().getBoundary(), start)[0];
            double dx = nearest.x - start.getX();
            double dy = nearest.y - start.getY();
            double norm = Math.hypot(dx, dy);
            if (norm < 1e-8) return Optional.empty();
            // Двигаемся по прямой через ближайшую границу до внешней стороны зоны отступа.
            double low = norm;
            double high = norm + 2 * (clearance(RestrictionType.OKS, diameter) + halfWidth + 1);
            for (int i = 0; i < 45; i++) {
                double middle = (low + high) / 2;
                Point p = point(start.getX() + middle * dx / norm,
                        start.getY() + middle * dy / norm);
                if (expanded.covers(p)) low = middle; else high = middle;
            }
            Point exit = point(start.getX() + high * dx / norm, start.getY() + high * dy / norm);
            List<Geometry> otherBarriers = new ArrayList<>();
            for (int i = 0; i < barriers.size(); i++) {
                if (barrierOwners.get(i) != ownOks) otherBarriers.add(barriers.get(i));
            }
            if (!visible(start, exit, otherBarriers)) {
                return Optional.empty();
            }
            start = exit;
        }
        List<Point> vertices = new ArrayList<>();
        vertices.add(start);
        for (Geometry barrier : barriers) {
            for (Coordinate c : barrier.getBoundary().getCoordinates()) {
                if (vertices.size() >= MAX_VERTICES) return Optional.empty();
                vertices.add(point(c.x, c.y));
            }
        }
        // Врезка выбирается на ближайшей проекции точки ОКС на каждый существующий участок.
        List<HeatNetworkSegment> segments = metricDataset.getHeatNetwork();
        List<Integer> targetIndices = new ArrayList<>();
        List<HeatNetworkSegment> targets = new ArrayList<>();
        for (HeatNetworkSegment segment : segments) {
            Coordinate c = DistanceOp.nearestPoints(segment.getGeometry(), oks.getGeometry())[0];
            for (Coordinate candidate : new Coordinate[]{c,
                    segment.getGeometry().getStartPoint().getCoordinate(),
                    segment.getGeometry().getEndPoint().getCoordinate()}) {
                targetIndices.add(vertices.size());
                targets.add(segment);
                vertices.add(point(candidate.x, candidate.y));
            }
        }
        int count = vertices.size();
        double[] distances = new double[count];
        int[] previous = new int[count];
        java.util.Arrays.fill(distances, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(previous, -1);
        boolean[] settled = new boolean[count];
        double[] heuristic = new double[count];
        for (int i = 0; i < count; i++) {
            heuristic[i] = Double.POSITIVE_INFINITY;
            for (int target : targetIndices) {
                heuristic[i] = Math.min(heuristic[i], vertices.get(i).distance(vertices.get(target)));
            }
        }
        PriorityQueue<SearchStep> open = new PriorityQueue<>(Comparator.comparingDouble(s -> s.estimate));
        distances[0] = 0;
        open.add(new SearchStep(0, heuristic[0]));
        while (!open.isEmpty()) {
            int u = open.poll().index;
            if (settled[u]) continue;
            settled[u] = true;
            if (targetIndices.contains(u)) {
                List<Coordinate> coordinates = new ArrayList<>();
                for (int v = u; v != -1; v = previous[v]) coordinates.add(vertices.get(v).getCoordinate());
                java.util.Collections.reverse(coordinates);
                if (ownOks != null) coordinates.add(0, oks.getGeometry().getCoordinate());
                int segmentIndex = targetIndices.indexOf(u);
                LineString line = factory.createLineString(coordinates.toArray(new Coordinate[0]));
                line.setSRID(32637);
                return Optional.of(new Route(line, targets.get(segmentIndex).getId(), vertices.get(u)));
            }
            for (int v = 0; v < count; v++) {
                if (u == v || settled[v] || !visible(vertices.get(u), vertices.get(v), barriers)) continue;
                double alternative = distances[u] + vertices.get(u).distance(vertices.get(v));
                if (alternative < distances[v]) {
                    distances[v] = alternative;
                    previous[v] = u;
                    open.add(new SearchStep(v, alternative + heuristic[v]));
                }
            }
        }
        return Optional.empty();
    }

    private boolean visible(Point a, Point b, List<Geometry> buffered) {
        if (a.equalsExact(b)) return false;
        LineString line = factory.createLineString(new Coordinate[]{a.getCoordinate(), b.getCoordinate()});
        for (Geometry obstacle : buffered) {
            if (line.relate(obstacle, "T********")) return false;
        }
        return true;
    }

    private Point point(double x, double y) {
        Point p = factory.createPoint(new Coordinate(x, y));
        p.setSRID(32637);
        return p;
    }

    private double clearance(RestrictionType type, int diameter) {
        if (type == RestrictionType.OKS) return diameter < 500 ? 5 : diameter < 900 ? 7 : 9;
        if (type == RestrictionType.ROAD || type == RestrictionType.TRAM_TRACKS) return 1.5;
        if (type == RestrictionType.GAS_PIPELINE || type == RestrictionType.POWER_CABLE) return 2;
        return 1;
    }

    private double width(int diameter) {
        // Таблица 1; проход с меньшим габаритом здесь недопустим.
        int[] dn = {50,65,80,100,125,150,200,250,300,400,500,600,700,800,900,1000,1200,1400};
        double[] widths = {.4,.43,.47,.51,.6,.65,.88,1.05,1.15,1.37,1.67,1.85,2.05,2.25,2.45,2.65,3.1,3.45};
        for (int i = 0; i < dn.length; i++) if (dn[i] == diameter) return widths[i];
        throw new IllegalArgumentException("Неизвестный ДУ: " + diameter);
    }

    private static final class SearchStep {
        private final int index;
        private final double estimate;
        private SearchStep(int index, double estimate) {
            this.index = index;
            this.estimate = estimate;
        }
    }

    public static final class Route {
        private final LineString geometry;
        private final String existingSegmentId;
        private final Point connectionPoint;

        private Route(LineString geometry, String existingSegmentId, Point connectionPoint) {
            this.geometry = geometry;
            this.existingSegmentId = existingSegmentId;
            this.connectionPoint = connectionPoint;
        }
        public LineString getGeometry() { return geometry; }
        public String getExistingSegmentId() { return existingSegmentId; }
        public Point getConnectionPoint() { return connectionPoint; }
    }
}
