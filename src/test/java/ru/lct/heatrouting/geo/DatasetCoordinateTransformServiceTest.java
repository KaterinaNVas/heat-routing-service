package ru.lct.heatrouting.geo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetCoordinateTransformServiceTest {

    private DatasetCoordinateTransformService service;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        CoordinateTransformService coordinateTransformService =
                new CoordinateTransformService();

        service = new DatasetCoordinateTransformService(
                coordinateTransformService
        );

        geometryFactory = new GeometryFactory();
    }

    @Test
    void shouldTransformEntireDatasetToMetricCrs() {

        InputDataset input = createDataset();

        InputDataset metric = service.toMetric(input);

        assertEquals(
                32637,
                metric.getSource()
                        .getGeometry()
                        .getSRID()
        );

        assertEquals(
                32637,
                metric.getHeatNetwork()
                        .get(0)
                        .getGeometry()
                        .getSRID()
        );

        assertEquals(
                32637,
                metric.getConnectionPoints()
                        .get(0)
                        .getGeometry()
                        .getSRID()
        );

        double networkLength =
                metric.getHeatNetwork()
                        .get(0)
                        .getGeometry()
                        .getLength();

        assertTrue(networkLength > 0);
        assertTrue(networkLength < 1000);
    }

    @Test
    void shouldNotModifyOriginalDataset() {

        InputDataset input = createDataset();

        service.toMetric(input);

        assertEquals(
                4326,
                input.getSource()
                        .getGeometry()
                        .getSRID()
        );

        assertEquals(
                4326,
                input.getHeatNetwork()
                        .get(0)
                        .getGeometry()
                        .getSRID()
        );
    }

    @Test
    void shouldTransformMetricDatasetBackToWgs84() {

        InputDataset input = createDataset();

        InputDataset metric = service.toMetric(input);
        InputDataset restored = service.toWgs84(metric);

        Point restoredSource =
                restored.getSource().getGeometry();

        assertEquals(
                4326,
                restoredSource.getSRID()
        );

        assertEquals(
                37.6176,
                restoredSource.getX(),
                0.00001
        );

        assertEquals(
                55.7558,
                restoredSource.getY(),
                0.00001
        );
    }

    private InputDataset createDataset() {

        InputDataset dataset =
                new InputDataset();

        Point sourcePoint =
                geometryFactory.createPoint(
                        new Coordinate(
                                37.6176,
                                55.7558
                        )
                );

        sourcePoint.setSRID(4326);

        dataset.setSource(
                new Source(
                        "source-1",
                        sourcePoint
                )
        );

        LineString networkLine =
                geometryFactory.createLineString(
                        new Coordinate[]{
                                new Coordinate(
                                        37.6176,
                                        55.7558
                                ),
                                new Coordinate(
                                        37.6180,
                                        55.7560
                                )
                        }
                );

        networkLine.setSRID(4326);

        dataset.getHeatNetwork().add(
                new HeatNetworkSegment(
                        "network-1",
                        300,
                        100.0,
                        null,
                        networkLine
                )
        );

        Point connectionPoint =
                geometryFactory.createPoint(
                        new Coordinate(
                                37.6200,
                                55.7570
                        )
                );

        connectionPoint.setSRID(4326);

        dataset.getConnectionPoints().add(
                new ConnectionPoint(
                        "connection-1",
                        20.0,
                        null,
                        connectionPoint
                )
        );

        return dataset;
    }
}