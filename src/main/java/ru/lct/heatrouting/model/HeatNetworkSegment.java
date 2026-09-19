package ru.lct.heatrouting.model;

import org.locationtech.jts.geom.LineString;

public class HeatNetworkSegment {

    private String id;

    private Integer diameter;
    private Double flowTph;

    private String upstreamObjectId;

    private LineString geometry;

    public HeatNetworkSegment() {
    }

    public HeatNetworkSegment(
            String id,
            Integer diameter,
            Double flowTph,
            String upstreamObjectId,
            LineString geometry
    ) {
        this.id = id;
        this.diameter = diameter;
        this.flowTph = flowTph;
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

    public Double getFlowTph() {
        return flowTph;
    }

    public void setFlowTph(Double flowTph) {
        this.flowTph = flowTph;
    }

    public String getUpstreamObjectId() {
        return upstreamObjectId;
    }

    public void setUpstreamObjectId(String upstreamObjectId) {
        this.upstreamObjectId = upstreamObjectId;
    }

    public LineString getGeometry() {
        return geometry;
    }

    public void setGeometry(LineString geometry) {
        this.geometry = geometry;
    }
}