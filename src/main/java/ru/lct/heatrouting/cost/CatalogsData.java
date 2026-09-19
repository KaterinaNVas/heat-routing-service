package ru.lct.heatrouting.cost;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class CatalogsData {

    @JsonProperty("capacities")
    public Map<Integer, Double> capacities;

    @JsonProperty("maxLengths")
    public Map<Integer, Double> maxLengths;

    @JsonProperty("newBuildCosts")
    public Map<Integer, Double> newBuildCosts;

    @JsonProperty("reconstructionCosts")
    public Map<Integer, Double> reconstructionCosts;
}
