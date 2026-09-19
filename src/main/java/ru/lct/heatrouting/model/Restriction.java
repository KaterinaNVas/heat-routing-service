package ru.lct.heatrouting.model;

import org.locationtech.jts.geom.Geometry;

public class Restriction {

    private String id;

    private RestrictionType restrictionType;

    private String address;

    private Geometry geometry;

    public Restriction() {
    }

    public Restriction(
            String id,
            RestrictionType restrictionType,
            String address,
            Geometry geometry
    ) {
        this.id = id;
        this.restrictionType = restrictionType;
        this.address = address;
        this.geometry = geometry;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RestrictionType getRestrictionType() {
        return restrictionType;
    }

    public void setRestrictionType(
            RestrictionType restrictionType
    ) {
        this.restrictionType = restrictionType;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }
}