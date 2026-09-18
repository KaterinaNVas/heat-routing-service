package ru.lct.heatrouting.model;

import org.locationtech.jts.geom.Point;

public class Source {

    private String id;
    private Point geometry;

    public Source() {
    }

    public Source(String id, Point geometry) {
        this.id = id;
        this.geometry = geometry;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Point getGeometry() {
        return geometry;
    }

    public void setGeometry(Point geometry) {
        this.geometry = geometry;
    }
}