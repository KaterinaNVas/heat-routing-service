package ru.lct.heatrouting.importdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.*;

class DatasetReaderTest {

    private DatasetReader datasetReader;

    @BeforeEach
    void setUp() {
        datasetReader = new DatasetReader(
                new ObjectMapper(),
                new GeoJsonGeometryReader()
        );
    }

    @Test
    void shouldReadCompetitionDataset() {

        Path path = Path.of(
                "data",
                "input.geojson"
        );

        Assumptions.assumeTrue(
                Files.exists(path),
                "Локальный конкурсный датасет отсутствует"
        );

        InputDataset dataset =
                datasetReader.read(path);

        assertNotNull(dataset.getSource());

        assertEquals(
                29,
                dataset.getHeatNetwork().size()
        );

        assertEquals(
                9,
                dataset.getHeatChambers().size()
        );

        assertEquals(
                17,
                dataset.getConnectionPoints().size()
        );

        assertEquals(
                88,
                dataset.getRestrictions().size()
        );

        assertEquals(
                144,
                dataset.getTotalObjectCount()
        );
    }

    @Test
    void shouldCalculateRealNetworkLengthInMeters() {

        Path path = Path.of(
                "data",
                "input.geojson"
        );

        Assumptions.assumeTrue(
                Files.exists(path),
                "Локальный конкурсный датасет отсутствует"
        );

        InputDataset wgs84 =
                datasetReader.read(path);

        DatasetCoordinateTransformService transformService =
                new DatasetCoordinateTransformService(
                        new CoordinateTransformService()
                );

        InputDataset metric =
                transformService.toMetric(wgs84);

        double totalLength =
                metric.getHeatNetwork()
                        .stream()
                        .mapToDouble(
                                segment ->
                                        segment
                                                .getGeometry()
                                                .getLength()
                        )
                        .sum();

        System.out.println(
                "Total existing network length: "
                        + totalLength
                        + " m"
        );

        assertTrue(totalLength > 1400);
        assertTrue(totalLength < 1550);
    }
}