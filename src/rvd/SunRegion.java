package rvd;

import xyz.marsavic.geometry.Box;
import xyz.marsavic.geometry.Figure;
import xyz.marsavic.geometry.Vector;

public class SunRegion implements Figure {
	
	private final Figure c;
	private final Figure r0, r1;
	private final Figure l;
	
	
	
	public SunRegion(Figure c, Figure r0, Figure r1, Figure l) {
		this.c = c;
		this.r0 = r0;
		this.r1 = r1;
		this.l = l;
	}
	
	
	@Override
	public Box boundingBox() {
		return Box.FULL;
	}
	
	
	@Override
	public boolean contains(Vector p) {
		return l.contains(p) ? c.contains(p) : (r0.contains(p) && r1.contains(p));
	}
	
}