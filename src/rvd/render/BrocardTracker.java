package rvd.render;

import xyz.marsavic.geometry.Vector;

public class BrocardTracker {

    private Vector point;
    private double angle;

    public void reset() {
        angle = 0.0;
        point = null;
    }

    public void observe(int index, double candidateAngle, Vector candidatePoint) {
        // Preserves legacy sentinel behavior from RVDExplorer:
        // -1 = skeleton, -2 = aperture-clipped, -3 = out of domain.
        if (index >= -2 && candidateAngle > angle) {
            angle = candidateAngle;
            point = candidatePoint;
        }
    }

    public Vector point() {
        return point;
    }

    public double angle() {
        return angle;
    }
}
