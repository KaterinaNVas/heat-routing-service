package ru.lct.heatrouting.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.lct.heatrouting.calculation.FlowPropagator;
import ru.lct.heatrouting.calculation.MultiConnectionCalculator;
import ru.lct.heatrouting.calculation.MultiTreeVariantCalculator;
import ru.lct.heatrouting.calculation.TopologyValidator;
import ru.lct.heatrouting.calculation.VariantCalculator;
import ru.lct.heatrouting.calculation.VariantSummary;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.importdata.GeoJsonGeometryReader;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiOksRealDatasetCalculationTest {
    @Test
    void calculatesOneVariantForAllOks() throws Exception {
        URL resource = getClass().getClassLoader().getResource("test-dataset.geojson");
        assertNotNull(resource);
        InputDataset metric = new DatasetCoordinateTransformService(new CoordinateTransformService())
                .toMetric(new DatasetReader(new ObjectMapper(), new GeoJsonGeometryReader())
                        .read(Path.of(resource.toURI())));
        MultiOksRoutePreparationService.Preparation prepared =
                new MultiOksRoutePreparationService(new TerritoryRoutePlanner(),
                        new ConnectionResolver(), new OksConnectionGrouper()).prepare(metric);
        List<MultiConnectionDraftBuilder.Draft> drafts =
                new MultiConnectionDraftBuilder().build(prepared);

        Map<String, ConnectionPoint> byId = metric.getConnectionPoints().stream()
                .collect(Collectors.toMap(ConnectionPoint::getId, Function.identity()));
        List<Object> unconnectedIds = new ArrayList<>(prepared.getUnconnectedOksIds());
        List<Double> unconnectedFlows = prepared.getUnconnectedOksIds().stream()
                .map(id -> byId.get(id).getFlowTph()).collect(Collectors.toList());

        VariantCalculator pricing = new VariantCalculator(0.7, 0.3);
        VariantSummary summary = new MultiTreeVariantCalculator(
                new MultiConnectionCalculator(new FlowPropagator(), pricing,
                        new TopologyValidator()), pricing)
                .calculateVariant("v1", drafts, unconnectedIds, unconnectedFlows);

        int connected = drafts.stream().mapToInt(d -> d.getDemands().size()).sum();
        System.out.println("Подключено ОКС: " + connected);
        System.out.println("Деревьев: " + drafts.size());
        System.out.println("Новых участков: " + summary.getSegments().size());
        System.out.println("Длина, м: " + summary.getNewNetworkLength());
        System.out.println("Стоимость: " + summary.getCalculatedCost());
        System.out.println("Неподключённые ОКС: " + summary.getUnconnectedOksIds());

        assertEquals(17, connected + summary.getUnconnectedOksIds().size());
        assertTrue(summary.getNewNetworkLength() > 0);
        assertTrue(Double.isFinite(summary.getCalculatedCost()));
    }
}
