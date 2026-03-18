package rvd.core;

import xyz.marsavic.geometry.Geometry;
import xyz.marsavic.geometry.LineSegment;
import xyz.marsavic.geometry.Polygon;
import xyz.marsavic.geometry.Vector;
import xyz.marsavic.utils.performance.ArrayInts;

public class PolygonVisibility {

    public int[] visibleVertices(Vector p, Vector[] points, int n, Polygon polygon, boolean polygonMode, boolean showPolygonExterior) {
        ArrayInts vs = new ArrayInts(n);
        for (int k = 0; k < n; k++) {
            if (!polygonMode || visibleThroughPolygon(p, k, points, n, polygon, showPolygonExterior)) {
                vs.add(k);
            }
        }
        return vs.toArray();
    }

    public boolean visibleThroughPolygon(Vector p, int k, Vector[] points, int n, Polygon polygon, boolean showPolygonExterior) {
        LineSegment ab = LineSegment.pq(points[k], p);

        if (!showPolygonExterior) {
            if (ab.d().angleBetween(polygon.e(k - 1).d().inverse(), polygon.e(k).d())) {
                return false;
            }
        }

        for (int i = 0; i < n; i++) {
            if ((i == k) || ((i + 1) % n == k)) {
                continue;
            }
            LineSegment edge = LineSegment.pq(points[i], points[(i + 1) % n]);
            if (Geometry.intersecting(ab, edge)) {
                return false;
            }
        }
        return true;
    }
}
