package ru.lct.heatrouting.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.stereotype.Service;

@Service
public class CoordinateTransformService {

    private static final int WGS84_SRID = 4326;
    private static final int METRIC_SRID = 32637;

    private final CoordinateTransform toMetricTransform;
    private final CoordinateTransform toWgs84Transform;

    public CoordinateTransformService() {
        CRSFactory crsFactory = new CRSFactory();

        CoordinateReferenceSystem wgs84 =
                crsFactory.createFromName("EPSG:4326");

        CoordinateReferenceSystem metric =
                crsFactory.createFromName("EPSG:32637");

        CoordinateTransformFactory transformFactory =
                new CoordinateTransformFactory();

        this.toMetricTransform =
                transformFactory.createTransform(wgs84, metric);

        this.toWgs84Transform =
                transformFactory.createTransform(metric, wgs84);
    }

    public Geometry toMetric(Geometry geometry) {
        return transformGeometry(
                geometry,
                toMetricTransform,
                METRIC_SRID
        );
    }

    public Geometry toWgs84(Geometry geometry) {
        return transformGeometry(
                geometry,
                toWgs84Transform,
                WGS84_SRID
        );
    }

    private Geometry transformGeometry(
            Geometry geometry,
            CoordinateTransform transform,
            int targetSrid
    ) {
        if (geometry == null) {
            throw new IllegalArgumentException(
                    "Geometry must not be null"
            );
        }

        Geometry result = geometry.copy();

        result.apply((Coordinate coordinate) -> {
            ProjCoordinate source = new ProjCoordinate(
                    coordinate.x,
                    coordinate.y
            );

            ProjCoordinate target = new ProjCoordinate();

            transform.transform(source, target);

            coordinate.x = target.x;
            coordinate.y = target.y;
        });

        result.geometryChanged();
        result.setSRID(targetSrid);

        return result;
    }
}