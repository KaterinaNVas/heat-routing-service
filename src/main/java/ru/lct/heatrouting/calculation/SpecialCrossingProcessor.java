package ru.lct.heatrouting.calculation;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.Restriction;

import java.util.List;

/** Checks forbidden zones and the rules for permitted special crossings. */
@Component
public class SpecialCrossingProcessor {
    private static final double EPS = 1e-6;

    public void validateNoForbiddenCrossings(LineString route, List<Restriction> restrictions) {
        if (route == null || restrictions == null) {
            throw new IllegalArgumentException("Аргументы не могут быть null");
        }
        for (Restriction restriction : restrictions) {
            if (!CrossingRules.hasRule(restriction.getRestrictionType())) continue;
            CrossingRules.Rule rule = CrossingRules.getRule(restriction.getRestrictionType());
            if (rule.allowed) continue;
            Geometry geometry = restriction.getGeometry();
            if (geometry != null && route.intersects(geometry)) {
                throw new IllegalStateException("Маршрут пересекает запрещённую зону: "
                        + restriction.getRestrictionType() + " (id=" + restriction.getId() + ")");
            }
        }
    }

    public double findMaxKSpec(LineString route, List<Restriction> restrictions) {
        if (route == null || restrictions == null) {
            throw new IllegalArgumentException("Аргументы не могут быть null");
        }
        double maximum = 1.0;
        for (Restriction restriction : restrictions) {
            if (!CrossingRules.hasRule(restriction.getRestrictionType())) continue;
            CrossingRules.Rule rule = CrossingRules.getRule(restriction.getRestrictionType());
            Geometry geometry = restriction.getGeometry();
            if (rule.allowed && geometry != null && route.intersects(geometry)) {
                maximum = Math.max(maximum, rule.kSpec);
            }
        }
        return maximum;
    }

    /** Measures the smaller angle to the obstacle line, or to a polygon's entry boundary. */
    public void validateAngles(LineString route, List<Restriction> restrictions) {
        if (route == null || restrictions == null) {
            throw new IllegalArgumentException("Аргументы не могут быть null");
        }
        for (Restriction restriction : restrictions) {
            if (!CrossingRules.hasRule(restriction.getRestrictionType())) continue;
            CrossingRules.Rule rule = CrossingRules.getRule(restriction.getRestrictionType());
            if (!rule.allowed || rule.minAngleDegrees <= 0) continue;
            Geometry geometry = restriction.getGeometry();
            if (geometry == null || geometry.isEmpty()) continue;
            Geometry boundary = geometry.getDimension() == 2 ? geometry.getBoundary() : geometry;
            Geometry intersection = route.intersection(boundary);
            if (intersection.isEmpty()) continue;

            for (Coordinate crossing : intersection.getCoordinates()) {
                for (LineSegment routeSegment : segmentsAt(route, crossing)) {
                    for (LineSegment obstacleSegment : segmentsAt(boundary, crossing)) {
                        double angle = crossingAngle(routeSegment, obstacleSegment);
                        if (angle + EPS < rule.minAngleDegrees) {
                            throw new IllegalStateException("Угол пересечения "
                                    + restriction.getRestrictionType() + " (id="
                                    + restriction.getId() + "): " + angle
                                    + "° < " + rule.minAngleDegrees + "°");
                        }
                    }
                }
            }
        }
    }

    private java.util.List<LineSegment> segmentsAt(Geometry geometry, Coordinate point) {
        java.util.List<LineSegment> result = new java.util.ArrayList<>();
        for (int part = 0; part < geometry.getNumGeometries(); part++) {
            Coordinate[] coordinates = geometry.getGeometryN(part).getCoordinates();
            for (int i = 1; i < coordinates.length; i++) {
                LineSegment segment = new LineSegment(coordinates[i - 1], coordinates[i]);
                if (segment.getLength() > EPS && segment.distance(point) <= EPS) result.add(segment);
            }
        }
        return result;
    }

    private double crossingAngle(LineSegment first, LineSegment second) {
        double dx1 = first.p1.x - first.p0.x;
        double dy1 = first.p1.y - first.p0.y;
        double dx2 = second.p1.x - second.p0.x;
        double dy2 = second.p1.y - second.p0.y;
        double cosine = Math.abs(dx1 * dx2 + dy1 * dy2)
                / (first.getLength() * second.getLength());
        return Math.toDegrees(Math.acos(Math.min(1.0, cosine)));
    }
}
