package rvd.render;

import xyz.marsavic.geometry.Vector;

public class BrocardTracker {

    private Vector point;
    private double angle;

    public void reset() {
        angle = Double.NEGATIVE_INFINITY;
        point = null;
    }

    /** Renderer-supplied colored-cell argmax; point may be skeleton-snapped. Skips -2 (aperture-clipped) and -3 (out-of-domain). */
    public void observe(int index, double candidateAngle, Vector candidatePoint) {
        if (index > -2 && candidateAngle > angle) {
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
