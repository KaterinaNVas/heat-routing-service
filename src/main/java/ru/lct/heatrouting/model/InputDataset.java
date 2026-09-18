package ru.lct.heatrouting.model;
import java.util.ArrayList;
import java.util.List;

public class InputDataset {

    private Source source;

    private List<HeatNetworkSegment> heatNetwork = new ArrayList<>();
    private List<HeatChamber> heatChambers = new ArrayList<>();
    private List<ConnectionPoint> connectionPoints = new ArrayList<>();
    private List<Restriction> restrictions = new ArrayList<>();

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public List<HeatNetworkSegment> getHeatNetwork() {
        return heatNetwork;
    }

    public void setHeatNetwork(List<HeatNetworkSegment> heatNetwork) {
        this.heatNetwork = heatNetwork;
    }

    public List<HeatChamber> getHeatChambers() {
        return heatChambers;
    }

    public void setHeatChambers(List<HeatChamber> heatChambers) {
        this.heatChambers = heatChambers;
    }

    public List<ConnectionPoint> getConnectionPoints() {
        return connectionPoints;
    }

    public void setConnectionPoints(List<ConnectionPoint> connectionPoints) {
        this.connectionPoints = connectionPoints;
    }

    public List<Restriction> getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(List<Restriction> restrictions) {
        this.restrictions = restrictions;
    }

    public int getTotalObjectCount() {
        int count = heatNetwork.size()
                + heatChambers.size()
                + connectionPoints.size()
                + restrictions.size();

        if (source != null) {
            count++;
        }

        return count;
    }
}