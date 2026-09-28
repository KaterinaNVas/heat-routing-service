package ru.lct.heatrouting.calculation;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.cost.DiameterCatalog;
import ru.lct.heatrouting.cost.SegmentCostCalculator;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.network.NewSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Charges Kspec only for the length within allowed crossing polygons. */
@Component
public class SpecialCrossingCostCalculator {
    private static final double EPS = 1e-7;
    private final SegmentCostCalculator prices = new SegmentCostCalculator(new DiameterCatalog());

    public double calculate(NewSegment segment, LineString route, List<Restriction> restrictions) {
        return parts(segment, route, restrictions).stream().mapToDouble(Part::getCost).sum();
    }

    /** A special pass must follow one straight line, including its margins outside an obstacle. */
    public void validateStraightSpecialPasses(LineString route, List<Restriction> restrictions) {
        if (route == null) throw new IllegalArgumentException("Маршрут не задан");
        NewSegment segment = new NewSegment("validation", 100, route.getLength());
        Coordinate first = null;
        Coordinate last = null;
        double runLength = 0;
        for (Part part : parts(segment, route, restrictions)) {
            if (part.isSpecial()) {
                LineString line = part.getGeometry();
                if (first == null) first = line.getStartPoint().getCoordinate();
                last = line.getEndPoint().getCoordinate();
                runLength += line.getLength();
            } else {
                requireStraight(first, last, runLength);
                first = null;
                last = null;
                runLength = 0;
            }
        }
        requireStraight(first, last, runLength);
    }

    private void requireStraight(Coordinate first, Coordinate last, double length) {
        if (first != null && length - first.distance(last) > 0.01)
            throw new IllegalStateException("Специальный проход должен быть прямым");
    }

    /** Physical pieces separated at every change of laying method or special coefficient. */
    public List<Part> parts(NewSegment segment, LineString route, List<Restriction> restrictions) {
        if (segment == null || route == null || restrictions == null
                || Math.abs(segment.getLength() - route.getLength()) > 0.01) {
            throw new IllegalArgumentException("Длина и геометрия нового участка должны совпадать");
        }
        LengthIndexedLine indexed = new LengthIndexedLine(route);
        List<Double> cuts = new ArrayList<>();
        List<Interval> specialIntervals = new ArrayList<>();
        cuts.add(0.0);
        cuts.add(route.getLength());
        for (Restriction restriction : restrictions) {
            if (restriction == null || !CrossingRules.hasRule(restriction.getRestrictionType())
                    || !CrossingRules.getRule(restriction.getRestrictionType()).allowed
                    || restriction.getGeometry() == null || restriction.getGeometry().isEmpty()) {
                continue;
            }
            Geometry obstacle = restriction.getGeometry();
            if (!route.getEnvelopeInternal().intersects(obstacle.getEnvelopeInternal())) continue;
            Geometry crossing = route.intersection(obstacle);
            double margin = obstacle.getDimension() == 2 ? 3.0 : 2.0;
            collectIntervals(crossing, indexed, route.getLength(), margin,
                    CrossingRules.getRule(restriction.getRestrictionType()).kSpec,
                    obstacle.getDimension() == 2, specialIntervals, cuts);
        }
        Collections.sort(cuts);
        List<Part> parts = new ArrayList<>();
        for (int i = 1; i < cuts.size(); i++) {
            double start = cuts.get(i - 1);
            double end = cuts.get(i);
            if (end - start < EPS) continue;
            double coefficient = 1.0;
            for (Interval interval : specialIntervals) {
                if (interval.start + EPS < (start + end) / 2
                        && (start + end) / 2 < interval.end - EPS)
                    coefficient = Math.max(coefficient, interval.coefficient);
            }
            double cost = prices.calculateNewSegmentCost(
                    new NewSegment(segment.getId(), segment.getDiameter(), end - start), coefficient);
            LineString geometry = (LineString) indexed.extractLine(start, end);
            geometry.setSRID(route.getSRID());
            parts.add(new Part(geometry, coefficient > 1.0 + EPS, cost));
        }
        return List.copyOf(parts);
    }

    private void collectIntervals(Geometry crossing, LengthIndexedLine indexed,
                                  double length, double margin, double coefficient,
                                  boolean polygon,
                                  List<Interval> intervals, List<Double> cuts) {
        if (crossing.isEmpty()) return;
        if (crossing instanceof GeometryCollection) {
            for (int i = 0; i < crossing.getNumGeometries(); i++) {
                collectIntervals(crossing.getGeometryN(i), indexed, length, margin,
                        coefficient, polygon, intervals, cuts);
            }
            return;
        }
        if (crossing instanceof LineString) {
            Coordinate[] vertices = crossing.getCoordinates();
            double first = indexed.project(vertices[0]);
            double last = indexed.project(vertices[vertices.length - 1]);
            addInterval(Math.max(0, Math.min(first, last) - margin),
                    Math.min(length, Math.max(first, last) + margin),
                    coefficient, intervals, cuts);
        } else if (!polygon && crossing instanceof Point) {
            double at = indexed.project(crossing.getCoordinate());
            addInterval(Math.max(0, at - margin), Math.min(length, at + margin),
                    coefficient, intervals, cuts);
        }
    }

    private void addInterval(double start, double end, double coefficient,
                             List<Interval> intervals, List<Double> cuts) {
        if (end - start <= EPS) return;
        intervals.add(new Interval(start, end, coefficient));
        cuts.add(start);
        cuts.add(end);
    }

    private static final class Interval {
        private final double start, end, coefficient;
        private Interval(double start, double end, double coefficient) {
            this.start = start;
            this.end = end;
            this.coefficient = coefficient;
        }
    }

    public static final class Part {
        private final LineString geometry;
        private final boolean special;
        private final double cost;

        private Part(LineString geometry, boolean special, double cost) {
            this.geometry = geometry;
            this.special = special;
            this.cost = cost;
        }

        public LineString getGeometry() { return geometry; }
        public boolean isSpecial() { return special; }
        public double getCost() { return cost; }
    }
}
