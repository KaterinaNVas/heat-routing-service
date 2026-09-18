package ru.lct.heatrouting.model;

import org.locationtech.jts.geom.Point;

import java.util.Objects;

/**
 * Узел тепловой сети.
 * <p>
 * Это точка на карте с уникальным ID и типом объекта
 * (например, "heat_chamber", "source", "oks_connection_point").
 */
public class Node {

    private final String id;
    private final Point point;
    private final String objectType;

    public Node(String id, Point point, String objectType) {
        this.id = id;
        this.point = point;
        this.objectType = objectType;
    }

    public String getId() {
        return id;
    }

    public Point getPoint() {
        return point;
    }

    public String getObjectType() {
        return objectType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node node = (Node) o;
        return Objects.equals(id, node.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Node{" + id + ", " + objectType + "}";
    }
}