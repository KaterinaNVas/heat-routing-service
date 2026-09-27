package ru.lct.heatrouting.calculation;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
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
        if (segment == null || route == null || restrictions == null
                || Math.abs(segment.getLength() - route.getLength()) > 0.01) {
            throw new IllegalArgumentException("Длина и геометрия нового участка должны совпадать");
        }
        LengthIndexedLine indexed = new LengthIndexedLine(route);
        List<Double> cuts = new ArrayList<>();
        cuts.add(0.0);
        cuts.add(route.getLength());
        for (Restriction restriction : restrictions) {
            if (restriction == null || !CrossingRules.hasRule(restriction.getRestrictionType())
                    || !CrossingRules.getRule(restriction.getRestrictionType()).allowed
                    || restriction.getGeometry() == null || restriction.getGeometry().isEmpty()) {
                continue;
            }
            Geometry boundary = restriction.getGeometry().getBoundary();
            if (!route.getEnvelopeInternal().intersects(boundary.getEnvelopeInternal())) continue;
            Geometry crossings = route.intersection(boundary);
            for (Coordinate point : crossings.getCoordinates()) {
                cuts.add(indexed.project(point));
            }
        }
        Collections.sort(cuts);
        double cost = 0;
        for (int i = 1; i < cuts.size(); i++) {
            double start = cuts.get(i - 1);
            double end = cuts.get(i);
            if (end - start < EPS) continue;
            Point middle = route.getFactory().createPoint(indexed.extractPoint((start + end) / 2));
            double coefficient = 1.0;
            for (Restriction restriction : restrictions) {
                if (restriction == null || !CrossingRules.hasRule(restriction.getRestrictionType())) continue;
                CrossingRules.Rule rule = CrossingRules.getRule(restriction.getRestrictionType());
                if (rule.allowed && restriction.getGeometry() != null
                        && restriction.getGeometry().covers(middle)) {
                    coefficient = Math.max(coefficient, rule.kSpec);
                }
            }
            cost += prices.calculateNewSegmentCost(
                    new NewSegment(segment.getId(), segment.getDiameter(), end - start), coefficient);
        }
        return cost;
    }
}
