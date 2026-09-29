package ru.lct.heatrouting.routing;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.geo.RouteAngleValidator;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/** Первый 2D-поиск: граф видимости вокруг непроходимых ограничений в EPSG:32637. */
@Component
public class TerritoryRoutePlanner {
    private static final int[] VERTEX_BUDGETS = {400, 800, 1600};
    private static final double PORTAL_MARGIN = 0.05;
    private static final double BUFFER_MARGIN = 0.10;
    private static final double MIN_SEARCH_RADIUS_METERS = 100;
    private final GeometryFactory factory = new GeometryFactory();
    private final RouteAngleValidator angleValidator = new RouteAngleValidator();

    public Optional<Route> find(InputDataset metricDataset, ConnectionPoint oks, int diameter) {
        List<Route> routes = findInternal(metricDataset, oks, diameter, null, null, 1);
        return routes.isEmpty() ? Optional.empty() : Optional.of(routes.get(0));
    }

    /** Shortest feasible routes to distinct existing network segments. */
    public List<Route> findAlternatives(InputDataset metricDataset, ConnectionPoint oks,
                                        int diameter, int limit) {
        if (limit < 1) throw new IllegalArgumentException("Число вариантов должно быть положительным");
        return List.copyOf(findInternal(metricDataset, oks, diameter, null, null, limit));
    }

    /** Перестраивает путь к выбранной существующей камере на указанном участке. */
    public Optional<Route> findTo(InputDataset metricDataset, ConnectionPoint oks, int diameter,
                                  Point chamber, String existingSegmentId) {
        if (chamber == null || existingSegmentId == null || chamber.getSRID() != 32637) {
            throw new IllegalArgumentException("Камера и существующий участок обязательны");
        }
        List<Route> routes = findInternal(metricDataset, oks, diameter, chamber, existingSegmentId, 1);
        return routes.isEmpty() ? Optional.empty() : Optional.of(routes.get(0));
    }

