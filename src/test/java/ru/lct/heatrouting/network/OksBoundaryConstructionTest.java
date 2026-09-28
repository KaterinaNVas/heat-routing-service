package ru.lct.heatrouting.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.importdata.GeoJsonGeometryReader;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OksBoundaryConstructionTest {
    @Test
    void routesLeaveOwnBuildingsOnceAndStartAtOksConnectionPoint() throws Exception {
        URL resource = getClass().getClassLoader().getResource("test-dataset.geojson");
        assertNotNull(resource);
        InputDataset metric = new DatasetCoordinateTransformService(new CoordinateTransformService())
                .toMetric(new DatasetReader(new ObjectMapper(), new GeoJsonGeometryReader())
                        .read(Path.of(resource.toURI())));
        var prepared = new MultiOksRoutePreparationService(new TerritoryRoutePlanner(),
                new ConnectionResolver(), new OksConnectionGrouper()).prepare(metric);
        var drafts = new MultiConnectionDraftBuilder().build(prepared);

        Map<String, LineString> pricedByOks = new HashMap<>();
        for (var draft : drafts) {
            for (var edge : draft.getParentEdge().values()) {
                pricedByOks.put(edge.getFrom().getId().substring("oks:".length()),
                        (LineString) edge.getGeometry());
            }
        }
        assertEquals(17, pricedByOks.size());
        assertTrue(prepared.getUnconnectedOksIds().isEmpty());

        for (ConnectionPoint oks : metric.getConnectionPoints()) {
            LineString priced = pricedByOks.get(oks.getId());
            assertNotNull(priced, "ОКС " + oks.getId());
            Restriction own = null;
            for (Restriction restriction : metric.getRestrictions()) {
                if (restriction.getRestrictionType() != RestrictionType.OKS
                        || !restriction.getGeometry().covers(oks.getGeometry())) continue;
                if (own == null || restriction.getGeometry().getBoundary()
                        .distance(oks.getGeometry()) < own.getGeometry().getBoundary()
                        .distance(oks.getGeometry())) own = restriction;
            }
            if (own == null) continue;
            Geometry footprint = own.getGeometry();
            assertTrue(oks.getGeometry().distance(priced.getStartPoint()) < 0.01,
                    "Участок ОКС " + oks.getId() + " не начинается в точке подключения");
            Geometry inside = priced.intersection(footprint);
            assertEquals(1, inside.getNumGeometries(),
                    "Участок ОКС " + oks.getId() + " входит в здание более одного раза");
            assertTrue(inside.getGeometryN(0) instanceof LineString,
                    "Внутренний подход к ОКС " + oks.getId() + " должен быть прямым");
            assertEquals(2, inside.getGeometryN(0).getNumPoints(),
                    "Подход к ОКС " + oks.getId() + " должен быть одной прямой");
        }

        for (String id : new String[]{"2", "3"}) {
            ConnectionPoint oks = metric.getConnectionPoints().stream()
                    .filter(p -> id.equals(p.getId())).findFirst().orElseThrow();
            LineString complete = pricedByOks.get(id);
            assertTrue(complete.getStartPoint().distance(oks.getGeometry()) < 0.01,
                    "ОКС " + id + ": внутренняя часть пропала из трассы");
        }
    }
}
