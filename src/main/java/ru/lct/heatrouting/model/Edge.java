package ru.lct.heatrouting.model;

import org.locationtech.jts.geom.LineString;

import java.util.Objects;

/**
 * Ребро тепловой сети — участок между двумя узлами.
 * <p>
 * Хранит геометрию (линию), DN (условный диаметр),
 * текущий расход и способ прокладки.
 */
public class Edge {

    private final String id;
    private final Node from;
    private final Node to;
    private final LineString geometry;
    private final int diameter;
    private final double flowTph;
    private final String layingMethod;

    public Edge(String id,
                Node from,
                Node to,
                LineString geometry,
                int diameter,
                double flowTph,
                String layingMethod) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.geometry = geometry;
        this.diameter = diameter;
        this.flowTph = flowTph;
        this.layingMethod = layingMethod;
    }

    public String getId() {
        return id;
    }

    public Node getFrom() {
        return from;
    }

    public Node getTo() {
        return to;
    }

    public LineString getGeometry() {
        return geometry;
    }

    public int getDiameter() {
        return diameter;
    }

    public double getFlowTph() {
        return flowTph;
    }

    public String getLayingMethod() {
        return layingMethod;
    }

    public double getLengthMeters() {
        return geometry.getLength();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge)) return false;
        Edge edge = (Edge) o;
        return Objects.equals(id, edge.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Edge{" + id + ", DN=" + diameter + "}";
    }
}