    private List<Route> findInternal(InputDataset metricDataset, ConnectionPoint oks,
                                     int diameter, Point forcedTarget,
                                     String forcedSegmentId, int limit) {
        if (metricDataset == null || oks == null || oks.getGeometry() == null ||
                oks.getGeometry().getSRID() != 32637) {
            throw new IllegalArgumentException("Ожидается входной набор в EPSG:32637");
        }
        if (diameter <= 0) throw new IllegalArgumentException("ДУ должен быть положительным");
        if (metricDataset.getHeatNetwork().isEmpty()) return List.of();
        Point oksPoint = oks.getGeometry();
        double nearestNetwork = metricDataset.getHeatNetwork().stream()
                .mapToDouble(s -> s.getGeometry().distance(oksPoint))
                .min().orElse(Double.POSITIVE_INFINITY);
        double targetDistance = forcedTarget == null ? nearestNetwork : oksPoint.distance(forcedTarget);
        double searchRadius = Math.max(MIN_SEARCH_RADIUS_METERS, targetDistance + 25);
        Geometry searchZone = oksPoint.buffer(searchRadius, 16);
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
            double offset = clearance(restriction.getRestrictionType(), diameter)
                    + halfWidth + BUFFER_MARGIN;
            if (geometry.distance(oksPoint) > searchRadius + offset) continue;
            Geometry localBarrier = geometry.buffer(offset, 16).intersection(searchZone);
            if (localBarrier.isEmpty()) continue;
            barriers.add(localBarrier);
            barrierOwners.add(restriction);
        }
        Point start = oks.getGeometry();
        List<Point> portals = new ArrayList<>();
        if (ownOks != null) {
            Geometry expanded = ownOks.getGeometry().buffer(clearance(RestrictionType.OKS, diameter)
                    + halfWidth + BUFFER_MARGIN + PORTAL_MARGIN, 16);
            List<Geometry> otherBarriers = new ArrayList<>();
            for (int i = 0; i < barriers.size(); i++) {
                if (barrierOwners.get(i) != ownOks) otherBarriers.add(barriers.get(i));
            }
            List<Coordinate> boundary = new ArrayList<>();
            for (Coordinate c : expanded.getBoundary().getCoordinates()) boundary.add(c);
            boundary.sort(Comparator.comparingDouble(c -> start.getCoordinate().distance(c)));
            for (Coordinate c : boundary) {
                double distance = start.getCoordinate().distance(c);
                if (distance < 1e-8 || distance > searchRadius) continue;
                double scale = (distance + 0.25) / distance;
                Point exit = point(start.getX() + (c.x - start.getX()) * scale,
                        start.getY() + (c.y - start.getY()) * scale);
                if (expanded.covers(exit) || !singleExit(start, exit, ownOks.getGeometry())
                        || !visible(start, exit, otherBarriers)) continue;
                if (portals.stream().anyMatch(p -> p.distance(exit) < 1.0)) continue;
                portals.add(exit);
                if (portals.size() == 32) break;
            }
            if (portals.isEmpty()) return List.of();
        }
        for (int vertexBudget : VERTEX_BUDGETS) {
        List<Point> vertices = routeVertices(start, barriers, vertexBudget);
        int firstPortal = vertices.size();
        vertices.addAll(portals);
        int afterPortals = vertices.size();
        // Врезка выбирается на ближайшей проекции точки ОКС на каждый существующий участок.
        List<HeatNetworkSegment> segments = metricDataset.getHeatNetwork();
        List<Integer> targetIndices = new ArrayList<>();
        List<HeatNetworkSegment> targets = new ArrayList<>();
        for (HeatNetworkSegment segment : segments) {
            if (forcedTarget != null) {
                if (segment.getId().equals(forcedSegmentId)) {
                    if (segment.getGeometry().distance(forcedTarget) > 0.01) {
                        throw new IllegalArgumentException("Камера не находится на выбранном участке");
                    }
                    targetIndices.add(vertices.size());
                    targets.add(segment);
                    vertices.add(forcedTarget);
                }
                continue;
            }
            if (segment.getGeometry().distance(oksPoint) > searchRadius) continue;
            Coordinate c = DistanceOp.nearestPoints(segment.getGeometry(), oks.getGeometry())[0];
            for (Coordinate candidate : new Coordinate[]{c,
                    segment.getGeometry().getStartPoint().getCoordinate(),
                    segment.getGeometry().getEndPoint().getCoordinate()}) {
                if (oksPoint.getCoordinate().distance(candidate) > searchRadius) continue;
                targetIndices.add(vertices.size());
                targets.add(segment);
                vertices.add(point(candidate.x, candidate.y));
            }
        }
        if (targetIndices.isEmpty()) return List.of();
        // A tie-in located inside a road/tram polygon needs a straight exit.
        // The portal is beyond the three-metre special margin and the buffered
        // obstacle, so the normal visibility graph can reach it from outside.
        Map<Integer, Integer> specialTargetPortals = new HashMap<>();
        for (int target : targetIndices) {
            Point tieIn = vertices.get(target);
            for (Restriction restriction : metricDataset.getRestrictions()) {
                RestrictionType type = restriction.getRestrictionType();
                if (type != RestrictionType.ROAD && type != RestrictionType.TRAM_TRACKS) continue;
                Geometry area = restriction.getGeometry();
                if (area == null || area.getDimension() != 2 || !area.covers(tieIn)) continue;
                Coordinate boundary = DistanceOp.nearestPoints(area.getBoundary(), tieIn)[0];
                double distance = tieIn.getCoordinate().distance(boundary);
                if (distance < 1e-7) continue;
                double offset = clearance(type, diameter) + halfWidth + BUFFER_MARGIN;
                double extension = Math.max(3.05, offset + 0.2);
                double scale = (distance + extension) / distance;
                Point portal = point(tieIn.getX() + (boundary.x - tieIn.getX()) * scale,
                        tieIn.getY() + (boundary.y - tieIn.getY()) * scale);
                if (area.buffer(offset).covers(portal)) continue;
                List<Geometry> otherBarriers = new ArrayList<>();
                for (int i = 0; i < barriers.size(); i++) {
                    if (barrierOwners.get(i) != restriction) otherBarriers.add(barriers.get(i));
                }
                if (!visible(portal, tieIn, otherBarriers)) continue;
                specialTargetPortals.put(target, vertices.size());
                vertices.add(portal);
                break;
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
        List<Route> alternatives = new ArrayList<>();
        java.util.Set<String> selectedSegments = new java.util.HashSet<>();
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
                int segmentIndex = targetIndices.indexOf(u);
                LineString line = factory.createLineString(coordinates.toArray(new Coordinate[0]));
                line.setSRID(32637);
                line = shortcutOwnOksExit(line, ownOks, barriers, barrierOwners, diameter);
                LineString construction = constructionOutsideOwnOks(line, ownOks);
                if (construction != null) {
                    String segmentId = targets.get(segmentIndex).getId();
                    if (selectedSegments.add(segmentId)) {
                        alternatives.add(new Route(line, construction, segmentId, vertices.get(u)));
                        if (alternatives.size() == limit) return alternatives;
                    }
                }
            }
            for (int v = 0; v < count; v++) {
                if (u == v || settled[v]) continue;
                if (u == 0 && ownOks != null) {
                    if (v < firstPortal || v >= afterPortals) continue;
                } else if (specialTargetPortals.containsKey(v)) {
                    if (specialTargetPortals.get(v) != u) continue;
                } else if (!visible(vertices.get(u), vertices.get(v), barriers)) continue;
                double alternative = distances[u] + vertices.get(u).distance(vertices.get(v));
                if (alternative < distances[v]) {
                    distances[v] = alternative;
                    previous[v] = u;
                    open.add(new SearchStep(v, alternative + heuristic[v]));
                }
            }
        }
        if (!alternatives.isEmpty()) return alternatives;
        }
        return List.of();
    }

    private LineString shortcutOwnOksExit(LineString line, Restriction ownOks,
                                          List<Geometry> barriers,
                                          List<Restriction> barrierOwners, int diameter) {
        if (ownOks == null || line.getNumPoints() < 3
                || angleValidator.validate(line).isValid()) return line;
        Coordinate[] points = line.getCoordinates();
        Point first = point(points[0].x, points[0].y);
        Point third = point(points[2].x, points[2].y);
        List<Geometry> otherBarriers = new ArrayList<>();
        for (int i = 0; i < barriers.size(); i++) {
            if (barrierOwners.get(i) != ownOks) otherBarriers.add(barriers.get(i));
        }
        if (!singleExit(first, third, ownOks.getGeometry())
                || !visible(first, third, otherBarriers)) return line;
        Geometry expanded = ownOks.getGeometry().buffer(
                clearance(RestrictionType.OKS, diameter)
                        + width(diameter) / 2 + BUFFER_MARGIN + PORTAL_MARGIN, 16);
        if (expanded.covers(third)) return line;
        LineString directExit = factory.createLineString(new Coordinate[]{points[0], points[2]});
        if (directExit.difference(expanded).getNumGeometries() != 1) return line;
        List<Coordinate> shortened = new ArrayList<>();
        shortened.add(points[0]);
        for (int i = 2; i < points.length; i++) shortened.add(points[i]);
        LineString candidate = factory.createLineString(shortened.toArray(new Coordinate[0]));
        candidate.setSRID(32637);
        return angleValidator.validate(candidate).isValid() ? candidate : line;
    }

    /** The first leg may leave its own footprint once, but may not enter it again. */
    private boolean singleExit(Point start, Point outside, Geometry ownFootprint) {
        LineString leg = factory.createLineString(new Coordinate[]{
                start.getCoordinate(), outside.getCoordinate()});
        Geometry inside = leg.intersection(ownFootprint);
        if (inside.isEmpty()) return true;
        if (inside instanceof Point) {
            return inside.getCoordinate().distance(start.getCoordinate()) < 0.01;
        }
        if (!(inside instanceof LineString)) return false;
        Coordinate[] coordinates = inside.getCoordinates();
        return coordinates.length >= 2
                && (coordinates[0].distance(start.getCoordinate()) < 0.01
                || coordinates[coordinates.length - 1].distance(start.getCoordinate()) < 0.01);
    }

    /** Price and export only the pipe outside the building; keep the full route for validation. */
    private LineString constructionOutsideOwnOks(LineString route, Restriction ownOks) {
        if (ownOks == null) return route;
        Coordinate[] coordinates = route.getCoordinates();
        if (coordinates.length < 2) return null;
        Point start = point(coordinates[0].x, coordinates[0].y);
        Point next = point(coordinates[1].x, coordinates[1].y);
        if (!singleExit(start, next, ownOks.getGeometry())) return null;
        LineString leg = factory.createLineString(new Coordinate[]{coordinates[0], coordinates[1]});
        Geometry inside = leg.intersection(ownOks.getGeometry());
        if (inside.isEmpty()) return route;
        Coordinate origin = coordinates[0];
        Coordinate exit = origin;
        Coordinate delta = new Coordinate(coordinates[1].x - origin.x,
                coordinates[1].y - origin.y);
        double squared = delta.x * delta.x + delta.y * delta.y;
        if (squared < 1e-12) return null;
        double furthest = 0;
        for (Coordinate candidate : inside.getCoordinates()) {
            double fraction = ((candidate.x - origin.x) * delta.x
                    + (candidate.y - origin.y) * delta.y) / squared;
            if (fraction > furthest) {
                furthest = fraction;
                exit = candidate;
            }
        }
        List<Coordinate> outside = new ArrayList<>();
        outside.add(exit.copy());
        for (int i = 1; i < coordinates.length; i++) {
            if (outside.get(outside.size() - 1).distance(coordinates[i]) > 1e-7) {
                outside.add(coordinates[i]);
            }
        }
        if (outside.size() < 2) return null;
        LineString construction = factory.createLineString(outside.toArray(new Coordinate[0]));
        construction.setSRID(32637);
        return construction.intersection(ownOks.getGeometry()).getLength() < 0.01
                ? construction : null;
    }

    /** Сокращаем только вершины графа: проверка проходимости использует точные барьеры. */
    private List<Point> routeVertices(Point start, List<Geometry> barriers, int maxVertices) {
        for (double tolerance : new double[]{0, 0.25, 0.5, 1, 2, 4, 8, 16, 32, 64}) {
            List<Point> vertices = new ArrayList<>();
            vertices.add(start);
            boolean overflow = false;
            for (Geometry barrier : barriers) {
                Geometry candidates = tolerance == 0 ? barrier.getBoundary()
                        : DouglasPeuckerSimplifier.simplify(
                                barrier.buffer(tolerance + 0.1, 8).getBoundary(), tolerance);
                for (Coordinate candidate : candidates.getCoordinates()) {
                    // Упрощённые хорды должны проходить снаружи исходной зоны.
                    Point vertex = point(candidate.x, candidate.y);
                    if (vertices.stream().noneMatch(p -> p.equalsExact(vertex))) vertices.add(vertex);
                    if (vertices.size() > maxVertices) {
                        overflow = true;
                        break;
                    }
                }
                if (overflow) break;
            }
            if (!overflow) return vertices;
        }
        throw new IllegalArgumentException("Превышен лимит геометрии в зоне поиска маршрута");
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
        private final LineString constructionGeometry;
        private final String existingSegmentId;
        private final Point connectionPoint;

        private Route(LineString geometry, LineString constructionGeometry,
                      String existingSegmentId, Point connectionPoint) {
            this.geometry = geometry;
            this.constructionGeometry = constructionGeometry;
            this.existingSegmentId = existingSegmentId;
            this.connectionPoint = connectionPoint;
        }
        // Keep the original signature for synthetic routes used by integration tests.
        private Route(LineString geometry, String existingSegmentId, Point connectionPoint) {
            this(geometry, geometry, existingSegmentId, connectionPoint);
        }
        public LineString getGeometry() { return geometry; }
        public LineString getConstructionGeometry() { return constructionGeometry; }
        public String getExistingSegmentId() { return existingSegmentId; }
        public Point getConnectionPoint() { return connectionPoint; }
    }
}
