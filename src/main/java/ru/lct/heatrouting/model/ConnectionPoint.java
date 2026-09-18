package ru.lct.heatrouting.model;

import org.locationtech.jts.geom.Point;

public class ConnectionPoint {

    private String id;

    private Double flowTph;

    private String oksId;

    private Point geometry;

    public ConnectionPoint() {
    }

    public ConnectionPoint(
            String id,
            Double flowTph,
            String oksId,
            Point geometry
    ) {
        this.id = id;
        this.flowTph = flowTph;
        this.oksId = oksId;
        this.geometry = geometry;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Double getFlowTph() {
        return flowTph;
    }

    public void setFlowTph(Double flowTph) {
        this.flowTph = flowTph;
    }

    public String getOksId() {
        return oksId;
    }

    public void setOksId(String oksId) {
        this.oksId = oksId;
    }

    public Point getGeometry() {
        return geometry;
    }

    public void setGeometry(Point geometry) {
        this.geometry = geometry;
    }
}