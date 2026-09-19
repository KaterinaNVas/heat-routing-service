package ru.lct.heatrouting.network;

public abstract class Segment {

    protected final String id;
    protected final int diameter;
    protected final double length;

    protected Segment(String id, int diameter, double length) {
        if (length < 0) {
            throw new IllegalArgumentException("Длина не может быть отрицательной: " + length);
        }
        this.id = id;
        this.diameter = diameter;
        this.length = length;
    }

    public String getId() { return id; }
    public int getDiameter() { return diameter; }
    public double getLength() { return length; }
}
