package TigerPDP;

import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.geom.Point;


public class Colony extends Depot {
	
	//private int succesfulDeliveries;
	private Point position;
	private Ant restingAnt = null;

	Colony(Point position, double capacity) {
		super(position);
		setCapacity(capacity);
		this.position = position;
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
	}

	public Point getPosition() {
		return position;
	}
	
	public boolean occupyForResting(Ant ant) {
		if(isOccupiedByOtherAnt(ant))
			return false;
		//if(restingAnt == null)
			//System.out.println("new ant occupies");
		restingAnt = ant;
		return true;
	}
	
	public boolean isOccupiedByOtherAnt(Ant ant) {
		return restingAnt != null && !ant.equals(restingAnt);
	}

	public void finishedResting(Ant ant) {
		if(ant.equals(restingAnt))
			restingAnt = null;
		else throw new IllegalArgumentException();
		//System.out.println("ant "+ant.toString()+" is done resting");
	}
}
