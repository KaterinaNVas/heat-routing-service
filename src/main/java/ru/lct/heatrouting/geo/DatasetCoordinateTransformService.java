package ru.lct.heatrouting.geo;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatChamber;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.Source;

import java.util.function.Function;

@Service
public class DatasetCoordinateTransformService {

    private final CoordinateTransformService coordinateTransformService;

    public DatasetCoordinateTransformService(
            CoordinateTransformService coordinateTransformService
    ) {
        this.coordinateTransformService =
                coordinateTransformService;
    }

    public InputDataset toMetric(InputDataset sourceDataset) {
        return transformDataset(
                sourceDataset,
                coordinateTransformService::toMetric
        );
    }

    public InputDataset toWgs84(InputDataset sourceDataset) {
        return transformDataset(
                sourceDataset,
                coordinateTransformService::toWgs84
        );
    }

    private InputDataset transformDataset(
            InputDataset sourceDataset,
            Function<Geometry, Geometry> transformer
    ) {
        if (sourceDataset == null) {
            throw new IllegalArgumentException(
                    "InputDataset must not be null"
            );
        }

        InputDataset result = new InputDataset();

        if (sourceDataset.getSource() != null) {
            result.setSource(
                    transformSource(
                            sourceDataset.getSource(),
                            transformer
                    )
            );
        }

        for (HeatNetworkSegment segment :
                sourceDataset.getHeatNetwork()) {

            result.getHeatNetwork().add(
                    transformHeatNetworkSegment(
                            segment,
                            transformer
                    )
            );
        }

        for (HeatChamber chamber :
                sourceDataset.getHeatChambers()) {

            result.getHeatChambers().add(
                    transformHeatChamber(
                            chamber,
                            transformer
                    )
            );
        }

        for (ConnectionPoint connectionPoint :
                sourceDataset.getConnectionPoints()) {

            result.getConnectionPoints().add(
                    transformConnectionPoint(
                            connectionPoint,
                            transformer
                    )
            );
        }

        for (Restriction restriction :
                sourceDataset.getRestrictions()) {

            result.getRestrictions().add(
                    transformRestriction(
                            restriction,
                            transformer
                    )
            );
        }

        return result;
    }

    private Source transformSource(
            Source source,
            Function<Geometry, Geometry> transformer
    ) {
        Point geometry = (Point) transformer.apply(
                source.getGeometry()
        );

        return new Source(
                source.getId(),
                geometry
        );
    }

    private HeatNetworkSegment transformHeatNetworkSegment(
            HeatNetworkSegment segment,
            Function<Geometry, Geometry> transformer
    ) {
        LineString geometry = (LineString) transformer.apply(
                segment.getGeometry()
        );

        return new HeatNetworkSegment(
                segment.getId(),
                segment.getDiameter(),
                segment.getFlowTph(),
                segment.getUpstreamObjectId(),
                geometry
        );
    }

    private HeatChamber transformHeatChamber(
            HeatChamber chamber,
            Function<Geometry, Geometry> transformer
    ) {
        Point geometry = (Point) transformer.apply(
                chamber.getGeometry()
        );

        return new HeatChamber(
                chamber.getId(),
                chamber.getDiameter(),
                chamber.getUpstreamObjectId(),
                geometry
        );
    }

    private ConnectionPoint transformConnectionPoint(
            ConnectionPoint connectionPoint,
            Function<Geometry, Geometry> transformer
    ) {
        Point geometry = (Point) transformer.apply(
                connectionPoint.getGeometry()
        );

        return new ConnectionPoint(
                connectionPoint.getId(),
                connectionPoint.getFlowTph(),
                connectionPoint.getOksId(),
                geometry
        );
    }

    private Restriction transformRestriction(
            Restriction restriction,
            Function<Geometry, Geometry> transformer
    ) {
        Geometry geometry = transformer.apply(
                restriction.getGeometry()
        );

        return new Restriction(
                restriction.getId(),
                restriction.getRestrictionType(),
                restriction.getAddress(),
                geometry
        );
    }
}