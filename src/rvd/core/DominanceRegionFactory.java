package rvd.core;

import xyz.marsavic.geometry.Circle;
import xyz.marsavic.geometry.Figure;
import xyz.marsavic.geometry.HalfPlane;
import xyz.marsavic.geometry.Vector;
import xyz.marsavic.utils.Numeric;

public class DominanceRegionFactory {

    public Figure create(Vector p0, Vector p1, double angle0, double angle1) {
        double phi = angle1 - angle0;

        if (Numeric.mod(phi, 0.5) != 0) {
            Vector d = p1.sub(p0).div(2);
            Vector c = p0.add(d.asBase(1, Numeric.tanT(0.25 - phi)));
            double r = Math.copySign(c.to(p0).length(), 0.5 - Numeric.mod(phi, 1));
            return Circle.cr(c, r);
        } else {
            return phi == 0 ? HalfPlane.pq(p0, p1) : HalfPlane.pq(p1, p0);
        }
    }
}
