package ru.lct.heatrouting.model;

import org.locationtech.jts.geom.Point;

public class HeatChamber {

    private String id;

    private Integer diameter;
    private String upstreamObjectId;

    private Point geometry;

    public HeatChamber() {
    }

    public HeatChamber(
            String id,
            Integer diameter,
            String upstreamObjectId,
            Point geometry
    ) {
        this.id = id;
        this.diameter = diameter;
        this.upstreamObjectId = upstreamObjectId;
        this.geometry = geometry;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getDiameter() {
        return diameter;
    }

    public void setDiameter(Integer diameter) {
        this.diameter = diameter;
    }

    public String getUpstreamObjectId() {
        return upstreamObjectId;
    }

    public void setUpstreamObjectId(String upstreamObjectId) {
        this.upstreamObjectId = upstreamObjectId;
    }

    public Point getGeometry() {
        return geometry;
    }

    public void setGeometry(Point geometry) {
        this.geometry = geometry;
    }
}